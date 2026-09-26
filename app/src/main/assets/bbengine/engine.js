/**
 * MinewaysMobile · bbmodel → OBJ 转换引擎（宿主脚本）
 * =====================================================================
 * 定位：本页面**不是 UI**，而是把内置的离线 Blockbench 内核当成"转换引擎"来用。
 * 内核以 iframe 承载（`../blockbench/index.html`），同源，可直接访问其 window，
 * 因此无需修改 `assets/blockbench/` 下任何文件。
 *
 * 调用链（刻意绕开 Blockbench.export / FileSaver，只取内存里的转换结果）：
 *   1. `loadModelFile({path, content})`  —— 内核自带的加载入口，内部同步走 project 编解码器；
 *   2. 等待 `Outliner.elements` 数量达到 JSON 里声明的 elements 数（确认解析完成）；
 *   3. `Codecs.obj.compile({})` —— OBJ 编解码器编译，会派发 `compile` 事件，
 *      事件载荷 {model, mtl, images} 正好是我们需要的三份产物；
 *   4. 把结果暂存在页面内存，由原生层**分片**拉取（避免一次跨桥传输超大字符串）。
 *
 * 与原生层的通信：结果不能靠 evaluateJavascript 的返回值（它不会等待 Promise），
 * 因此统一通过注入的 Java 对象 `BBEngineHost.onReply(id, ok, payload)` 回调。
 *
 * 说明：内核里 `Codecs.obj.compile()` 输出的顶点坐标已除以 `Settings.get('model_export_scale')`
 * （默认 16），且 vt 的 v 轴内核已自行翻转；原生层只需按需把 `v ` 行的坐标乘回 scale，
 * **不要**再翻转 UV。
 */
(() => {
	'use strict';
	if (window.__bbEngineLoaded) return;
	window.__bbEngineLoaded = true;

	/** 承载内核的 iframe id（见 engine.html）。 */
	const FRAME_ID = 'bb';
	const KERNEL_READY_TIMEOUT = 30000;
	const LOAD_SETTLE_TIMEOUT = 15000;
	/** 输入文件分片长度（字符），避免单次 evaluateJavascript 传入超长字符串。 */
	const CHUNK = 40000;

	const log = (...args) => {
		try {
			console.log('[bbengine]', ...args);
			if (window.BBEngineHost && typeof window.BBEngineHost.onLog === 'function') {
				window.BBEngineHost.onLog(args.map((a) => String(a && a.message ? a.message : a)).join(' '));
			}
		} catch (e) { /* 忽略 */ }
	};

	const frame = () => document.getElementById(FRAME_ID);
	/** 内核 window；同源 iframe，可直接访问。 */
	const kw = () => {
		const f = frame();
		return (f && f.contentWindow) || null;
	};

	/** 最近一次 OBJ 编译结果（{model, mtl, images}）。 */
	let lastCompile = null;
	let hookInstalled = false;
	/** 当前转换产物：[{ key, name, text }]，text 为 obj/mtl 文本或贴图 data URI。 */
	let result = null;
	/** 分片上传的输入缓冲。 */
	let pendingLoad = null;

	// ------------------------------------------------------------------ 与原生层通信

	const reply = (id, ok, payload) => {
		try {
			if (window.BBEngineHost && typeof window.BBEngineHost.onReply === 'function') {
				window.BBEngineHost.onReply(String(id), ok ? 1 : 0,
					JSON.stringify(ok ? (payload === undefined ? null : payload) : String((payload && payload.message) || payload)));
			}
		} catch (e) {
			try { console.error('[bbengine] 回报失败', e); } catch (e2) { /* 忽略 */ }
		}
	};

	const run = (id, fn) => {
		Promise.resolve()
			.then(fn)
			.then((data) => reply(id, true, data))
			.catch((err) => {
				log('失败', err);
				reply(id, false, err);
			});
	};

	// ------------------------------------------------------------------ 内核就绪

	/**
	 * 内核就绪判定。
	 * ★ 注意：绝不能把 `w.Project` 当就绪条件 —— 内核里 window.Project 初始是占位值 0，
	 *   只有"打开一个项目"之后才会变成实例；转换引擎启动时本来就没有项目，
	 *   旧判定 `w.Project && w.Codecs...` 因此在所有设备上永远失败（bb内核启动失败的根因）。
	 *   真正的启动完成标志：Codecs（含 obj 编解码器）与 loadModelFile 已挂到 window。
	 */
	const kernelReady = () => {
		const w = kw();
		return !!(w && w.Codecs && w.Codecs.obj && typeof w.loadModelFile === 'function');
	};

	/** 内核状态快照：就绪判定失败时附带回报，让"启动失败"变成可读的原因。 */
	const diagText = () => {
		const w = kw();
		if (!w) return '内核 iframe 尚未挂载';
		const bits = [];
		bits.push('Codecs=' + (w.Codecs ? '有' : '无'));
		bits.push('Codecs.obj=' + (w.Codecs && w.Codecs.obj ? '有' : '无'));
		bits.push('loadModelFile=' + (typeof w.loadModelFile === 'function' ? '有' : '无'));
		bits.push('Outliner=' + (w.Outliner ? '有' : '无'));
		bits.push('已打开项目=' + (w.Project ? '是' : '否(启动时正常)'));
		try {
			bits.push('setup_successful=' + !!(w.Blockbench && w.Blockbench.setup_successful));
		} catch (e) { /* 忽略 */ }
		try {
			const errs = (w.ErrorLog || []).slice(-3)
				.map((e) => String(e && e.message ? e.message : e)).filter(Boolean).join(' | ');
			if (errs) bits.push('内核错误: ' + errs);
		} catch (e) { /* 忽略 */ }
		return bits.join('，');
	};

	const waitKernel = () => new Promise((resolve, reject) => {
		const t0 = Date.now();
		(function poll() {
			if (kernelReady()) return resolve(true);
			if (Date.now() - t0 > KERNEL_READY_TIMEOUT) {
				return reject(new Error('Blockbench 内核就绪超时（' + KERNEL_READY_TIMEOUT
					+ 'ms）。内核状态：' + diagText()));
			}
			setTimeout(poll, 120);
		})();
	});

	/** 挂一次 compile 监听：载荷里带 mtl 与贴图，比拦截导出更直接。 */
	const installCompileHook = () => {
		if (hookInstalled) return;
		const w = kw();
		w.Codecs.obj.on('compile', (payload) => {
			lastCompile = payload || null;
		});
		hookInstalled = true;
	};

	/** 等解析完成：outliner 节点数达到 bbmodel 里声明的 elements 数（分组只多不少）。 */
	const waitProject = (expected) => new Promise((resolve, reject) => {
		const t0 = Date.now();
		(function poll() {
			const w = kw();
			const n = (w && w.Outliner && Array.isArray(w.Outliner.elements)) ? w.Outliner.elements.length : 0;
			if (n >= expected) return resolve(n);
			if (Date.now() - t0 > LOAD_SETTLE_TIMEOUT) {
				return reject(new Error('项目解析超时：只得到 ' + n + ' / ' + expected + ' 个节点'));
			}
			setTimeout(poll, 100);
		})();
	});

	// ------------------------------------------------------------------ 对外能力

	async function ready() {
		await waitKernel();
		const w = kw();
		installCompileHook();
		return {
			version: (w.Blockbench && w.Blockbench.version) || 'unknown',
			format: (w.Format && w.Format.id) || '',
			scale: (w.Settings && typeof w.Settings.get === 'function') ? w.Settings.get('model_export_scale') : null
		};
	}

	/** 真正加载：文本已在 pendingLoad 里拼好。 */
	async function loadText(text) {
		await waitKernel();
		installCompileHook();

		let expected = 0;
		let parsed = null;
		try {
			parsed = JSON.parse(text);
		} catch (e) {
			throw new Error('不是合法的 bbmodel JSON：' + e.message);
		}
		if (!parsed || !parsed.meta) {
			throw new Error('缺少 meta 字段，这不像是 Blockbench 项目文件');
		}
		if (Array.isArray(parsed.elements)) expected = parsed.elements.length;
		if (expected < 1) throw new Error('bbmodel 里没有任何 elements，无法转换');

		lastCompile = null;
		const w = kw();
		// 内核入口：必须传 {path, content}，传原生 File 对象会报 substring 未定义
		w.loadModelFile({ path: 'model.bbmodel', content: text });
		await waitProject(expected);

		return {
			project: w.Project.name || 'model',
			nodes: w.Outliner.elements.length,
			elements: expected,
			textures: Array.isArray(parsed.textures) ? parsed.textures.length : 0
		};
	}

	/** 编译 OBJ 并把三份产物暂存到内存。 */
	async function exportObj() {
		await waitKernel();
		installCompileHook();
		const w = kw();

		lastCompile = null;
		const compiled = w.Codecs.obj.compile({});
		if (!lastCompile) throw new Error('未捕获到 OBJ 编译结果（内核 compile 事件未派发）');

		const obj = typeof compiled === 'string' ? compiled : (lastCompile.model || '');
		const mtl = lastCompile.mtl || '';
		if (!obj) throw new Error('编译结果为空，项目可能没有可导出的元素');

		const entries = [
			{ key: 'obj', name: 'model.obj', text: obj },
			{ key: 'mtl', name: 'materials.mtl', text: mtl }
		];
		const names = [];
		const images = lastCompile.images || {};
		let i = 0;
		for (const uuid in images) {
			if (!Object.prototype.hasOwnProperty.call(images, uuid)) continue;
			const tex = images[uuid];
			if (!tex || typeof tex.source !== 'string' || tex.source.indexOf('data:') !== 0) continue;
			let nm = String(tex.name || ('texture_' + i));
			if (!/\.png$/i.test(nm)) nm += '.png';
			entries.push({ key: 'tex:' + i, name: nm, text: tex.source });
			names.push(nm);
			i++;
		}

		result = { entries: entries };

		const s = { v: 0, vt: 0, vn: 0, f: 0, usemtl: 0, mtllib: 0 };
		const lines = obj.split('\n');
		for (let k = 0; k < lines.length; k++) {
			const ln = lines[k];
			if (ln.startsWith('v ')) s.v++;
			else if (ln.startsWith('vt ')) s.vt++;
			else if (ln.startsWith('vn ')) s.vn++;
			else if (ln.startsWith('f ')) s.f++;
			else if (ln.startsWith('usemtl ')) s.usemtl++;
			else if (ln.startsWith('mtllib ')) s.mtllib++;
		}

		return {
			project: w.Project.name || 'model',
			scale: (w.Settings && typeof w.Settings.get === 'function') ? (w.Settings.get('model_export_scale') || 16) : 16,
			header: lines.length ? lines[0] : '',
			objLen: obj.length,
			mtlLen: mtl.length,
			textureCount: names.length,
			textureNames: names,
			v: s.v, vt: s.vt, vn: s.vn, f: s.f, usemtl: s.usemtl, mtllib: s.mtllib
		};
	}

	// ------------------------------------------------------------------ 分片拉取

	const find = (key) => {
		if (!result) return null;
		for (let i = 0; i < result.entries.length; i++) {
			if (result.entries[i].key === key) return result.entries[i];
		}
		return null;
	};

	const info = () => {
		if (!result) throw new Error('还没有可拉取的转换结果');
		return result.entries.map((e) => ({ key: e.key, name: e.name, len: e.text.length }));
	};

	const lengthOf = (key) => {
		const e = find(key);
		if (!e) throw new Error('未知的产物键：' + key);
		return e.text.length;
	};

	const sliceOf = (key, off, n) => {
		const e = find(key);
		if (!e) throw new Error('未知的产物键：' + key);
		let end = Math.min(e.text.length, off + n);
		// 不要把代理对切开，否则 JSON 编码会出现孤立代理项
		const last = e.text.charCodeAt(end - 1);
		if (end < e.text.length && last >= 0xD800 && last <= 0xDBFF) end++;
		return e.text.slice(off, end);
	};

	// ------------------------------------------------------------------ 宿主入口

	window.BBEngine = {
		// 输入分片：beginLoad / appendLoad × N / endLoad(id)
		beginLoad: () => { pendingLoad = []; },
		appendLoad: (chunk) => { if (pendingLoad) pendingLoad.push(String(chunk)); },
		endLoad: (id) => {
			const text = pendingLoad ? pendingLoad.join('') : '';
			pendingLoad = null;
			run(id, () => loadText(text));
		},
		// 单次输入（小文件 / 调试用）
		loadText: (id, text) => run(id, () => loadText(String(text))),
		ready: (id) => run(id, ready),
		exportObj: (id) => run(id, exportObj),
		info: (id) => run(id, info),
		length: (id, key) => run(id, () => lengthOf(String(key))),
		name: (id, key) => run(id, () => { const e = find(String(key)); return e ? e.name : ''; }),
		slice: (id, key, off, n) => run(id, () => sliceOf(String(key), Number(off) || 0, Number(n) || 0)),
		reset: (id) => { result = null; lastCompile = null; if (id !== undefined) run(id, () => true); },
		/** 供原生层判断内核 iframe 是否已挂上。 */
		frameReady: () => !!kw()
	};
	// 兼容写法：允许通过 evaluateJavascript 直接取分片长度等数字（无需 Promise）
	window.BBEngine.chunk = CHUNK;

	log('引擎脚本已装载');
})();