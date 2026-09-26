/**
 * Snowstorm 离线汉化 + 原生导入导出适配（MinewaysMobile 专用，不属于 Snowstorm 上游）
 * ============================================================================
 * 为什么这样汉化：Snowstorm 没有 i18n 系统，UI 文案硬编码在 dist/app.js 中。
 * 本脚本在页面加载后注入，按 zh-dict.js 的英→中对照**只替换界面显示文本**：
 *   · 文本节点（保留原有前后空白）
 *   · title / placeholder / aria-label 等属性
 * 绝不改动任何逻辑值（选项 value、格式化字段名、文件名、Molang 表达式等），
 * 因此不会影响导入导出内容与模拟结果。未收录的字符串保持英文，可平滑增补。
 *
 * 原生适配（配合 SnowstormActivity 的 SSAndroid 桥）：
 *   · 导出接管：Snowstorm 用 Blob + <a download> 触发下载；这里改为把内容交给
 *     原生保存到「下载/MinewaysMobile/粒子/」，不再走 WebView 下载（Android WebView
 *     无法保存 blob: 链接）。文本（.particle.json/.mcfunction）与图片（截图 PNG）都支持。
 *   · 导入：保持 Snowstorm 自带的 <input type=file>，由 Activity 的
 *     WebChromeClient.onShowFileChooser 提供系统文件选择器（含 .json 过滤）。
 */
(function () {
	'use strict';
	if (window.__ssHansLoaded) return;
	window.__ssHansLoaded = true;

	var DICT = window.__SS_ZH || {};
	var PATTERNS = window.__SS_ZH_PATTERNS || [];
	var ATTRS = ['title', 'placeholder', 'aria-label', 'alt', 'data-tooltip'];
	var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, CODE: 1, PRE: 1, CANVAS: 1, SVG: 1, PATH: 1, TEXTAREA: 1 };

	var stats = { text: 0, attr: 0 };

	/** 精确/正则查表；命中返回中文，否则返回 null。 */
	function lookup(s) {
		if (!s) return null;
		var key = s.trim();
		if (!key) return null;
		// 已是纯中文的一律跳过（幂等 + 零开销）
		if (!/[A-Za-z]/.test(key)) return null;
		if (Object.prototype.hasOwnProperty.call(DICT, key)) return DICT[key];
		for (var i = 0; i < PATTERNS.length; i++) {
			var m = key.match(PATTERNS[i][0]);
			if (m) return key.replace(PATTERNS[i][0], PATTERNS[i][1]);
		}
		return null;
	}

	/** 保留原文前后空白，只替换中间部分。 */
	function fixTextNode(node) {
		var raw = node.nodeValue;
		if (!raw) return;
		var zh = lookup(raw);
		if (zh === null) return;
		var lead = raw.match(/^\s*/)[0];
		var trail = raw.match(/\s*$/)[0];
		node.nodeValue = lead + zh + trail;
		stats.text++;
	}

	function fixAttributes(el) {
		for (var i = 0; i < ATTRS.length; i++) {
			var a = ATTRS[i];
			if (!el.hasAttribute || !el.hasAttribute(a)) continue;
			var zh = lookup(el.getAttribute(a));
			if (zh !== null) {
				el.setAttribute(a, zh);
				stats.attr++;
			}
		}
		// 提交按钮的 value 也是可见文案
		if (el.tagName === 'INPUT') {
			var t = (el.getAttribute('type') || '').toLowerCase();
			if ((t === 'submit' || t === 'button') && el.value) {
				var zh2 = lookup(el.value);
				if (zh2 !== null) el.value = zh2;
			}
		}
	}

	function walk(root) {
		if (!root) return;
		if (root.nodeType === 3) {
			fixTextNode(root);
			return;
		}
		if (root.nodeType !== 1) return;
		if (SKIP_TAGS[root.tagName]) return;
		fixAttributes(root);
		for (var c = root.firstChild; c; c = c.nextSibling) walk(c);
	}

	var scheduled = false;
	function runAll() {
		scheduled = false;
		walk(document.body);
		window.__ssHansStats = stats;
	}
	function schedule() {
		if (scheduled) return;
		scheduled = true;
		setTimeout(runAll, 60);
	}

	// ------------------------------------------------------------------ 导出接管

	function hookDownloads() {
		if (window.__ssDownloadHooked) return;
		window.__ssDownloadHooked = true;
		var bridge = function () { return window.SSAndroid || null; };

		var origClick = HTMLAnchorElement.prototype.click;
		HTMLAnchorElement.prototype.click = function () {
			try {
				var name = this.getAttribute('download');
				var href = this.getAttribute('href') || '';
				var api = bridge();
				if (name && api && (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0)) {
					// blob/ data URI -> 交给原生保存（文本按 UTF-8，图片按 data URL 解码）
					fetch(href)
						.then(function (r) { return r.blob(); })
						.then(function (blob) {
							if (/^(image|application\/octet-stream)/i.test(blob.type) && blob.type !== 'application/json') {
								var fr = new FileReader();
								fr.onloadend = function () {
									try { api.saveText(String(name), String(fr.result)); } catch (e) { }
								};
								fr.readAsDataURL(blob);
							} else {
								blob.text().then(function (text) {
									try { api.saveText(String(name), text); } catch (e) { }
								});
							}
						})
						.catch(function () {
							try { origClick.apply(this, arguments); } catch (e) { }
						});
					return;   // 不再触发 WebView 下载
				}
			} catch (e) { /* 回退到原逻辑 */ }
			return origClick.apply(this, arguments);
		};

		// 兜底：直接赋值 location.href 的下载（部分框架路径）
		window.addEventListener('click', function (ev) {
			try {
				var a = ev.target && ev.target.closest ? ev.target.closest('a[download]') : null;
				if (!a) return;
				var href = a.getAttribute('href') || '';
				if (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0) {
					ev.preventDefault();
					a.click();
				}
			} catch (e) { }
		}, true);
	}

	// ------------------------------------------------------------------ 启动

	function boot() {
		hookDownloads();
		runAll();
		try {
			var mo = new MutationObserver(schedule);
			mo.observe(document.body, { childList: true, subtree: true, characterData: true });
		} catch (e) { }
		// 兜底轮询：个别面板用 v-html/内部替换，不一定触发 characterData
		setInterval(schedule, 1500);
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', boot);
	} else {
		boot();
	}
})();
