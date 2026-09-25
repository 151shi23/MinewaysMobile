/**
 * MinewaysMobile 保存助手（自制内置插件 · id: mineways_saver）
 * =====================================================================
 * 为什么需要它：内核在网页版（含本应用的在线版与离线版）导出时走的是 FileSaver，
 * 它创建一个**从不插入 DOM** 的 <a download>，再对该游离节点 dispatchEvent('click')。
 * 游离节点的点击事件不会冒泡到 document，所以页面级 / 原生 DownloadListener 都收不到；
 * 而内核在调用 Blockbench.export() 之后**立刻**把 Project.saved 置为 true，
 * 于是界面提示“导出成功”，磁盘上却什么都没有。本插件把这条链路接管过来。
 *
 * 接管顺序（前者命中即不再往后走）：
 *   1. Blockbench.export(options, callback) —— 所有编解码器/第三方插件的统一出口，
 *      能拿到 name / extensions / content，成功率最高，且回调参数就是落盘路径；
 *   2. HTMLAnchorElement.prototype.dispatchEvent / click —— 兜住绕过 1 直接 saveAs 的路径
 *      （例如纹理、截图等），靠 URL.createObjectURL 记录的 blob 取内容；
 *   3. window.MMSave.rescueBlob(url) —— 原生层 DownloadListener 兜底时反向调用。
 *
 * 内容一律经 BBAndroid 文件桥**分块**写入（每块 240KB），避免一次传输超大 base64
 * 字符串压爆 WebView 堆；原生侧累积完成后发布到用户可见的
 * Download/MinewaysMobile/ 目录（Android 10+ 走 MediaStore，无需存储权限）。
 *
 * 面板：右下角「保存到手机」悬浮按钮（纯 DOM 自建，不依赖内核 UI API 版本），
 * 可直接触发内核自身的导出流程，并列出最近保存的文件路径。
 */
(() => {
	'use strict';
	if (window.__mmSaverLoaded) {
		// 二次求值（脚本标签与 loadFromURL 各可能加载一次）：只补登记，不重复装钩子
		try { maybeRegister(); } catch (e) { /* 忽略 */ }
		return;
	}
	window.__mmSaverLoaded = true;

	const PLUGIN_ID = 'mineways_saver';
	const UPLOAD_CHUNK = 240 * 1024; // 单次跨桥的原始字节数
	const B64_CHUNK = 8190;          // btoa 编码分片（3 的倍数，避免出现 '=' 填充）
	const MAX_RECENT = 8;

	const state = {
		enabled: true,     // 是否接管导出
		recent: [],        // [{ name, path }]
		lastError: '',
		logs: [],
	};

	/** 原生文件桥（延迟取，避免注入时序问题）。 */
	const bridge = () => window.BBAndroid || null;

	const hasBridge = () => {
		const b = bridge();
		return !!(b && typeof b.beginSave === 'function' && typeof b.finishSave === 'function');
	};

	const log = (...args) => {
		const line = args.map((a) => (typeof a === 'string' ? a : String(a && a.message ? a.message : a))).join(' ');
		state.logs.push(line);
		if (state.logs.length > 40) state.logs.shift();
		console.log('[mineways_saver]', ...args);
	};

	/** 轻提示：优先用内核的气泡，其次退回控制台。 */
	const notify = (msg, duration) => {
		try {
			if (window.Blockbench && typeof Blockbench.showQuickMessage === 'function') {
				Blockbench.showQuickMessage(msg, duration || 2600);
				return;
			}
		} catch (e) { /* 忽略 */ }
		log(msg);
	};

	// ---------------------------------------------------------------- 编码工具

	/** Uint8Array → base64（分片调用原生 btoa，避免超长参数与堆峰值）。 */
	const bytesToBase64 = (u8) => {
		let out = '';
		for (let i = 0; i < u8.length; i += B64_CHUNK) {
			const part = u8.subarray(i, Math.min(i + B64_CHUNK, u8.length));
			let bin = '';
			for (let j = 0; j < part.length; j++) bin += String.fromCharCode(part[j]);
			out += btoa(bin);
		}
		return out;
	};

	const strToBytes = (str) => {
		try {
			return new TextEncoder().encode(str);
		} catch (e) {
			const u8 = new Uint8Array(str.length * 3);
			let p = 0;
			for (let i = 0; i < str.length; i++) {
				const c = str.charCodeAt(i);
				if (c < 0x80) u8[p++] = c;
				else if (c < 0x800) { u8[p++] = 0xc0 | (c >> 6); u8[p++] = 0x80 | (c & 63); }
				else { u8[p++] = 0xe0 | (c >> 12); u8[p++] = 0x80 | ((c >> 6) & 63); u8[p++] = 0x80 | (c & 63); }
			}
			return u8.subarray(0, p);
		}
	};

	const base64ToBytes = (b64) => {
		const bin = atob(b64);
		const u8 = new Uint8Array(bin.length);
		for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
		return u8;
	};

	const dataUrlToBytes = (url) => {
		const comma = url.indexOf(',');
		if (comma < 0) return new Uint8Array(0);
		const meta = url.slice(0, comma);
		const body = url.slice(comma + 1);
		return /;base64/i.test(meta) ? base64ToBytes(body) : strToBytes(decodeURIComponent(body));
	};

	/** 任意导出内容 → Uint8Array。 */
	const toBytes = (content) => new Promise((resolve, reject) => {
		try {
			if (content == null) return reject(new Error('导出内容为空'));
			if (typeof content === 'string') {
				return resolve(/^data:[^,]*,/i.test(content) ? dataUrlToBytes(content) : strToBytes(content));
			}
			if (typeof Blob === 'function' && content instanceof Blob) {
				if (typeof content.arrayBuffer === 'function') {
					return content.arrayBuffer().then((ab) => resolve(new Uint8Array(ab)), reject);
				}
				const fr = new FileReader();
				fr.onload = () => resolve(new Uint8Array(fr.result));
				fr.onerror = () => reject(new Error('读取导出内容失败'));
				return fr.readAsArrayBuffer(content);
			}
			if (content instanceof ArrayBuffer) return resolve(new Uint8Array(content));
			if (content && content.buffer instanceof ArrayBuffer) {
				return resolve(new Uint8Array(content.buffer, content.byteOffset || 0, content.byteLength));
			}
			return resolve(strToBytes(JSON.stringify(content)));
		} catch (e) {
			reject(e);
		}
	});

	// ---------------------------------------------------------------- 落盘

	const sanitizeName = (name) => {
		let n = String(name == null ? '' : name).replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').trim();
		n = n.replace(/^\.+/, '').replace(/\.+$/, '');
		if (!n) n = 'model';
		return n.length > 120 ? n.slice(0, 120) : n;
	};

	const MIME = {
		png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
		zip: 'application/zip', json: 'application/json', bbmodel: 'application/json',
		glb: 'model/gltf-binary', gltf: 'model/gltf+json', obj: 'text/plain', mtl: 'text/plain',
		fbx: 'application/octet-stream', vox: 'application/octet-stream', txt: 'text/plain',
	};
	const guessMime = (name) => {
		const i = name.lastIndexOf('.');
		return (i >= 0 && MIME[name.slice(i + 1).toLowerCase()]) || 'application/octet-stream';
	};

	/** 分块写入原生侧，返回落盘后的可读路径（如 Download/MinewaysMobile/x.bbmodel）。 */
	const writeBytes = async (u8, name, mime) => {
		const b = bridge();
		if (!b) throw new Error('未连接 Android 文件桥');
		const id = b.beginSave(name, mime || guessMime(name));
		if (typeof id !== 'string' || id.indexOf('ERR:') === 0) {
			throw new Error('无法创建保存会话：' + id);
		}
		let sent = 0;
		try {
			for (let off = 0; off < u8.length; off += UPLOAD_CHUNK, sent++) {
				const part = u8.subarray(off, Math.min(off + UPLOAD_CHUNK, u8.length));
				const r = b.appendSave(id, bytesToBase64(part));
				if (typeof r === 'string' && r.indexOf('ERR:') === 0) throw new Error(r.slice(4));
				// 每 8 块让出一次主线程，避免大文件导出时界面假死
				if (sent % 8 === 7) await new Promise((r2) => setTimeout(r2, 0));
			}
			const path = b.finishSave(id);
			if (typeof path !== 'string' || path.indexOf('ERR:') === 0) {
				throw new Error('保存失败：' + (typeof path === 'string' ? path.slice(4) : '未知错误'));
			}
			remember(name, path);
			return path;
		} catch (e) {
			try { b.abortSave(id); } catch (e2) { /* 忽略 */ }
			throw e;
		}
	};

	const remember = (name, path) => {
		state.recent = state.recent.filter((r) => r.path !== path);
		state.recent.unshift({ name: name, path: path });
		if (state.recent.length > MAX_RECENT) state.recent.length = MAX_RECENT;
		state.lastError = '';
		refreshPanel();
	};

	/** 补全扩展名：与内核 provider 的规则保持一致。 */
	const resolveName = (options) => {
		let name = options && options.name ? String(options.name) : 'file';
		const exts = options && options.extensions;
		if (Array.isArray(exts) && exts.length) {
			const dot = name.lastIndexOf('.');
			const cur = dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
			if (exts.map((e) => String(e).toLowerCase()).indexOf(cur) < 0) name += '.' + exts[0];
		}
		return sanitizeName(name);
	};

	/** 保存一个 Blob / 字符串 / 缓冲区，落盘成功后返回路径。 */
	const saveContent = async (content, name, mime) => {
		const finalName = sanitizeName(name);
		const u8 = await toBytes(content);
		if (!u8.length) throw new Error('导出内容为 0 字节');
		return writeBytes(u8, finalName, mime || guessMime(finalName));
	};

	// ---------------------------------------------------------------- 接管一：Blockbench.export

	let originalExport = null;

	const installExportHook = () => {
		if (originalExport || !window.Blockbench || typeof Blockbench.export !== 'function') return;
		originalExport = Blockbench.export;
		const wrapped = function (options, callback) {
			const ctx = this;
			const args = arguments;
			if (!state.enabled || !options || options.content == null || !hasBridge()) {
				return originalExport.apply(ctx, args);
			}
			const name = resolveName(options);
			return saveContent(options.content, name, options.mime)
				.then((path) => {
					log('已保存', name, '->', path);
					notify('已保存到 ' + path, 3000);
					if (typeof callback === 'function') {
						try { callback(path); } catch (e) { log('导出回调异常', e); }
					}
					return path;
				})
				.catch((err) => {
					state.lastError = String((err && err.message) || err);
					log('接管导出失败，回退内核原流程：', err);
					refreshPanel();
					// 兜底：交回内核（配合下面的锚点钩子再试一次），至少不吞掉错误
					return originalExport.apply(ctx, args);
				});
		};
		try {
			Blockbench.export = wrapped;
		} catch (e) {
			log('挂接 Blockbench.export 失败', e);
			originalExport = null;
		}
	};

	const uninstallExportHook = () => {
		if (!originalExport) return;
		try { Blockbench.export = originalExport; } catch (e) { /* 忽略 */ }
		originalExport = null;
	};

	// ---------------------------------------------------------------- 接管二：下载锚点

	const blobs = new Map(); // objectURL -> Blob（上限 16 条，避免长会话堆积）
	let originalCreateObjectURL = null;
	let originalRevokeObjectURL = null;
	let originalDispatch = null;
	let originalClick = null;

	const rememberBlob = (url, blob) => {
		if (!url) return;
		try {
			blobs.set(url, blob);
			while (blobs.size > 16) blobs.delete(blobs.keys().next().value);
		} catch (e) { /* 忽略 */ }
	};

	const anchorBlob = (href) => {
		if (!href) return null;
		if (blobs.has(href)) return blobs.get(href);
		for (const key of blobs.keys()) if (href === key || href.indexOf(key) === 0) return blobs.get(key);
		return null;
	};

	/**
	 * 锚点下载兜底：把游离 <a download> 的点击改成“写盘 + 提示”。
	 * 写盘失败时执行 fallback（交还原生点击），至少不让这次导出凭空消失。
	 */
	const interceptAnchor = (href, downloadName, fallback) => {
		const blob = anchorBlob(href);
		if (!blob) return false;
		const name = sanitizeName(downloadName || 'file');
		saveContent(blob, name)
			.then((path) => {
				log('锚点导出已保存', name, '->', path);
				notify('已保存到 ' + path, 3000);
			})
			.catch((err) => {
				state.lastError = String((err && err.message) || err);
				log('锚点导出保存失败：', err);
				refreshPanel();
				notify('保存失败：' + state.lastError + '（已交还原生下载）', 3300);
				try { if (typeof fallback === 'function') fallback(); } catch (e) { /* 忽略 */ }
			});
		return true;
	};

	const installAnchorHooks = () => {
		try {
			if (!originalCreateObjectURL && window.URL && URL.createObjectURL) {
				originalCreateObjectURL = URL.createObjectURL;
				URL.createObjectURL = function (obj) {
					const url = originalCreateObjectURL.apply(this, arguments);
					try { if (typeof Blob === 'function' && obj instanceof Blob) rememberBlob(url, obj); } catch (e) { /* 忽略 */ }
					return url;
				};
			}
			if (!originalRevokeObjectURL && window.URL && URL.revokeObjectURL) {
				originalRevokeObjectURL = URL.revokeObjectURL;
				URL.revokeObjectURL = function (url) {
					// 保留记录：内核会在下载 40 秒后才 revoke，中途仍可能用到
					return originalRevokeObjectURL.apply(this, arguments);
				};
			}
			const A = window.HTMLAnchorElement;
			if (!A || !A.prototype) return;
			if (!originalDispatch && A.prototype.dispatchEvent) {
				originalDispatch = A.prototype.dispatchEvent;
				A.prototype.dispatchEvent = function (event) {
					try {
						const anchor = this;
						if (state.enabled && event && event.type === 'click' && anchor.download
							&& interceptAnchor(anchor.href, anchor.download,
								() => originalDispatch.call(anchor, event))) {
							return false; // 已接管，阻止游离点击进入原生下载通道
						}
					} catch (e) { /* 忽略 */ }
					return originalDispatch.apply(this, arguments);
				};
			}
			if (!originalClick && A.prototype.click) {
				originalClick = A.prototype.click;
				A.prototype.click = function () {
					try {
						const anchor = this;
						if (state.enabled && anchor.download
							&& interceptAnchor(anchor.href, anchor.download,
								() => originalClick.call(anchor))) {
							return;
						}
					} catch (e) { /* 忽略 */ }
					return originalClick.apply(this, arguments);
				};
			}
		} catch (e) {
			log('挂接下载锚点失败', e);
		}
	};

	const installHooks = () => {
		installExportHook();
		installAnchorHooks();
	};

	const uninstallHooks = () => {
		uninstallExportHook();
		try {
			if (window.URL && originalCreateObjectURL) URL.createObjectURL = originalCreateObjectURL;
			if (window.URL && originalRevokeObjectURL) URL.revokeObjectURL = originalRevokeObjectURL;
			const A = window.HTMLAnchorElement;
			if (A && originalDispatch) A.prototype.dispatchEvent = originalDispatch;
			if (A && originalClick) A.prototype.click = originalClick;
		} catch (e) { /* 忽略 */ }
		originalCreateObjectURL = originalRevokeObjectURL = originalDispatch = originalClick = null;
	};

	// ---------------------------------------------------------------- 面板

	let panel = null;
	let panelBody = null;
	let panelToggle = null;
	let panelHint = null;
	let expanded = false;

	const css = (el, obj) => { for (const k in obj) el.style[k] = obj[k]; return el; };

	const button = (label, onClick) => {
		const b = document.createElement('button');
		b.type = 'button';
		b.textContent = label;
		css(b, {
			display: 'block', width: '100%', margin: '0 0 6px 0', padding: '8px 10px',
			border: '1px solid #4b5563', borderRadius: '6px', cursor: 'pointer',
			background: '#374151', color: '#f3f4f6', fontSize: '13px',
		});
		b.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); onClick(); });
		return b;
	};

	const buildPanel = () => {
		if (panel || !document.body) return;
		panel = document.createElement('div');
		panel.id = 'mineways-saver-panel';
		css(panel, {
			position: 'fixed', right: '12px', bottom: '84px', zIndex: '2147483000',
			width: '212px', padding: '10px', boxSizing: 'border-box',
			background: 'rgba(24,24,27,.94)', color: '#eee', border: '1px solid #52525b',
			borderRadius: '10px', boxShadow: '0 6px 20px rgba(0,0,0,.45)',
			fontFamily: 'system-ui, -apple-system, "Noto Sans CJK SC", sans-serif', fontSize: '12px',
			lineHeight: '1.5',
		});

		const head = document.createElement('div');
		css(head, { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' });
		const title = document.createElement('b');
		title.textContent = '保存到手机';
		const fold = document.createElement('span');
		fold.textContent = '收起';
		css(fold, { cursor: 'pointer', color: '#a1a1aa' });
		fold.addEventListener('click', () => setExpanded(false));
		head.appendChild(title);
		head.appendChild(fold);
		panel.appendChild(head);

		panelBody = document.createElement('div');
		panelBody.appendChild(button('保存当前模型', manualSave));
		panelBody.appendChild(button('复制最近路径', () => {
			const p = state.recent.length ? state.recent[0].path : '';
			if (!p) return notify('还没有保存记录', 2000);
			try {
				if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(p);
			} catch (e) { /* 忽略 */ }
			notify('已复制：' + p, 2600);
		}));

		const row = document.createElement('label');
		css(row, { display: 'flex', alignItems: 'center', gap: '6px', margin: '4px 0 8px 0', cursor: 'pointer' });
		panelToggle = document.createElement('input');
		panelToggle.type = 'checkbox';
		panelToggle.checked = state.enabled;
		panelToggle.addEventListener('change', () => {
			state.enabled = !!panelToggle.checked;
			notify(state.enabled ? '已开启导出接管' : '已暂停导出接管', 2000);
			refreshPanel();
		});
		row.appendChild(panelToggle);
		row.appendChild(document.createTextNode('自动接管导出（推荐）'));
		panelBody.appendChild(row);

		panelHint = document.createElement('div');
		css(panelHint, { color: '#a1a1aa', marginBottom: '6px' });
		panelBody.appendChild(panelHint);

		const recentBox = document.createElement('div');
		recentBox.id = 'mineways-saver-recent';
		css(recentBox, { maxHeight: '128px', overflowY: 'auto', borderTop: '1px solid #3f3f46', paddingTop: '6px' });
		panelBody.appendChild(recentBox);

		panel.appendChild(panelBody);

		// 折叠态：只剩一个悬浮按钮
		const pill = document.createElement('button');
		pill.type = 'button';
		pill.id = 'mineways-saver-pill';
		pill.textContent = '保存到手机';
		css(pill, {
			position: 'fixed', right: '12px', bottom: '84px', zIndex: '2147483000',
			padding: '10px 14px', border: '1px solid #52525b', borderRadius: '20px',
			background: 'rgba(24,24,27,.94)', color: '#fff', fontSize: '13px',
			boxShadow: '0 6px 20px rgba(0,0,0,.45)', cursor: 'pointer',
			fontFamily: 'system-ui, -apple-system, "Noto Sans CJK SC", sans-serif',
		});
		pill.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); setExpanded(true); });
		panel._pill = pill;

		document.body.appendChild(panel);
		document.body.appendChild(pill);
		setExpanded(false);
		refreshPanel();
	};

	const setExpanded = (on) => {
		expanded = !!on;
		if (!panel) return;
		panel.style.display = expanded ? 'block' : 'none';
		if (panel._pill) panel._pill.style.display = expanded ? 'none' : 'block';
	};

	const refreshPanel = () => {
		if (!panel || !panelHint) return;
		panelHint.textContent = hasBridge()
			? (state.enabled ? '导出将直接写入 Download/MinewaysMobile' : '已暂停：导出会回到内核原流程')
			: '未连接文件桥，无法写入本机';
		if (panelToggle) panelToggle.checked = state.enabled;
		const box = document.getElementById('mineways-saver-recent');
		if (!box) return;
		box.innerHTML = '';
		if (state.lastError) {
			const err = document.createElement('div');
			err.textContent = '上次失败：' + state.lastError;
			css(err, { color: '#fca5a5', marginBottom: '4px', wordBreak: 'break-all' });
			box.appendChild(err);
		}
		if (!state.recent.length) {
			const empty = document.createElement('div');
			empty.textContent = '还没有保存记录';
			css(empty, { color: '#71717a' });
			box.appendChild(empty);
			return;
		}
		state.recent.forEach((r) => {
			const item = document.createElement('div');
			item.textContent = (r.name || '') + ' → ' + r.path;
			css(item, { color: '#d4d4d8', marginBottom: '3px', wordBreak: 'break-all' });
			box.appendChild(item);
		});
	};

	/** 面板被内核重绘冲掉时补回去。 */
	const keepPanelAlive = () => setInterval(() => {
		try {
			if (!document.body) return;
			if (panel && !document.body.contains(panel)) {
				document.body.appendChild(panel);
				if (panel._pill) document.body.appendChild(panel._pill);
				setExpanded(expanded);
			} else if (!panel && document.body.children.length) {
				buildPanel();
			}
		} catch (e) { /* 忽略 */ }
	}, 3000);

	// ---------------------------------------------------------------- 手动保存

	const manualSave = () => {
		if (!hasBridge()) return notify('本页未连接 Android 文件桥，无法写入本机', 3000);
		const F = window.Format;
		try {
			// 首选：内核自己的导出流程（会先询问该格式的导出选项，再走 Blockbench.export）
			if (F && F.codec && typeof F.codec.export === 'function') {
				F.codec.export();
				return;
			}
			// 兜底：直接编译当前格式再保存
			if (F && F.codec && typeof F.codec.compile === 'function') {
				const name = (typeof F.codec.fileName === 'function' ? F.codec.fileName()
					: (window.Project && Project.name ? Project.name : 'model') + '.' + (F.codec.extension || 'bbmodel'));
				Promise.resolve(F.codec.compile()).then((content) => {
					saveContent(content, name).then((path) => {
						notify('已保存到 ' + path, 3000);
						remember(sanitizeName(name), path);
					}, (e) => notify('保存失败：' + ((e && e.message) || e), 3200));
				}, (e) => notify('编译失败：' + ((e && e.message) || e), 3200));
				return;
			}
		} catch (e) {
			log('手动保存异常', e);
		}
		notify('请先在内核中打开项目或选择格式，再点“保存当前模型”', 3200);
	};

	// ---------------------------------------------------------------- 对外接口 / 注册

	// 安装钩子：不依赖 onload 是否被调用（脚本标签注入时同样生效）。
	// 经典脚本可能先于内核执行（离线版 index.html 的 defer 顺序有保证，但异常时序下未必），
	// 因此内核尚未就绪时轮询等待，最多约 15 秒。
	let hookAttempts = 0;
	const installHooksWhenReady = () => {
		try { installHooks(); } catch (e) { log('安装钩子失败', e); }
		if (originalExport) return;
		if (++hookAttempts > 75) return;
		setTimeout(installHooksWhenReady, 200);
	};
	installHooksWhenReady();
	try { window.addEventListener('blockbench_ready', installHooksWhenReady, { once: true }); } catch (e) { /* 忽略 */ }
	try { if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', buildPanel); else buildPanel(); } catch (e) { log('构建面板失败', e); }
	try { keepPanelAlive(); } catch (e) { /* 忽略 */ }

	window.MMSave = {
		state: state,
		save: (content, name, mime) => saveContent(content, name, mime),
		saveNow: manualSave,
		setEnabled: (on) => { state.enabled = !!on; refreshPanel(); },
		recent: () => state.recent.slice(),
		logs: () => state.logs.slice(),
		/** 原生 DownloadListener 兜底：拿到 blob: 地址时反向请页面写盘。 */
		rescueBlob: (url) => {
			const blob = anchorBlob(url);
			if (!blob) return false;
			const name = sanitizeName('model');
			saveContent(blob, name).then((path) => notify('已保存到 ' + path, 3000), (e) => notify('保存失败：' + ((e && e.message) || e), 3200));
			return true;
		},
	};

	/**
	 * 按官方流程登记插件：只有加载器已为本插件创建占位实例时才登记
	 * （脚本标签注入时没有占位实例，此时跳过登记、只用已安装的钩子，避免弹加载失败）。
	 */
	function maybeRegister() {
		const PluginCtor = window.BBPlugin || window.Plugin;
		const registered = window.Plugins && window.Plugins.registered;
		if (!PluginCtor || !registered || !registered[PLUGIN_ID]) {
			console.log('[mineways_saver] 未找到占位实例，仅安装保存钩子（脚本注入模式）');
			return false;
		}
		PluginCtor.register(PLUGIN_ID, {
			title: 'MinewaysMobile 保存助手',
			author: 'MinewaysMobile',
			description: '把 Blockbench 的导出直接写入手机 Download/MinewaysMobile 目录（修复网页版导出“提示成功但无文件”）。',
			icon: 'fa-download',
			version: '1.0.0',
			onload() {
				installHooks();
				buildPanel();
				refreshPanel();
				log('插件已加载');
			},
			onunload() {
				uninstallHooks();
				try {
					if (panel) panel.remove();
					if (panel && panel._pill) panel._pill.remove();
				} catch (e) { /* 忽略 */ }
				panel = null;
			},
		});
		return true;
	}

	maybeRegister();
})();
