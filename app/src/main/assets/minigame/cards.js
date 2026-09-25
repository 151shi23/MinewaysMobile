'use strict';

/* 卡牌系统：101 个机制，每个分初阶/进阶/终极三档，共 303 张不重样。
   每关不放回抽 13 张，按顺序铺成赛道。 */

const RARITY = {
  1: { name: '普通', c1: '#5c6579', c2: '#98a3b8' },
  2: { name: '稀有', c1: '#2f6aa8', c2: '#5aa9e6' },
  3: { name: '史诗', c1: '#6b3aa8', c2: '#a86ce8' },
  4: { name: '传说', c1: '#a8761a', c2: '#f2c14a' },
};
const TIER_NAME = ['初阶', '进阶', '终极'];
const TIER_RARE = [1, 2, 3];

/* 小工具：在世界里铺一段坑 */
function putGap(w, x, width) {
  w.gaps.push({ x: x, w: width, spikes: Math.max(2, Math.round(width / 44)) });
  w.signs.push({ x: x - 190 });
  addArcCoins(w, x, width);
}
function putCoins(w, x, n, y) {
  for (let i = 0; i < n; i++) w.coins.push({ x: x + i * 76, y: GROUND_Y - (y || 112), phase: i * 1.1, taken: false });
}
function putPlatform(w, x, width, high) {
  const top = GROUND_Y - high;
  w.platforms.push({ x: x, w: width, top: top, h: 70 });
  for (let i = 0; i < 2; i++) w.coins.push({ x: x + width * (0.3 + 0.4 * i), y: top - 70, phase: i, taken: false });
}
function putCrumble(w, x, n, high) {
  for (let i = 0; i < n; i++) {
    w.crumbles.push({ x: x + i * 150, w: 128, top: GROUND_Y - (high || 236), h: 62, t: -1, broken: false, fall: 0 });
  }
}
function putPend(w, x, amp) {
  w.pendulums.push({ x: x, pivotY: 96, len: 470, r: 42, amp: amp || 0.62, spd: rnd(1.02, 1.28), phase: Math.random() * 6.28 });
}
function putSpike(w, x, width) {
  w.spikeTraps.push({ x: x, w: width || 112, phase: Math.random() * 2.6 });
}
function putHidden(w, x, width) {
  w.hiddenSpikes.push({ x: x, w: width || 106, t: -1 });
}
function putCrusher(w, x, travel) {
  w.crushers.push({ x: x, w: rnd(112, 146), high: GROUND_Y - 300, travel: travel || 206, spd: rnd(0.95, 1.25), phase: Math.random() * 6.28 });
}
function putRock(w, x) {
  w.rollingRocks.push({ x: x + 1000, y: GROUND_Y - 54, r: 54, vx: -rnd(130, 175), spin: 0, spawnX: x + 200, alive: true });
}
function putDrop(w, x) {
  w.fallingRocks.push({ x: x, y: -80, vy: 0, r: 42, triggered: false, landed: false });
}
function putBar(w, x, gapH) {
  w.lowBars.push({ x: x, w: rnd(92, 116), gapH: gapH || 94 });
}
function putCeil(w, x, width, low) {
  w.ceilSpikes.push({ x: x, w: width || 160, low: low || 280 });
}
function putSpring(w, x) {
  w.trapSprings.push({ x: x, w: 116, t: -1 });
  w.ceilSpikes.push({ x: x - 40, w: 196, low: rnd(300, 326) });
}
function putPower(w, x, type) {
  w.powers.push({ x: x, y: GROUND_Y - 118, type: type, taken: false });
}
function putCrate(w, x) {
  w.crates.push({ x: x, y: GROUND_Y - rnd(100, 140), taken: false, ph: Math.random() * 6.28 });
}
function putFake(w, x, n) {
  for (let i = 0; i < n; i++) w.fakeFloors.push({ x: x + i * 155, w: 130, broken: false, t: 0 });
}
function putFakeCoin(w, x, n) {
  for (let i = 0; i < n; i++) w.fakeCoins.push({ x: x + i * 74, y: GROUND_Y - rnd(96, 128), phase: i * 1.4, taken: false });
}
function putBait(w, x, n) {
  for (let i = 0; i < n; i++) {
    const gx = x + i * 430;
    const gw = 166;
    w.gaps.push({ x: gx, w: gw, spikes: 4 });
    w.signs.push({ x: gx - 210 });
    for (let k = 0; k < 3; k++) w.coins.push({ x: gx - 160 + k * 64, y: GROUND_Y - 68, phase: k * 0.8, taken: false });
    addArcCoins(w, gx, gw);
  }
}
function putEnemy(w, x, i) {
  w.enemies.push({
    x: x, y0: GROUND_Y - (152 + (i % 2) * 54), amp: 34, spd: rnd(0.9, 1.3),
    phase: Math.random() * 6.28, r: 26, dead: false, deadT: 0,
  });
}
function putBoost(w, x) { w.boosters.push({ x: x, w: 150, gl: 0 }); }
function putPad(w, x) { w.bouncePads.push({ x: x, w: 134, t: -1, pop: 0 }); }
function putChase(w, x) { w.chaseTriggers.push({ x: x, spawned: false, rel: rnd(122, 148) }); }

/* ============================================================
   100 种机制（build 里用 t = 0/1/2 表示档位）
   ============================================================ */
const MECHS = [
  /* ---------- 地形 ---------- */
  { id: 'm01', name: '孤坑', danger: 1, len: 380, desc: '一道坑，跳过去',
    build(w, x, t) { putGap(w, x + 150, 126 + t * 34); } },
  { id: 'm02', name: '连环坑', danger: 2, len: 300, desc: '连续数道坑，节奏要稳',
    build(w, x, t) { for (let i = 0; i <= t + 1; i++) putGap(w, x + 130 + i * 268, 132 + t * 14); } },
  { id: 'm03', name: '宽坑', danger: 2, len: 460, desc: '一道极宽的坑',
    build(w, x, t) { putGap(w, x + 170, 176 + t * 44); } },
  { id: 'm04', name: '双坑窄岛', danger: 3, len: 700, desc: '两坑之间只有一小块落脚地',
    build(w, x, t) { putGap(w, x + 140, 150); putGap(w, x + 390 + t * 20, 150 + t * 20); } },
  { id: 'm05', name: '深坑', danger: 3, len: 480, desc: '宽坑且没有金币引导',
    build(w, x, t) { const gw = 190 + t * 40; w.gaps.push({ x: x + 170, w: gw, spikes: 6 }); w.signs.push({ x: x - 20 }); } },
  { id: 'm06', name: '坑后坑', danger: 3, len: 560, desc: '刚落地立刻又是坑',
    build(w, x, t) { putGap(w, x + 140, 150); putGap(w, x + 140 + 150 + 210 - t * 30, 150 + t * 16); } },
  { id: 'm07', name: '阶梯地形', danger: 2, len: 560, desc: '坑与平台交替上下',
    build(w, x, t) { putGap(w, x + 130, 150); putPlatform(w, x + 320, 150 + t * 30, 210 + t * 30); putGap(w, x + 540, 150 + t * 20); } },
  { id: 'm08', name: '塌方段', danger: 2, len: 460, desc: '地面大片下沉',
    build(w, x, t) { putGap(w, x + 150, 160 + t * 30); putFake(w, x + 330 + t * 20, 1 + t); } },
  { id: 'm09', name: '断桥', danger: 2, len: 500, desc: '几块小平台悬在坑上',
    build(w, x, t) { const gw = 300 + t * 60; w.gaps.push({ x: x + 150, w: gw, spikes: 6 }); w.signs.push({ x: x - 20 }); for (let i = 0; i < t + 1; i++) putPlatform(w, x + 200 + i * 160, 96, 220); } },
  { id: 'm10', name: '窄桥', danger: 3, len: 520, desc: '坑中央只有一块落脚点',
    build(w, x, t) { const gw = 340 + t * 50; w.gaps.push({ x: x + 160, w: gw, spikes: 7 }); w.signs.push({ x: x - 20 }); putPlatform(w, x + 160 + gw / 2 - 62, 124, 200 + t * 24); } },
  { id: 'm11', name: '浮空台', danger: 1, len: 440, desc: '一座空中平台',
    build(w, x, t) { putPlatform(w, x + 170, 200 + t * 40, 240 + t * 20); } },
  { id: 'm12', name: '平台链', danger: 2, len: 560, desc: '连续几座平台',
    build(w, x, t) { for (let i = 0; i <= t; i++) putPlatform(w, x + 160 + i * 220, 170, 230 + (i % 2) * 36); } },
  { id: 'm13', name: '高台', danger: 2, len: 480, desc: '高台上的金币',
    build(w, x, t) { putPlatform(w, x + 170, 210, 262 + t * 8); putGap(w, x + 400, 150); } },
  { id: 'm14', name: '低台', danger: 1, len: 420, desc: '触手可及的平台',
    build(w, x, t) { putPlatform(w, x + 160, 240, 190 + t * 20); } },
  { id: 'm15', name: '移动台', danger: 2, len: 460, desc: '上下摆动的平台',
    build(w, x, t) { const y0 = GROUND_Y - (226 + t * 14); w.movingPlatforms.push({ x: x + 180, w: 180, y0: y0, amp: 42 + t * 10, spd: 1.3 + t * 0.15, phase: 0.7 }); } },
  { id: 'm16', name: '双移动台', danger: 3, len: 640, desc: '两座相位相反的移动台',
    build(w, x, t) { for (let i = 0; i < 2; i++) w.movingPlatforms.push({ x: x + 170 + i * 220, w: 170, y0: GROUND_Y - 236, amp: 46 + t * 8, spd: 1.35, phase: i * 3.14 }); } },
  { id: 'm17', name: '塌陷台', danger: 3, len: 440, desc: '一踩就塌，不能停',
    build(w, x, t) { putCrumble(w, x + 170, 1 + t); } },
  { id: 'm18', name: '塌陷链', danger: 3, len: 620, desc: '连续塌陷平台',
    build(w, x, t) { putCrumble(w, x + 160, 2 + t); } },
  { id: 'm19', name: '混合台', danger: 3, len: 660, desc: '稳固与塌陷交替',
    build(w, x, t) { putPlatform(w, x + 150, 170, 236); putCrumble(w, x + 350, 1 + t); } },
  { id: 'm20', name: '空中走廊', danger: 2, len: 720, desc: '高空平台连续跳跃',
    build(w, x, t) { for (let i = 0; i <= t + 1; i++) putPlatform(w, x + 150 + i * 200, 150, 258); } },
  { id: 'm21', name: '金币桥', danger: 1, len: 420, desc: '金币引路，前面有坑',
    build(w, x, t) { putCoins(w, x + 120, 4, 110); putGap(w, x + 460, 150 + t * 20); } },
  { id: 'm22', name: '金币雨', danger: 0, len: 400, desc: '一大片金币，随便拿',
    build(w, x, t) { for (let i = 0; i < 6 + t * 4; i++) w.coins.push({ x: x + 140 + i * 62, y: GROUND_Y - (86 + (i % 3) * 42), phase: i, taken: false }); } },
  { id: 'm23', name: '金币迷宫', danger: 2, len: 520, desc: '金币摆在危险位置',
    build(w, x, t) { putGap(w, x + 190, 160); putCoins(w, x + 120, 3, 66); putCoins(w, x + 380, 3 + t, 150); } },
  { id: 'm24', name: '喘息段', danger: 0, len: 340, desc: '一段安全路，调整呼吸',
    build(w, x, t) { putCoins(w, x + 140, 3 + t, 118); } },
  { id: 'm25', name: '长跑道', danger: 0, len: 520, desc: '没有机关，纯粹加速',
    build(w, x, t) { putCoins(w, x + 160, 5 + t, 108); } },

  /* ---------- 机关 ---------- */
  { id: 'm26', name: '地刺', danger: 2, len: 390, desc: '会弹出来的尖刺',
    build(w, x, t) { putSpike(w, x + 160, 106 + t * 12); } },
  { id: 'm27', name: '刺阵', danger: 3, len: 300, desc: '连续弹刺，节奏要紧',
    build(w, x, t) { for (let i = 0; i <= t + 1; i++) putSpike(w, x + 140 + i * 250, 100 + t * 10); } },
  { id: 'm28', name: '暗刺', danger: 3, len: 390, desc: '没有预警的尖刺',
    build(w, x, t) { putHidden(w, x + 160, 100 + t * 12); } },
  { id: 'm29', name: '暗刺群', danger: 3, len: 560, desc: '两处无声尖刺',
    build(w, x, t) { putHidden(w, x + 150, 100); putHidden(w, x + 460, 100 + t * 12); } },
  { id: 'm30', name: '明暗刺', danger: 3, len: 620, desc: '有预警与无预警混排',
    build(w, x, t) { putSpike(w, x + 150); putHidden(w, x + 420, 104 + t * 10); } },
  { id: 'm31', name: '顶刺', danger: 2, len: 420, desc: '天空垂下的尖刺',
    build(w, x, t) { putCeil(w, x + 170, 150 + t * 30, 282 - t * 6); } },
  { id: 'm32', name: '顶刺阵', danger: 3, len: 400, desc: '连续顶刺，压低跳跃',
    build(w, x, t) { for (let i = 0; i <= t; i++) putCeil(w, x + 150 + i * 220, 140, 286 - t * 8); } },
  { id: 'm33', name: '顶刺陷坑', danger: 3, len: 520, desc: '坑上有刺，只能用中低跳钻过去',
    build(w, x, t) { putGap(w, x + 170, 108 + t * 10); putCeil(w, x + 196, 74 + t * 18, 296); } },
  { id: 'm34', name: '低横杆', danger: 2, len: 420, desc: '必须滑铲钻过去',
    build(w, x, t) { putBar(w, x + 160, 96 - t * 2); } },
  { id: 'm35', name: '横杆阵', danger: 3, len: 340, desc: '连续低杆',
    build(w, x, t) { for (let i = 0; i <= t + 1; i++) putBar(w, x + 150 + i * 260, 94 - t * 2); } },
  { id: 'm36', name: '杆后坑', danger: 3, len: 560, desc: '滑铲刚完就要起跳',
    build(w, x, t) { putBar(w, x + 150); putGap(w, x + 320 + t * 20, 158 + t * 16); } },
  { id: 'm37', name: '摇摆锤', danger: 2, len: 450, desc: '一座钟摆铁锤',
    build(w, x, t) { putPend(w, x + 200, 0.56 + t * 0.07); } },
  { id: 'm38', name: '双锤', danger: 3, len: 620, desc: '两座反相摆锤',
    build(w, x, t) { putPend(w, x + 190, 0.6); putPend(w, x + 430 + t * 20, 0.6 + t * 0.06); } },
  { id: 'm39', name: '三锤', danger: 3, len: 820, desc: '三座摆锤连过',
    build(w, x, t) { for (let i = 0; i <= t; i++) putPend(w, x + 190 + i * 240, 0.58 + (i % 2) * 0.1); } },
  { id: 'm40', name: '锤与坑', danger: 3, len: 600, desc: '躲锤的同时还要跳坑',
    build(w, x, t) { putPend(w, x + 190, 0.6); putGap(w, x + 380, 158 + t * 18); } },
  { id: 'm41', name: '迎面滚石', danger: 2, len: 460, desc: '石头迎面滚来',
    build(w, x, t) { putRock(w, x + 200); } },
  { id: 'm42', name: '滚石群', danger: 3, len: 620, desc: '两块石头接连滚来',
    build(w, x, t) { putRock(w, x + 180); putRock(w, x + 180 + 320 - t * 40); } },
  { id: 'm43', name: '背后追石', danger: 3, len: 480, desc: '身后滚来的石头',
    build(w, x, t) { putChase(w, x + 220); if (t >= 1) putChase(w, x + 420); } },
  { id: 'm44', name: '落石', danger: 3, len: 420, desc: '红圈落下巨石',
    build(w, x, t) { putDrop(w, x + 190); } },
  { id: 'm45', name: '石雨', danger: 3, len: 560, desc: '连续落石',
    build(w, x, t) { for (let i = 0; i <= t; i++) putDrop(w, x + 170 + i * 200, 0); } },
  { id: 'm46', name: '石雨陷坑', danger: 3, len: 600, desc: '落石罩在坑上',
    build(w, x, t) { putGap(w, x + 150, 156); putDrop(w, x + 200); if (t >= 1) putDrop(w, x + 380); } },
  { id: 'm47', name: '天花板压制', danger: 3, len: 440, desc: '石板往复下压',
    build(w, x, t) { putCrusher(w, x + 180, 200 + t * 12); } },
  { id: 'm48', name: '双压制', danger: 3, len: 640, desc: '两块石板交替下压',
    build(w, x, t) { putCrusher(w, x + 170, 204); putCrusher(w, x + 400 + t * 20, 204); } },
  { id: 'm49', name: '压制陷坑', danger: 3, len: 560, desc: '石板下方是坑',
    build(w, x, t) { putGap(w, x + 190, 150 + t * 20); putCrusher(w, x + 170, 206); } },
  { id: 'm50', name: '陷阱弹簧', danger: 3, len: 430, desc: '踩上直接弹进顶刺',
    build(w, x, t) { putSpring(w, x + 170); } },
  { id: 'm51', name: '弹簧阵', danger: 3, len: 620, desc: '两处弹簧陷阱',
    build(w, x, t) { putSpring(w, x + 150); putSpring(w, x + 380 + t * 20); } },
  { id: 'm52', name: '弹跳板', danger: 0, len: 480, desc: '踩它飞越深坑',
    build(w, x, t) { putPad(w, x + 160); const gw = 280 + t * 40; w.gaps.push({ x: x + 288, w: gw, spikes: 6 }); w.signs.push({ x: x + 40 }); } },
  { id: 'm53', name: '加速带', danger: 0, len: 430, desc: '提速冲刺一段',
    build(w, x, t) { putBoost(w, x + 170); } },
  { id: 'm54', name: '减速带', danger: 0, len: 430, desc: '降速换缓冲',
    build(w, x, t) { putPower(w, x + 170, 'slow'); } },
  { id: 'm55', name: '混合机关', danger: 3, len: 620, desc: '随机两种机关叠加',
    build(w, x, t) {
      const pool = ['spike', 'bar', 'pend', 'crusher', 'drop', 'hidden'];
      for (let i = 0; i <= t; i++) {
        const k = pool[Math.floor(Math.random() * pool.length)];
        const px = x + 160 + i * 240;
        if (k === 'spike') putSpike(w, px);
        else if (k === 'bar') putBar(w, px);
        else if (k === 'pend') putPend(w, px + 40);
        else if (k === 'crusher') putCrusher(w, px);
        else if (k === 'drop') putDrop(w, px + 40);
        else putHidden(w, px);
      }
    } },

  /* ---------- 坑人 ---------- */
  { id: 'm56', name: '假地面', danger: 3, len: 400, desc: '看着是地面，踩了就塌',
    build(w, x, t) { putFake(w, x + 150, 1 + t); } },
  { id: 'm57', name: '假地长廊', danger: 3, len: 560, desc: '整段地面都是假的',
    build(w, x, t) { putFake(w, x + 150, 2 + t); } },
  { id: 'm58', name: '全假地面', danger: 3, len: 700, desc: '一眼望不到真地',
    build(w, x, t) { putFake(w, x + 140, 3 + t); } },
  { id: 'm59', name: '假地陷坑', danger: 3, len: 640, desc: '假地面紧接真坑',
    build(w, x, t) { putFake(w, x + 140, 1 + t); putGap(w, x + 150 + (1 + t) * 155 + 60, 150); } },
  { id: 'm60', name: '假金币', danger: 2, len: 380, desc: '暗红金币，吃了扣分',
    build(w, x, t) { putFakeCoin(w, x + 150, 2 + t); } },
  { id: 'm61', name: '假金币群', danger: 2, len: 520, desc: '一大片假金币',
    build(w, x, t) { putFakeCoin(w, x + 140, 4 + t); } },
  { id: 'm62', name: '真假混排', danger: 3, len: 560, desc: '真金币里混着假的',
    build(w, x, t) {
      for (let i = 0; i < 3 + t; i++) {
        const px = x + 140 + i * 78;
        if (i % 2 === 0) w.coins.push({ x: px, y: GROUND_Y - 112, phase: i, taken: false });
        else w.fakeCoins.push({ x: px, y: GROUND_Y - 112, phase: i * 1.3, taken: false });
      }
    } },
  { id: 'm63', name: '诱饵金币', danger: 2, len: 440, desc: '金币贴着坑口，诱你晚跳',
    build(w, x, t) { putBait(w, x + 40, 1 + t); } },
  { id: 'm64', name: '双重诱饵', danger: 3, len: 620, desc: '连续两组诱饵',
    build(w, x, t) { putBait(w, x + 40, 2 + t); } },
  { id: 'm65', name: '诱饵暗刺', danger: 3, len: 620, desc: '诱饵坑后还埋着暗刺',
    build(w, x, t) { putBait(w, x + 40, 1); putHidden(w, x + 560, 104 + t * 10); } },
  { id: 'm66', name: '假平台', danger: 3, len: 480, desc: '平台是塌的，别停',
    build(w, x, t) { putCrumble(w, x + 170, 1 + t); putGap(w, x + 420, 150); } },
  { id: 'm67', name: '无声陷阱', danger: 3, len: 400, desc: '完全没有征兆的一击',
    build(w, x, t) { putHidden(w, x + 180, 100 + t * 14); } },
  { id: 'm68', name: '明暗夹击', danger: 3, len: 620, desc: '先给你看一个，再阴你一次',
    build(w, x, t) { putSpike(w, x + 150); putHidden(w, x + 430 + t * 20, 104); } },
  { id: 'm69', name: '刺后坑', danger: 3, len: 560, desc: '躲过刺就是坑',
    build(w, x, t) { putSpike(w, x + 150, 104); putGap(w, x + 330 + t * 30, 158 + t * 14); } },
  { id: 'm70', name: '三连陷阱', danger: 3, len: 760, desc: '刺、坑、刺连环',
    build(w, x, t) { putSpike(w, x + 140); putGap(w, x + 360, 152); putHidden(w, x + 620, 104 + t * 10); } },
  { id: 'm71', name: '弹进刺里', danger: 3, len: 440, desc: '弹簧把你送进顶刺',
    build(w, x, t) { putSpring(w, x + 170); if (t >= 1) putHidden(w, x + 400, 104); } },
  { id: 'm72', name: '弹进深坑', danger: 3, len: 620, desc: '弹跳板后面是宽坑',
    build(w, x, t) { putPad(w, x + 150); const gw = 360 + t * 40; w.gaps.push({ x: x + 280, w: gw, spikes: 8 }); w.signs.push({ x: x + 20 }); } },
  { id: 'm73', name: '低空封锁', danger: 3, len: 600, desc: '顶刺压着低横杆，滑铲的时机要卡准',
    build(w, x, t) { putCeil(w, x + 238, 142, 292); putBar(w, x + 300, 96); if (t >= 1) putBar(w, x + 480, 94); } },
  { id: 'm74', name: '追击陷坑', danger: 3, len: 640, desc: '石头追着你，前面还有坑',
    build(w, x, t) { putChase(w, x + 200); putGap(w, x + 460, 160 + t * 16); } },
  { id: 'm75', name: '天地夹击', danger: 3, len: 620, desc: '顶刺与地刺同时封路',
    build(w, x, t) { putCeil(w, x + 150, 200, 288); putSpike(w, x + 170, 110); putHidden(w, x + 440, 104 + t * 8); } },

  /* ---------- 增益 ---------- */
  { id: 'm76', name: '磁力补给', danger: 0, len: 400, desc: '拾取后自动吸金',
    build(w, x, t) { putPower(w, x + 170, 'magnet'); putCoins(w, x + 200, 3 + t, 108); } },
  { id: 'm77', name: '护盾祭坛', danger: 0, len: 400, desc: '抵挡一次死亡',
    build(w, x, t) { putPower(w, x + 170, 'shield'); if (t >= 2) putPower(w, x + 260, 'shield'); } },
  { id: 'm78', name: '二段跳符', danger: 0, len: 420, desc: '获得空中二段跳',
    build(w, x, t) { putPower(w, x + 170, 'djump'); if (t >= 1) putGap(w, x + 420, 190); } },
  { id: 'm79', name: '无敌星', danger: 0, len: 400, desc: '短时间免疫伤害',
    build(w, x, t) { putPower(w, x + 170, 'invinc'); } },
  { id: 'm80', name: '双倍金币', danger: 0, len: 400, desc: '一段时间金币翻倍',
    build(w, x, t) { putPower(w, x + 170, 'x2'); putCoins(w, x + 220, 4 + t, 110); } },
  { id: 'm81', name: '极速冲刺', danger: 0, len: 400, desc: '大幅提速',
    build(w, x, t) { putPower(w, x + 170, 'sprint'); } },
  { id: 'm82', name: '道具箱', danger: 0, len: 390, desc: '随机开出一样增益',
    build(w, x, t) { putCrate(w, x + 170); } },
  { id: 'm83', name: '双箱', danger: 0, len: 520, desc: '两个箱子连开',
    build(w, x, t) { putCrate(w, x + 150); putCrate(w, x + 320); if (t >= 1) putCrate(w, x + 460); } },
  { id: 'm84', name: '金币大放送', danger: 0, len: 520, desc: '磁铁配漫天金币',
    build(w, x, t) { putPower(w, x + 140, 'magnet'); for (let i = 0; i < 8 + t * 3; i++) w.coins.push({ x: x + 200 + i * 58, y: GROUND_Y - (90 + (i % 3) * 40), phase: i, taken: false }); } },
  { id: 'm85', name: '补给站', danger: 0, len: 460, desc: '护盾加金币',
    build(w, x, t) { putPower(w, x + 150, 'shield'); putCoins(w, x + 200, 4 + t, 112); } },
  { id: 'm86', name: '全面强化', danger: 0, len: 520, desc: '随机两样增益',
    build(w, x, t) {
      const pool = ['magnet', 'djump', 'x2', 'invinc', 'slow', 'sprint', 'shield'];
      for (let i = 0; i <= t; i++) putPower(w, x + 150 + i * 150, pool[Math.floor(Math.random() * pool.length)]);
    } },
  { id: 'm87', name: '幸运通道', danger: 0, len: 560, desc: '磁铁加金币雨，一路爽拿',
    build(w, x, t) { putPower(w, x + 140, 'magnet'); putPower(w, x + 260, 'x2'); for (let i = 0; i < 6 + t * 2; i++) w.coins.push({ x: x + 320 + i * 62, y: GROUND_Y - 108, phase: i, taken: false }); } },

  /* ---------- 极限 ---------- */
  { id: 'm88', name: '锤阵风暴', danger: 3, len: 900, desc: '四座摆锤连过',
    build(w, x, t) { for (let i = 0; i < 3 + t; i++) putPend(w, x + 180 + i * 220, 0.58 + (i % 2) * 0.1); } },
  { id: 'm89', name: '刺海', danger: 3, len: 820, desc: '满地是刺',
    build(w, x, t) { for (let i = 0; i < 3 + t; i++) { const px = x + 150 + i * 210; if (i % 2) putHidden(w, px); else putSpike(w, px); } } },
  { id: 'm90', name: '石流', danger: 3, len: 860, desc: '滚石与落石混流',
    build(w, x, t) { putRock(w, x + 180); putDrop(w, x + 380); if (t >= 1) putDrop(w, x + 620); putRock(w, x + 800); } },
  { id: 'm91', name: '天崩', danger: 3, len: 880, desc: '石板与落石一起砸',
    build(w, x, t) { putCrusher(w, x + 170); putDrop(w, x + 360); if (t >= 1) putCrusher(w, x + 560); if (t >= 2) putDrop(w, x + 740); } },
  { id: 'm92', name: '死亡跑道', danger: 3, len: 900, desc: '提速冲三连大坑',
    build(w, x, t) { putBoost(w, x + 140); for (let i = 0; i < 2 + t; i++) putGap(w, x + 360 + i * 300, 186 + t * 14); } },
  { id: 'm93', name: '极限窄桥', danger: 3, len: 900, desc: '窄塌陷平台架在深坑上',
    build(w, x, t) {
      const gw = 700 + t * 80;
      w.gaps.push({ x: x + 130, w: gw, spikes: 12 });
      w.signs.push({ x: x - 30 });
      for (let i = 0; i < 4; i++) w.crumbles.push({ x: x + 180 + i * 160, w: 96, top: GROUND_Y - (214 + (i % 2) * 40), h: 58, t: -1, broken: false, fall: 0 });
    } },
  { id: 'm94', name: '深渊试炼', danger: 3, len: 860, desc: '大坑、平台、顶刺三样齐上',
    build(w, x, t) { putGap(w, x + 150, 200 + t * 16); putPlatform(w, x + 200 + 200, 130, 230); putCeil(w, x + 300, 180, 286); } },
  { id: 'm95', name: '崩坏区', danger: 3, len: 900, desc: '整片地面都在塌',
    build(w, x, t) { putFake(w, x + 130, 4 + t); if (t >= 1) putSpike(w, x + 780, 110); } },
  { id: 'm96', name: '完美试炼', danger: 3, len: 960, desc: '坑、刺、锤、石各来一遍',
    build(w, x, t) {
      putGap(w, x + 140, 150);
      putPend(w, x + 400, 0.6);
      putSpike(w, x + 620, 108);
      putDrop(w, x + 820);
      if (t >= 1) putBar(w, x + 980, 95);
    } },
  { id: 'm97', name: '混沌领域', danger: 3, len: 1000, desc: '六种机关随机砸下来',
    build(w, x, t) {
      const pool = ['spike', 'hidden', 'bar', 'pend', 'drop', 'crusher', 'spring'];
      for (let i = 0; i < 5 + t; i++) {
        const px = x + 150 + i * 190;
        const k = pool[Math.floor(Math.random() * pool.length)];
        if (k === 'spike') putSpike(w, px);
        else if (k === 'hidden') putHidden(w, px);
        else if (k === 'bar') putBar(w, px);
        else if (k === 'pend') putPend(w, px + 30);
        else if (k === 'drop') putDrop(w, px + 30);
        else if (k === 'crusher') putCrusher(w, px);
        else putSpring(w, px);
      }
    } },
  { id: 'm98', name: '绝望连击', danger: 3, len: 1000, desc: '机关一个接一个不停',
    build(w, x, t) {
      putSpike(w, x + 140);
      putGap(w, x + 340, 152);
      putBar(w, x + 560, 95);
      putHidden(w, x + 740, 106);
      if (t >= 1) putPend(w, x + 920, 0.62);
      if (t >= 2) putDrop(w, x + 1120);
    } },
  { id: 'm99', name: '天地绝境', danger: 3, len: 940, desc: '上面压下、下面陷落',
    build(w, x, t) { putCrusher(w, x + 170); putFake(w, x + 320, 1 + t); if (t >= 1) putCeil(w, x + 600, 220, 290); } },
  { id: 'm100', name: '万年坑', danger: 3, len: 1200, desc: '全牌库最狠的一套组合',
    build(w, x, t) {
      putGap(w, x + 140, 160);
      putFake(w, x + 340, 1 + t);
      putPend(w, x + 620, 0.62);
      putSpike(w, x + 820, 112);
      putCrusher(w, x + 1000);
      putEnemy(w, x + 1160, 0);
      putHidden(w, x + 1300, 108);
      if (t >= 1) putDrop(w, x + 1340);
      if (t >= 2) putChase(w, x + 1500);
    } },

  /* ---------- 生物 ---------- */
  { id: 'm101', name: '浮空怪', danger: 2, len: 500, desc: '踩它头顶借力弹起，还回补一次补跳',
    build(w, x, t) { for (let i = 0; i <= t; i++) putEnemy(w, x + 180 + i * 210, i); } },
];

/* ============================================================
   300 张 = 100 机制 × 3 档
   ============================================================ */
function makeLibrary() {
  const lib = [];
  for (const m of MECHS) {
    for (let t = 0; t < 3; t++) {
      lib.push({
        id: m.id + '_' + t,
        name: m.name + '·' + TIER_NAME[t],
        mech: m,
        tier: t,
        rarity: (m.danger >= 3 && t === 2) ? 4 : TIER_RARE[t],
        danger: Math.min(3, m.danger + (t === 2 ? 1 : 0)),
        tag: m.desc,
        len: Math.round(m.len * (1 + t * 0.32)),
      });
    }
  }
  return lib;
}

const CARD_LIBRARY = makeLibrary();
const CARD_TOTAL = CARD_LIBRARY.length;
const CARDS_PER_LEVEL = 13;

/* 扫描世界内所有机关的最右边界 */
function worldMaxX(w) {
  let m = 0;
  const arrs = [
    w.gaps, w.fakeFloors, w.spikeTraps, w.hiddenSpikes, w.lowBars, w.ceilSpikes,
    w.bouncePads, w.trapSprings, w.movingPlatforms, w.platforms, w.pendulums,
    w.rollingRocks, w.fallingRocks, w.boosters, w.fakeCoins, w.crushers,
    w.crumbles, w.powers, w.crates, w.chaseTriggers, w.coins, w.signs,
  ];
  for (const a of arrs) {
    if (!a) continue;
    for (let i = 0; i < a.length; i++) {
      const o = a[i];
      // 滚石的实际位置在 spawnX，生成坐标 x 是远处起点
      const ox = (o.spawnX !== undefined) ? o.spawnX : (o.x || 0);
      const rx = ox + (o.w || 0);
      if (rx > m) m = rx;
    }
  }
  return m;
}

/* 铺一段牌：以实际内容末端为准，避免与下一张牌重叠 */
function buildCard(card, w, x) {
  card.mech.build(w, x, card.tier);
  return Math.max(x + card.len, worldMaxX(w));
}

// 加权抽 13 张，不放回
function drawLevelCards(levelIdx) {
  const pool = CARD_LIBRARY.slice();
  const out = [];
  const bias = c => {
    const base = c.rarity === 1 ? 1 : c.rarity === 2 ? 0.66 : 0.36;
    return base * (1 + (c.rarity - 1) * levelIdx * 0.15);
  };
  const usedMech = {};
  let guard = 0;
  while (out.length < CARDS_PER_LEVEL && pool.length && guard++ < 400) {
    let total = 0;
    const ws = [];
    for (const c of pool) {
      let w = bias(c);
      // 纯增益机制在后段降权，免得太水
      if (c.mech.danger === 0 && levelIdx > 3) w *= 0.6;
      // 同一机制本关只出现一次
      if (usedMech[c.mech.id]) w = 0;
      ws.push(w);
      total += w;
    }
    if (total <= 0) break;
    let r = Math.random() * total;
    let pick = pool.length - 1;
    for (let i = 0; i < pool.length; i++) {
      r -= ws[i];
      if (r <= 0) { pick = i; break; }
    }
    const card = pool[pick];
    if (usedMech[card.mech.id]) { pool.splice(pick, 1); continue; }
    // 难度曲线：前 2 张只出低难度热身，第 3~4 张最多 2 级，之后放开
    const maxDanger = out.length < 2 ? 1 : out.length < 4 ? 2 : 3;
    if (card.danger > maxDanger) { pool.splice(pick, 1); continue; }
    usedMech[card.mech.id] = 1;
    out.push(card);
    pool.splice(pick, 1);
  }
  return out;
}

/* 把 13 张牌铺成赛道，返回终点 x */
function buildFromCards(cards, world, levelIdx) {
  let x = 920;                      // 起步缓冲，给玩家反应时间
  for (const c of cards) x = buildCard(c, world, x) + 165;
  return x + 360;
}

/* 诱饵金币 */
function buildBait(w, gx) {
  const gw = 166;
  w.gaps.push({ x: gx, w: gw, spikes: 4 });
  w.signs.push({ x: gx - 210 });
  for (let i = 0; i < 3; i++) {
    w.coins.push({ x: gx - 160 + i * 64, y: GROUND_Y - 68, phase: i * 0.8, taken: false });
  }
  addArcCoins(w, gx, gw);
}
