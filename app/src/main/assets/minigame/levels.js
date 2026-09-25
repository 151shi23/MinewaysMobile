'use strict';

/* 12 个固定关卡的数据和构建脚本，每关一个招牌机关。
   plan 里按 [米数, 类型, 参数] 排布，类型有：
   gap 坑 / hiddenSpike 暗刺 / spikeTrap 伸缩刺 / trapSpring 陷阱弹簧
   crumble 塌陷台 / crusher 压制板 / chaseRock 背后追石 / rollingRock 滚石
   fallingRock 落石 / pendulum 摆锤 / fakeFloor 假地面 / bait 诱饵金币
   lowBar 低杆 / spikeCeil 顶刺 / bouncePad 弹跳板 / booster 加速带
   movingPlatform 移动台 / platform 浮空平台 / fakeCoins 假金币 */

const LEVELS = [
  {
    name: '初次试跑', tip: '跳起来越过坑洞，顺手把金币收下',
    lenM: 150, speed: 336,
    plan: [
      [24, 'coins', { n: 3 }],
      [52, 'gap', { w: 140 }],
      [84, 'coins', { n: 3 }],
      [116, 'gap', { w: 155 }],
      [142, 'coins', { n: 4 }],
    ],
  },
  {
    name: '连环坑', tip: '第三个坑比前两个宽，别按习惯跳',
    lenM: 188, speed: 350,
    plan: [
      [18, 'coins', { n: 3 }],
      [40, 'gap', { w: 142 }],
      [64, 'gap', { w: 142 }],
      [90, 'gap', { w: 184 }],
      [120, 'coins', { n: 3 }],
      [142, 'gap', { w: 152 }],
      [168, 'coins', { n: 4 }],
    ],
  },
  {
    name: '高台跳跃', tip: '有的平台踩上去就会塌，别停',
    lenM: 212, speed: 356,
    plan: [
      [22, 'coins', { n: 3 }],
      [44, 'platform', { w: 200, level: 'mid', coins: 3 }],
      [78, 'gap', { w: 176 }],
      [102, 'crumble', {}],
      [142, 'gap', { w: 186 }],
      [172, 'coins', { n: 5 }],
    ],
  },
  {
    name: '陷阱初现', tip: '第一次会闪红光预警，第二次不会',
    lenM: 224, speed: 360,
    plan: [
      [20, 'coins', { n: 3 }],
      [42, 'spikeTrap', { w: 112 }],
      [68, 'gap', { w: 154 }],
      [92, 'hiddenSpike', { w: 112 }],
      [122, 'spikeTrap', { w: 104 }],
      [148, 'gap', { w: 164 }],
      [176, 'hiddenSpike', { w: 104 }],
      [202, 'coins', { n: 3 }],
    ],
  },
  {
    name: '真假难辨', tip: '盯紧了，有些地面撑不住你',
    lenM: 232, speed: 364,
    plan: [
      [22, 'coins', { n: 3 }],
      [46, 'fakeFloor', { w: 134 }],
      [74, 'gap', { w: 154 }],
      [98, 'fakeFloor', { w: 126 }],
      [124, 'coins', { n: 4 }],
      [148, 'fakeFloor', { w: 144 }],
      [178, 'fakeFloor', { w: 118 }],
      [204, 'coins', { n: 3 }],
    ],
  },
  {
    name: '低头前进', tip: '滑铲刚站起来就要起跳，注意衔接',
    lenM: 242, speed: 368,
    plan: [
      [24, 'lowBar', { w: 94, gapH: 96 }],
      [52, 'gap', { w: 158 }],
      [80, 'lowBar', { w: 114, gapH: 92 }],
      [108, 'gap', { w: 162 }],
      [136, 'lowBar', { w: 94, gapH: 96 }],
      [162, 'gap', { w: 170 }],
      [168, 'spikeCeil', { w: 62, low: 278 }],
      [200, 'coins', { n: 5 }],
    ],
  },
  {
    name: '甜蜜陷阱', tip: '金币越密的地方，死得越快',
    lenM: 258, speed: 372,
    plan: [
      [20, 'coins', { n: 3 }],
      [44, 'bait', {}],
      [72, 'fakeCoins', { n: 4 }],
      [98, 'movingPlatform', { w: 180 }],
      [132, 'gap', { w: 182 }],
      [160, 'bait', {}],
      [190, 'fakeCoins', { n: 3 }],
      [216, 'spikeTrap', { w: 124 }],
      [238, 'coins', { n: 4 }],
    ],
  },
  {
    name: '摇摆时刻', tip: '并排两个锤子反着摆，别用一个节奏过',
    lenM: 264, speed: 376,
    plan: [
      [22, 'coins', { n: 3 }],
      [46, 'pendulum', { amp: 0.62 }],
      [74, 'pendulum', { amp: 0.68 }],
      [106, 'gap', { w: 160 }],
      [134, 'pendulum', { amp: 0.58 }],
      [160, 'coins', { n: 4 }],
      [184, 'spikeTrap', { w: 116 }],
      [214, 'pendulum', { amp: 0.68 }],
      [242, 'coins', { n: 3 }],
    ],
  },
  {
    name: '滚石滚滚', tip: '身后也会有石头，别只顾着看前面',
    lenM: 274, speed: 380,
    plan: [
      [20, 'coins', { n: 3 }],
      [44, 'chaseRock', {}],
      [78, 'gap', { w: 166 }],
      [102, 'rollingRock', {}],
      [132, 'coins', { n: 4 }],
      [156, 'chaseRock', {}],
      [190, 'gap', { w: 172 }],
      [222, 'coins', { n: 3 }],
    ],
  },
  {
    name: '天降横祸', tip: '天上砸石头，头顶石板也在往下压',
    lenM: 288, speed: 384,
    plan: [
      [22, 'coins', { n: 3 }],
      [46, 'fallingRock', {}],
      [72, 'crusher', {}],
      [104, 'gap', { w: 160 }],
      [130, 'fallingRock', {}],
      [158, 'crusher', {}],
      [190, 'coins', { n: 4 }],
      [214, 'fallingRock', {}],
      [242, 'gap', { w: 170 }],
      [266, 'coins', { n: 3 }],
    ],
  },
  {
    name: '加速地狱', tip: '加速带上埋着刺，冲得越快越难躲',
    lenM: 298, speed: 388,
    plan: [
      [20, 'coins', { n: 3 }],
      [42, 'booster', {}],
      [74, 'hiddenSpike', { w: 112 }],
      [104, 'gap', { w: 204 }],
      [132, 'booster', {}],
      [164, 'hiddenSpike', { w: 104 }],
      [192, 'gap', { w: 214 }],
      [222, 'trapSpring', {}],
      [256, 'coins', { n: 4 }],
    ],
  },
  {
    name: '终极试炼', tip: '全部机关轮着来，没别的，就是难',
    lenM: 366, speed: 392,
    plan: [
      [18, 'gap', { w: 148 }],
      [44, 'hiddenSpike', { w: 108 }],
      [68, 'lowBar', { w: 100, gapH: 94 }],
      [92, 'fakeFloor', { w: 140 }],
      [116, 'pendulum', { amp: 0.62 }],
      [142, 'chaseRock', {}],
      [172, 'crumble', {}],
      [210, 'crusher', {}],
      [244, 'fallingRock', {}],
      [270, 'trapSpring', {}],
      [304, 'booster', {}],
      [336, 'gap', { w: 214 }],
      [352, 'spikeTrap', { w: 132 }],
    ],
  },
];

/* 跳跃轨迹金币弧线 */
function addArcCoins(world, gx, gw) {
  for (let i = 0; i < 5; i++) {
    const t = (i + 0.5) / 5;
    world.coins.push({
      x: gx + gw * t,
      y: GROUND_Y - 118 - Math.sin(t * Math.PI) * 100,
      phase: i * 0.9, taken: false,
    });
  }
}

/* 按关卡脚本构建地形，返回终点 x */
function buildLevel(idx, world) {
  const L = LEVELS[idx];
  const P = PX_PER_M;
  const G = GROUND_Y;
  if (!world.chaseTriggers) world.chaseTriggers = [];

  for (let k = 0; k < L.plan.length; k++) {
    const item = L.plan[k];
    const type = item[1];
    const opt = item[2] || {};
    const x = item[0] * P;

    switch (type) {
      case 'coins': {
        const n = opt.n || 3;
        const y = G - (opt.high ? 152 : 112);
        for (let i = 0; i < n; i++) world.coins.push({ x: x + i * 80, y: y, phase: i * 1.1, taken: false });
        break;
      }

      case 'fakeCoins': {
        const n = opt.n || 3;
        for (let i = 0; i < n; i++) {
          world.fakeCoins.push({ x: x + i * 74, y: G - rnd(96, 128), phase: i * 1.4, taken: false });
        }
        break;
      }

      case 'gap': {
        const w = opt.w || 150;
        world.gaps.push({ x: x, w: w, spikes: Math.max(2, Math.round(w / 44)) });
        world.signs.push({ x: x - 200 });
        if (!opt.noArc) addArcCoins(world, x, w);
        break;
      }

      case 'fakeFloor':
        world.fakeFloors.push({ x: x, w: opt.w || 130, broken: false, t: 0 });
        break;

      case 'spikeTrap':
        world.spikeTraps.push({ x: x, w: opt.w || 110, phase: (k * 0.83) % 3 });
        break;

      case 'hiddenSpike':
        world.hiddenSpikes.push({ x: x, w: opt.w || 104, t: -1 });
        break;

      case 'trapSpring': {
        const w = opt.w || 116;
        world.trapSprings.push({ x: x, w: w, t: -1 });
        world.ceilSpikes.push({ x: x - 40, w: w + 80, low: opt.low || rnd(298, 326) });
        break;
      }

      case 'crumble':
        world.crumbles.push({
          x: x, w: opt.w || 128, top: G - (opt.h || 236), h: 62,
          t: -1, broken: false, fall: 0,
        });
        break;

      case 'crusher':
        world.crushers.push({
          x: x, w: opt.w || rnd(112, 148),
          high: G - (opt.high || 300), travel: opt.travel || 206,
          spd: opt.spd || 1.05, phase: (k * 1.13) % 6.283,
        });
        break;

      case 'chaseRock':
        world.chaseTriggers.push({ x: x, spawned: false, rel: opt.rel || rnd(122, 148) });
        break;

      case 'lowBar':
        world.lowBars.push({ x: x, w: opt.w || 96, gapH: opt.gapH || 96 });
        break;

      case 'spikeCeil':
        world.ceilSpikes.push({ x: x, w: opt.w || 60, low: opt.low || 278 });
        break;

      case 'bouncePad':
        world.bouncePads.push({ x: x, w: opt.w || 130, t: -1, pop: 0 });
        break;

      case 'booster':
        world.boosters.push({ x: x, w: opt.w || 150, gl: 0 });
        break;

      case 'pendulum':
        world.pendulums.push({
          x: x, pivotY: opt.pivotY || 96, len: opt.len || 470, r: opt.r || 42,
          amp: opt.amp || 0.62, spd: opt.spd || 1.15, phase: (k * 1.31) % 6.283,
        });
        break;

      case 'rollingRock':
        world.rollingRocks.push({
          x: x + 900, y: G - 54, r: 54, vx: -(opt.vx || 150), spin: 0,
          spawnX: x, alive: true,
        });
        break;

      case 'fallingRock':
        world.fallingRocks.push({
          x: x, y: -80, vy: 0, r: opt.r || 42, triggered: false, landed: false,
        });
        break;

      case 'movingPlatform':
        world.movingPlatforms.push({
          x: x, w: opt.w || 180,
          y0: G - (opt.h || 236), amp: opt.amp || 46,
          spd: opt.spd || 1.35, phase: (k * 1.7) % 6.283,
        });
        break;

      case 'platform': {
        const w = opt.w || 200;
        const top = G - (opt.level === 'high' ? 268 : 240);
        world.platforms.push({ x: x, w: w, top: top, h: 70 });
        const n = opt.coins || 3;
        for (let i = 0; i < n; i++) {
          world.coins.push({
            x: x + w * (0.25 + 0.5 * i / Math.max(1, n - 1)),
            y: top - 70, phase: i, taken: false,
          });
        }
        break;
      }

      case 'bait': {
        const gw = opt.w || 166;
        const gx = x + 158;
        world.gaps.push({ x: gx, w: gw, spikes: Math.max(2, Math.round(gw / 44)) });
        world.signs.push({ x: gx - 210 });
        for (let i = 0; i < 3; i++) {
          world.coins.push({ x: gx - 160 + i * 64, y: G - 68, phase: i * 0.8, taken: false });
        }
        addArcCoins(world, gx, gw);
        break;
      }
    }
  }

  return L.lenM * P;
}
