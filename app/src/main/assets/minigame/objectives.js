'use strict';

/* 通关目标系统
   每关开赛前抽一个目标，跑到终点只算前提，目标没完成就是失败。
   抽取按关卡号加权，越高越坑，也会避开本关做不到的（没弹跳板就不给弹跳任务）。 */

const OBJECTIVES = [
  /* 1. 基础：只要跑到终点 */
  {
    id: 'reach', name: '抵达终点', color: '#7de08a',
    roll: () => ({}),
    desc: () => '跑到赛道终点即可',
    prog: () => null,
    done: () => true,
    weight: c => (c.idx <= 1 ? 16 : c.idx <= 3 ? 8 : 2),
  },

  /* 2. 收集金币 */
  {
    id: 'coins', name: '收金任务', color: '#ffd34d', accum: true,
    roll: c => {
      const rate = c.idx <= 2 ? rnd(0.28, 0.42) : rnd(0.34, 0.54);
      return { n: Math.max(8, Math.round(c.coinTotal * rate)) };
    },
    desc: d => '收集 ' + d.n + ' 枚金币',
    prog: d => G.coins + ' / ' + d.n,
    done: d => G.coins >= d.n,
    weight: c => (c.coinTotal >= 10 ? 10 : 4),
  },

  /* 3. 限时狂奔 */
  {
    id: 'time', name: '限时狂奔', color: '#8ce8ff',
    roll: c => ({ t: Math.round(c.lenPx / c.speed * rnd(1.14, 1.24)) }),
    desc: d => '在 ' + d.t + ' 秒内抵达终点',
    prog: d => G.runTime.toFixed(0) + 's / ' + d.t + 's',
    done: d => G.runTime <= d.t,
    weight: c => (c.idx <= 1 ? 3 : 9),
  },

  /* 4. 连击 */
  {
    id: 'combo', name: '连击大师', color: '#ff9c8a', accum: true,
    roll: c => ({ n: Math.min(20, 6 + c.idx) }),
    desc: d => '达成 ' + d.n + ' 连击',
    prog: d => G.maxCombo + ' / ' + d.n,
    done: d => G.maxCombo >= d.n,
    weight: c => (c.coinTotal >= 12 ? 8 : 3),
  },

  /* 5. 毫发无伤 */
  {
    id: 'nohit', name: '毫发无伤', color: '#8fc4f2',
    roll: () => ({}),
    desc: () => '全程不受伤（被护盾挡住也算）',
    prog: () => G.hurtCount + ' 次受伤',
    done: () => G.hurtCount === 0,
    weight: c => (c.idx < 3 ? 0 : c.idx < 6 ? 5 : 7),
  },

  /* 6. 开箱任务 */
  {
    id: 'crate', name: '开箱任务', color: '#c9a0ff', accum: true,
    roll: c => ({ n: Math.min(c.crates, 1 + Math.floor(c.idx / 4)) }),
    desc: d => '开启 ' + d.n + ' 个补给箱',
    prog: d => G.crateCount + ' / ' + d.n,
    done: d => G.crateCount >= d.n,
    weight: c => (c.crates > 0 ? 8 : 0),
  },

  /* 7. 弹跳任务 */
  {
    id: 'pad', name: '弹跳任务', color: '#7de0c8', accum: true,
    roll: c => ({ n: Math.min(c.pads, 1 + Math.floor(c.idx / 5)) }),
    desc: d => '踩弹跳板起飞 ' + d.n + ' 次',
    prog: d => G.padBounce + ' / ' + d.n,
    done: d => G.padBounce >= d.n,
    weight: c => (c.pads > 0 ? 8 : 0),
  },

  /* 8. 跳跃狂人 */
  {
  id: 'jump', name: '跳跃狂人', color: '#a8e86c', accum: true,
  roll: c => ({ n: Math.max(8, Math.round(c.lenPx / 800)) }),
    desc: d => '全关跳跃 ' + d.n + ' 次',
    prog: d => G.jumpCount + ' / ' + d.n,
    done: d => G.jumpCount >= d.n,
    weight: () => 5,
  },

  /* 9. 滑铲达人 */
  {
    id: 'slide', name: '滑铲达人', color: '#f2a45a', accum: true,
    roll: c => ({ n: 4 + Math.floor(c.idx / 2) }),
    desc: d => '全关滑铲 ' + d.n + ' 次',
    prog: d => G.slideCount + ' / ' + d.n,
    done: d => G.slideCount >= d.n,
    weight: () => 5,
  },

  /* 10. 空中飞人 */
  {
    id: 'air', name: '空中飞人', color: '#e8d17d', accum: true,
    roll: c => ({ s: +Math.max(2, c.lenPx / 3000).toFixed(1) }),
    desc: d => '累计滞空 ' + d.s + ' 秒',
    prog: d => G.airTime.toFixed(1) + 's / ' + d.s + 's',
    done: d => G.airTime >= d.s,
    weight: c => (c.idx >= 2 ? 6 : 2),
  },
];

/* 按关卡环境加权抽一个目标 */
function rollObjective(idx, w, lenPx, speed) {
  const ctx = {
    idx: idx,
    coinTotal: (w.coins || []).length,
    crates: (w.crates || []).length,
    pads: (w.bouncePads || []).length,
    lenPx: lenPx || 6000,
    speed: speed || 360,
  };
  let total = 0;
  const ws = [];
  for (const o of OBJECTIVES) {
    const wt = Math.max(0, o.weight(ctx));
    ws.push(wt);
    total += wt;
  }
  if (total <= 0) return { id: 'reach', name: '抵达终点', color: '#7de08a', data: {} };
  let r = Math.random() * total;
  let pick = OBJECTIVES.length - 1;
  for (let i = 0; i < OBJECTIVES.length; i++) {
    r -= ws[i];
    if (r <= 0) { pick = i; break; }
  }
  const o = OBJECTIVES[pick];
  let data = o.roll(ctx);
  // 极端情况兜底：目标数量为 0 视为不可用
  if (data && data.n !== undefined && data.n <= 0) data = null;
  if (!data) {
    const alt = OBJECTIVES[0];
    return { id: alt.id, name: alt.name, color: alt.color, data: alt.roll(ctx) };
  }
  return { id: o.id, name: o.name, color: o.color, data: data };
}

function objectiveById(id) {
  for (const o of OBJECTIVES) if (o.id === id) return o;
  return OBJECTIVES[0];
}

function objectiveDesc(obj) {
  if (!obj) return '';
  return objectiveById(obj.id).desc(obj.data);
}

function objectiveProg(obj) {
  if (!obj) return null;
  return objectiveById(obj.id).prog(obj.data);
}

function objectiveDone(obj) {
  if (!obj) return true;
  return !!objectiveById(obj.id).done(obj.data);
}

// 累计型目标能中途达成，达成了就即时提示
function objectiveIsAccum(obj) {
  return !!obj && !!objectiveById(obj.id).accum;
}
