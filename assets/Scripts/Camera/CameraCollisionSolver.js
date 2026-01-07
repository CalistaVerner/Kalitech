"use strict";

function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : fb;
}

function expSmooth(cur, target, smooth, dt) {
    const s = smooth > 0 ? smooth : 0;
    if (s === 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

class CameraZoomController {
    constructor(cfg) {
        cfg = cfg || Object.create(null);

        const steps = Array.isArray(cfg.steps) && cfg.steps.length ? cfg.steps : [2, 4, 8, 16, 32];
        this.steps = steps.slice();

        this.minIndex = clamp((cfg.minIndex | 0) || 0, 0, this.steps.length - 1) | 0;
        this.maxIndex = (cfg.maxIndex !== undefined) ? (cfg.maxIndex | 0) : (this.steps.length - 1);
        this.maxIndex = clamp(this.maxIndex, this.minIndex, this.steps.length - 1) | 0;

        const mid = (this.steps.length / 2) | 0;
        this.index = (cfg.index !== undefined) ? (cfg.index | 0) : mid;
        this.index = clamp(this.index, this.minIndex, this.maxIndex) | 0;

        this.min = num(cfg.min, 1.2);
        this.max = Math.max(this.min, num(cfg.max, 120.0));

        this.target = clamp(num(cfg.target, num(this.steps[this.index], 8)), this.min, this.max);
        this.current = clamp(num(cfg.current, this.target), this.min, this.max);

        this.smooth = num(cfg.smooth, 18.0);
        this.cooldown = Math.max(0, num(cfg.cooldown, 0.08));
        this._cd = 0;

        this.invertWheel = !!cfg.invertWheel;
        this.stepStride = Math.max(1, (cfg.stepStride | 0) || 1);
    }

    configure(cfg) {
        if (!cfg) return this;

        if (Array.isArray(cfg.steps) && cfg.steps.length) {
            this.steps = cfg.steps.slice();
            this.minIndex = clamp(this.minIndex, 0, this.steps.length - 1) | 0;
            this.maxIndex = clamp(this.maxIndex, this.minIndex, this.steps.length - 1) | 0;
            this.index = clamp(this.index, this.minIndex, this.maxIndex) | 0;
        }

        if (cfg.minIndex !== undefined) this.minIndex = clamp(cfg.minIndex | 0, 0, this.steps.length - 1) | 0;
        if (cfg.maxIndex !== undefined) this.maxIndex = clamp(cfg.maxIndex | 0, this.minIndex, this.steps.length - 1) | 0;
        if (cfg.index !== undefined) this.index = clamp(cfg.index | 0, this.minIndex, this.maxIndex) | 0;

        if (cfg.smooth !== undefined) this.smooth = num(cfg.smooth, this.smooth);
        if (cfg.cooldown !== undefined) this.cooldown = Math.max(0, num(cfg.cooldown, this.cooldown));
        if (cfg.invertWheel !== undefined) this.invertWheel = !!cfg.invertWheel;

        if (cfg.min !== undefined) this.min = num(cfg.min, this.min);
        if (cfg.max !== undefined) this.max = Math.max(this.min, num(cfg.max, this.max));

        if (cfg.stepStride !== undefined) this.stepStride = Math.max(1, cfg.stepStride | 0);

        this.target = clamp(num(this.steps[this.index], this.target), this.min, this.max);
        this.current = clamp(this.current, this.min, this.max);

        return this;
    }

    reset(value) {
        if (value !== undefined) {
            const v = +value;
            if (Number.isFinite(v)) {
                this.current = clamp(v, this.min, this.max);
                this.target = this.current;
            }
        }
        this._cd = 0;
        return this;
    }

    value() {
        return this.current;
    }

    targetValue() {
        return this.target;
    }

    stepIndex() {
        return this.index;
    }

    setIndex(idx, snap) {
        this.index = clamp(idx | 0, this.minIndex, this.maxIndex) | 0;
        this.target = clamp(num(this.steps[this.index], this.target), this.min, this.max);
        if (snap) this.current = this.target;
        return this;
    }

    update(dt, ctx) {
        dt = clamp(num(dt, 1 / 60), 0, 0.05);
        this._cd = Math.max(0, this._cd - dt);

        let want = 0;

        const inp = ctx && ctx.input;
        if (inp) {
            const w = num(inp.wheel, 0);
            if (w !== 0) want += (this.invertWheel ? -w : w) > 0 ? 1 : -1;

            if (inp.zoomIn) want += 1;
            if (inp.zoomOut) want -= 1;
        }

        if (want !== 0 && this._cd === 0) {
            const dir = (want > 0) ? -1 : 1;
            this.index = clamp(this.index + dir * this.stepStride, this.minIndex, this.maxIndex) | 0;
            this.target = clamp(num(this.steps[this.index], this.target), this.min, this.max);
            this._cd = this.cooldown;
        }

        this.current = expSmooth(this.current, this.target, this.smooth, dt);
        return this;
    }
}

module.exports = CameraZoomController;
