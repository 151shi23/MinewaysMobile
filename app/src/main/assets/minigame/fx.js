'use strict';

/* 特效：粒子、浮动文字、波纹、冲击环、闪屏、震屏、慢动作 */

const ease = {
  outCubic: t => 1 - Math.pow(1 - t, 3),
  outQuad: t => 1 - (1 - t) * (1 - t),
  inQuad: t => t * t,
  inOutSine: t => -(Math.cos(Math.PI * t) - 1) / 2,
  outBack: t => { const c = 1.9; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); },
  outBounce: t => {
    const n = 7.5625, d = 2.75;
    if (t < 1 / d) return n * t * t;
    if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75;
    if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375;
    return n * (t -= 2.625 / d) * t + 0.984375;
  },
  outElastic: t => {
    if (t === 0 || t === 1) return t;
    const p = 0.32;
    return Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1;
  },
};

// ---- 补间动画 ----
const Tweens = [];
function tween(o) {
  Tweens.push({
    tag: o.tag || 'global',
    t: 0, delay: o.delay || 0, dur: o.dur || 0.3,
    from: o.from || 0, to: o.to === undefined ? 1 : o.to,
    ease: o.ease || ease.outCubic,
    apply: o.apply, onDone: o.onDone,
  });
}
function updateTweens(dt) {
  for (let i = Tweens.length - 1; i >= 0; i--) {
    const w = Tweens[i];
    if (w.delay > 0) { w.delay -= dt; if (w.delay > 0) continue; }
    w.t += dt;
    const k = Math.min(1, w.dur > 0 ? w.t / w.dur : 1);
    if (w.apply) w.apply(w.from + (w.to - w.from) * w.ease(k), k);
    if (k >= 1) {
      if (w.onDone) w.onDone();
      Tweens.splice(i, 1);
    }
  }
}
function clearTweens(tag) {
  for (let i = Tweens.length - 1; i >= 0; i--) {
    if (tag === 'all' || Tweens[i].tag === tag) Tweens.splice(i, 1);
  }
}

// ---- 特效池 ----
const FX = {
  parts: [],      // 世界粒子
  sparts: [],     // 屏幕粒子
  floats: [],     // 世界浮动文字
  sfloats: [],    // 屏幕浮动文字
  ripples: [],    // 按钮涟漪（屏幕）
  rings: [],      // 冲击环
  flashA: 0,
  flashColor: '#ffffff',
  shake: 0,
  vignette: 0,
  timeScale: 1,
  slowT: 0,
  speedLines: 0,

  reset() {
    this.parts.length = 0;
    this.sparts.length = 0;
    this.floats.length = 0;
    this.sfloats.length = 0;
    this.ripples.length = 0;
    this.rings.length = 0;
    this.flashA = 0;
    this.shake = 0;
    this.vignette = 0;
    this.timeScale = 1;
    this.slowT = 0;
    this.speedLines = 0;
    clearTweens('all');
  },

  burst(x, y, n, o) {
    for (let i = 0; i < n; i++) {
      this.parts.push({
        x, y,
        vx: rnd(o.vx0, o.vx1),
        vy: rnd(o.vy0, o.vy1),
        life: 0,
        max: rnd(o.life0 || 0.25, o.life1 || 0.5),
        size: rnd(o.size0 || 5, o.size1 || 12),
        color: o.color || '#ffffff',
        grav: o.grav === undefined ? 260 : o.grav,
        spin: o.spin || 0,
        rot: Math.random() * 6.28,
      });
    }
  },

  sburst(x, y, n, o) {
    for (let i = 0; i < n; i++) {
      this.sparts.push({
        x, y,
        vx: rnd(o.vx0, o.vx1),
        vy: rnd(o.vy0, o.vy1),
        life: 0,
        max: rnd(o.life0 || 0.3, o.life1 || 0.6),
        size: rnd(o.size0 || 4, o.size1 || 10),
        color: o.color || '#ffffff',
        grav: o.grav === undefined ? 380 : o.grav,
      });
    }
  },

  float(x, y, text, o) {
    this.floats.push({
      x, y, text,
      life: 0, max: o.life || 0.9,
      color: o.color || '#ffffff',
      size: o.size || 30,
      vy: o.vy === undefined ? -80 : o.vy,
      scale: o.scale || 1,
    });
  },

  sfloat(x, y, text, o) {
    this.sfloats.push({
      x, y, text,
      life: 0, max: o.life || 1.1,
      color: o.color || '#ffffff',
      size: o.size || 44,
      vy: o.vy === undefined ? -40 : o.vy,
      scale: 1,
    });
  },

  ripple(x, y, o) {
    this.ripples.push({
      x, y,
      life: 0, max: o.max || 0.45,
      r0: o.r0 || 8, r1: o.r1 || 70,
      color: o.color || 'rgba(255,255,255,0.55)',
      width: o.width || 4,
    });
  },

  ring(x, y, o) {
    this.rings.push({
      x, y,
      life: 0, max: o.max || 0.5,
      r0: o.r0 || 6, r1: o.r1 || 90,
      color: o.color || 'rgba(255,255,255,0.8)',
      width: o.width || 6,
    });
  },

  flashScreen(color, a) {
    this.flashColor = color || '#ffffff';
    this.flashA = Math.max(this.flashA, a || 0.5);
  },

  addShake(a) {
    this.shake = Math.min(1.4, this.shake + a);
  },

  slowMo(dur, scale) {
    this.slowT = Math.max(this.slowT, dur);
    this.timeScale = Math.min(this.timeScale, scale === undefined ? 0.35 : scale);
  },

  update(dt) {
    if (this.slowT > 0) {
      this.slowT -= dt;
      if (this.slowT <= 0) this.timeScale = 1;
    }
    const d = dt * this.timeScale;

    for (let i = this.parts.length - 1; i >= 0; i--) {
      const p = this.parts[i];
      p.life += d;
      if (p.life >= p.max) { this.parts.splice(i, 1); continue; }
      p.x += p.vx * d;
      p.y += p.vy * d;
      p.vy += p.grav * d;
      if (p.spin) p.rot += p.spin * d;
    }
    for (let i = this.sparts.length - 1; i >= 0; i--) {
      const p = this.sparts[i];
      p.life += dt;
      if (p.life >= p.max) { this.sparts.splice(i, 1); continue; }
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vy += p.grav * dt;
    }
    for (let i = this.floats.length - 1; i >= 0; i--) {
      const f = this.floats[i];
      f.life += dt;
      if (f.life >= f.max) { this.floats.splice(i, 1); continue; }
      f.y += f.vy * dt;
    }
    for (let i = this.sfloats.length - 1; i >= 0; i--) {
      const f = this.sfloats[i];
      f.life += dt;
      if (f.life >= f.max) { this.sfloats.splice(i, 1); continue; }
      f.y += f.vy * dt;
    }
    for (let i = this.ripples.length - 1; i >= 0; i--) {
      const r = this.ripples[i];
      r.life += dt;
      if (r.life >= r.max) this.ripples.splice(i, 1);
    }
    for (let i = this.rings.length - 1; i >= 0; i--) {
      const r = this.rings[i];
      r.life += d;
      if (r.life >= r.max) this.rings.splice(i, 1);
    }
    if (this.flashA > 0) this.flashA = Math.max(0, this.flashA - dt * 2.2);
    if (this.shake > 0) this.shake = Math.max(0, this.shake - dt * 2.6);
    if (this.vignette > 0) this.vignette = Math.max(0, this.vignette - dt * 0.8);
  },

  // 世界层粒子，跟着镜头滚
  drawWorld(ctx, camX) {
    for (const p of this.parts) {
      const sx = p.x - camX;
      if (sx < -60 || sx > W + 60) continue;
      const k = p.life / p.max;
      ctx.globalAlpha = Math.max(0, 1 - k);
      ctx.fillStyle = p.color;
      const s = p.size * (1 - k * 0.55);
      if (p.spin) {
        ctx.save();
        ctx.translate(sx, p.y);
        ctx.rotate(p.rot);
        ctx.fillRect(-s / 2, -s / 2, s, s);
        ctx.restore();
      } else {
        ctx.fillRect(sx - s / 2, p.y - s / 2, s, s);
      }
    }
    ctx.globalAlpha = 1;

    for (const r of this.rings) {
      const sx = r.x - camX;
      const k = r.life / r.max;
      ctx.globalAlpha = Math.max(0, 1 - k);
      ctx.strokeStyle = r.color;
      ctx.lineWidth = r.width * (1 - k * 0.7);
      ctx.beginPath();
      ctx.arc(sx, r.y, r.r0 + (r.r1 - r.r0) * ease.outCubic(k), 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    for (const f of this.floats) {
      const sx = f.x - camX;
      const k = f.life / f.max;
      const pop = k < 0.2 ? ease.outBack(k / 0.2) : 1;
      ctx.globalAlpha = Math.max(0, 1 - Math.max(0, k - 0.55) / 0.45);
      ctx.save();
      ctx.translate(sx, f.y);
      ctx.scale(pop * f.scale, pop * f.scale);
      ctx.font = 'bold ' + f.size + 'px "Segoe UI", "Microsoft YaHei", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.lineWidth = 6;
      ctx.strokeStyle = 'rgba(32,44,64,0.85)';
      ctx.strokeText(f.text, 0, 0);
      ctx.fillStyle = f.color;
      ctx.fillText(f.text, 0, 0);
      ctx.restore();
    }
    ctx.globalAlpha = 1;
  },

  // 屏幕层特效，不跟镜头
  drawScreen(ctx) {
    for (const p of this.sparts) {
      const k = p.life / p.max;
      ctx.globalAlpha = Math.max(0, 1 - k);
      ctx.fillStyle = p.color;
      const s = p.size * (1 - k * 0.5);
      ctx.fillRect(p.x - s / 2, p.y - s / 2, s, s);
    }
    ctx.globalAlpha = 1;

    for (const r of this.ripples) {
      const k = r.life / r.max;
      ctx.globalAlpha = Math.max(0, 1 - k);
      ctx.strokeStyle = r.color;
      ctx.lineWidth = r.width * (1 - k * 0.6);
      ctx.beginPath();
      ctx.arc(r.x, r.y, r.r0 + (r.r1 - r.r0) * ease.outCubic(k), 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    for (const f of this.sfloats) {
      const k = f.life / f.max;
      const pop = k < 0.18 ? ease.outBack(k / 0.18) : 1;
      ctx.globalAlpha = Math.max(0, 1 - Math.max(0, k - 0.6) / 0.4);
      ctx.save();
      ctx.translate(f.x, f.y);
      ctx.scale(pop, pop);
      ctx.font = 'bold ' + f.size + 'px "Segoe UI", "Microsoft YaHei", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.lineWidth = 8;
      ctx.strokeStyle = 'rgba(32,44,64,0.9)';
      ctx.strokeText(f.text, 0, 0);
      ctx.fillStyle = f.color;
      ctx.fillText(f.text, 0, 0);
      ctx.restore();
    }
    ctx.globalAlpha = 1;

    if (this.flashA > 0.002) {
      ctx.globalAlpha = Math.min(1, this.flashA);
      ctx.fillStyle = this.flashColor;
      ctx.fillRect(0, 0, W, H);
      ctx.globalAlpha = 1;
    }
  },
};
