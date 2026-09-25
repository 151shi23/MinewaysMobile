'use strict';

/* 音效和 BGM：全部用 Web Audio 现场合成，不依赖任何音频文件 */

const mtof = n => 440 * Math.pow(2, (n - 69) / 12);

const Snd = {
  ac: null,
  master: null,
  sfxBus: null,
  musicBus: null,
  on: true,
  ready: false,

  init() {
    if (this.ac) {
      if (this.ac.state === 'suspended') this.ac.resume();
      return;
    }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try {
      this.ac = new AC();
      this.master = this.ac.createGain();
      this.master.gain.value = this.on ? 0.5 : 0;
      this.sfxBus = this.ac.createGain();
      this.sfxBus.gain.value = 0.9;
      this.musicBus = this.ac.createGain();
      this.musicBus.gain.value = 0.2;
      this.sfxBus.connect(this.master);
      this.musicBus.connect(this.master);
      this.master.connect(this.ac.destination);
      this.ready = true;
    } catch (e) {
      this.ac = null;
    }
  },

  toggle() {
    this.on = !this.on;
    if (this.master) this.master.gain.value = this.on ? 0.5 : 0;
    return this.on;
  },

  tone(f0, f1, dur, type, vol, when, bus) {
    if (!this.ready || !this.on) return;
    const t = when || this.ac.currentTime;
    const o = this.ac.createOscillator();
    const g = this.ac.createGain();
    o.type = type || 'square';
    o.frequency.setValueAtTime(Math.max(20, f0), t);
    if (f1 && f1 !== f0) o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0002, vol), t + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g);
    g.connect(bus || this.sfxBus);
    o.start(t);
    o.stop(t + dur + 0.05);
  },

  noise(dur, vol, freq, when, bus) {
    if (!this.ready || !this.on) return;
    const t = when || this.ac.currentTime;
    const len = Math.max(1, Math.floor(this.ac.sampleRate * dur));
    const buf = this.ac.createBuffer(1, len, this.ac.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = this.ac.createBufferSource();
    src.buffer = buf;
    const filt = this.ac.createBiquadFilter();
    filt.type = 'bandpass';
    filt.frequency.value = freq || 900;
    filt.Q.value = 0.7;
    const g = this.ac.createGain();
    g.gain.value = vol;
    src.connect(filt);
    filt.connect(g);
    g.connect(bus || this.sfxBus);
    src.start(t);
  },

  /* ---------------- 音效 ---------------- */
  jump()    { this.tone(420, 900, 0.13, 'square', 0.15); this.noise(0.05, 0.05, 1800); },
  double()  { this.tone(640, 1180, 0.12, 'square', 0.13); },
  land()    { this.tone(210, 110, 0.08, 'triangle', 0.16); this.noise(0.06, 0.06, 480); },
  coin(n)   { const p = Math.pow(2, ((n || 0) % 8) / 12); this.tone(988 * p, 1480 * p, 0.09, 'sine', 0.2); this.tone(1976 * p, 2637 * p, 0.07, 'sine', 0.07); },
  die()     { this.noise(0.45, 0.32, 420); this.tone(320, 48, 0.45, 'sawtooth', 0.2); },
  click()   { this.tone(880, 1180, 0.05, 'square', 0.12); },
  back()    { this.tone(620, 420, 0.07, 'square', 0.11); },
  slide()   { this.noise(0.18, 0.09, 1500); this.tone(300, 140, 0.16, 'triangle', 0.07); },
  bounce()  { this.tone(320, 980, 0.2, 'sine', 0.22); this.tone(640, 1560, 0.16, 'triangle', 0.1); },
  spikeOut(){ this.tone(160, 720, 0.09, 'square', 0.16); this.noise(0.12, 0.14, 2600); },
  crack()   { this.noise(0.2, 0.2, 700); this.tone(240, 90, 0.22, 'triangle', 0.12); },
  breakFloor(){ this.noise(0.3, 0.24, 380); this.tone(180, 60, 0.3, 'sawtooth', 0.1); },
  milestone(){ const t = this.ac ? this.ac.currentTime : 0; [0, 4, 7, 12].forEach((s, i) => this.tone(mtof(72 + s), 0, 0.14, 'square', 0.11, t + i * 0.06)); },
  clear()   { const t = this.ac ? this.ac.currentTime : 0; [0, 4, 7, 12, 16, 19].forEach((s, i) => this.tone(mtof(69 + s), mtof(69 + s + 2), 0.22, 'square', 0.14, t + i * 0.09)); },
  star(i)   { this.tone(mtof(84 + i * 4), mtof(96 + i * 4), 0.22, 'sine', 0.16); },
  fail()    { const t = this.ac ? this.ac.currentTime : 0; [0, -3, -7].forEach((s, i) => this.tone(mtof(64 + s), mtof(58 + s), 0.3, 'square', 0.12, t + i * 0.14)); },
  whoosh()  { this.noise(0.28, 0.1, 900); },
  /* 极限闪避：短促上滑的高音「擦身而过」 */
  nearMiss(){ this.tone(1320, 1980, 0.07, 'sine', 0.13); this.tone(2640, 3160, 0.05, 'sine', 0.05); },
  /* 空中补跳：翻身再起的上扬音 */
  airJump() { this.tone(720, 1280, 0.11, 'square', 0.14); this.noise(0.07, 0.06, 2400); },
  /* 命运之门：正常门上扬双音，狂暴门低吼 */
  gate(risk){ risk ? this.tone(180, 70, 0.3, 'sawtooth', 0.18) : this.tone(523, 1046, 0.16, 'triangle', 0.15); },
  /* FEVER 开启：号角感 */
  fever()   { this.tone(660, 1320, 0.28, 'square', 0.12); this.tone(990, 1980, 0.24, 'square', 0.07); },
  // 能力就绪提示（别叫 ready，会和音频标志撞名）
  charged() { this.tone(880, 1320, 0.09, 'triangle', 0.1); this.tone(1760, 2200, 0.07, 'sine', 0.05); },
  /* 能量冲刺释放 */
  dash()    { this.tone(180, 900, 0.34, 'sawtooth', 0.2); this.noise(0.4, 0.2, 1600); },
  /* 踩怪 */
  stomp()   { this.tone(560, 180, 0.12, 'square', 0.2); this.noise(0.14, 0.12, 900); },
};

/* ---------------- BGM：mp3 曲库（HTMLAudio 循环播放） ----------------
   曲目 0 = 默认背景音乐，曲目 1 = 备选，-1 = 关闭。
   浏览器自动播放限制：首次点击/按键（用户手势）后才会真正出声，
   Music.start() 都挂在手势链路里（菜单按钮 / onDown / keydown）。 */
const MUSIC_TRACKS = [
  { name: '默认 BGM', src: 'c122c5e61f5c23837d12dccd2c08899bea6d81f9186971de955e955da3dc2442.mp3' },
  { name: '有氧往返跑', src: '15米有氧渐进性往返跑 （片段）.mp3' },
];

const Music = {
  playing: false,
  idx: 0,          // 当前曲目：-1 关，0..MUSIC_TRACKS.length-1
  audio: null,
  duckV: 0.2,      // 目标音量（沿用原 musicBus 语义：0.2 常态 / 0.08~0.15 剧情压低）

  init() {
    let v = 0;
    try { v = parseInt(localStorage.getItem('jiling_music') || '0', 10); } catch (e) { v = 0; }
    this.idx = v >= -1 && v < MUSIC_TRACKS.length ? v : 0;
  },

  curName() { return this.idx < 0 ? '音乐：关' : MUSIC_TRACKS[this.idx].name; },
  shortName() { return this.idx < 0 ? '关' : '曲' + (this.idx + 1); },

  _apply() {
    if (this.audio) this.audio.volume = this.playing && Snd.on ? this.duckV * 0.75 : 0;
  },

  start() {
    this.init();
    if (this.idx < 0 || this.playing) return;
    try {
      const a = new Audio(encodeURI(MUSIC_TRACKS[this.idx].src));
      a.loop = true;
      a.volume = 0;
      this.audio = a;
      const p = a.play();
      if (p && p.catch) p.catch(() => { this.playing = false; });   // 没手势被拦就先哑着，下次手势再试
      this.playing = true;
      this._apply();
    } catch (e) { this.audio = null; this.playing = false; }
  },

  stop() {
    this.playing = false;
    if (this.audio) { try { this.audio.pause(); } catch (e) {} this.audio = null; }
  },

  duck(v) {
    this.duckV = v;
    this._apply();
  },

  /* 切歌循环：曲1 → 曲2 → 关 → 曲1 …；返回提示文字 */
  cycle() {
    this.init();
    this.stop();
    this.idx = this.idx + 1 >= MUSIC_TRACKS.length ? -1 : this.idx + 1;
    try { localStorage.setItem('jiling_music', String(this.idx)); } catch (e) {}
    this.start();
    Snd.click();
    return this.idx < 0 ? '♪ 音乐已关' : '♪ 音乐：' + MUSIC_TRACKS[this.idx].name;
  },
};
