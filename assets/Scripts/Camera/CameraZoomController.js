// FILE: Scripts/Camera/CameraZoomController.js
"use strict";

/**
 * CameraZoomController
 * - Progressive zoom steps (e.g. 2,4,8,16,32)
 * - Smooth interpolation current -> target
 * - Input: INP.consumeWheelDelta() (global) + optional ctx.input.zoomIn/zoomOut
 *
 * Usage:
 *   const zoom = new CameraZoomController({ steps:[2,4,8,16,32], index:2 });
 *   zoom.update(dt, { input:{ zoomIn:false, zoomOut:false } });
 *   const dist = zoom.value();
 */

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

function expSmooth(cur, target, smooth, dt) {
    const s = Math.max(0, num(smooth, 0));
    if (s <= 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

class CameraZoomController {
    constructor(cfg) {
        cfg = cfg || {};

        this.steps = Array.isArray(cfg.steps) && cfg.steps.length ? cfg.steps.slice() : [2, 4, 8, 16, 32];

        this.minIndex = (typeof cfg.minIndex === "number") ? (cfg.minIndex | 0) : 0;
        this.maxIndex = (typeof cfg.maxIndex === "number") ? (cfg.maxIndex | 0) : (this.steps.length - 1);

        this.minIndex = clamp(this.minIndex, 0, this.steps.length - 1) | 0;
        this.maxIndex = clamp(this.maxIndex, this.minIndex, this.steps.length - 1) | 0;

        this.index = (typeof cfg.index === "number") ? (cfg.index | 0) : clamp(((this.steps.length / 2) | 0), this.minIndex, this.maxIndex);
        this.index = clamp(this.index, this.minIndex, this.maxIndex) | 0;

        this.target = num(cfg.target, num(this.steps[this.index], 8));
        this.current = num(cfg.current, this.target);

        this.smooth = num(cfg.smooth, 18.0);
        this.cooldown = num(cfg.cooldown, 0.08);
        this._cd = 0;

        // Wheel direction: true -> invert
        this.invertWheel = !!cfg.invertWheel;

        // Optional hard clamp (in case you put weird steps)
        this.min = num(cfg.min, 1.2);
        this.max = num(cfg.max, 120.0);

        // How many steps to move per tick (normally 1)
        this.stepStride = Math.max(1, (cfg.stepStride | 0) || 1);
    }

    configure(cfg) {
        if (!cfg) return this;

        if (Array.isArray(cfg.steps) && cfg.steps.length) {
            this.steps = cfg.steps.slice();
            this.minIndex = clamp(this.minIndex, 0, this.steps.length - 1) | 0;
            this.maxIndex = clamp(this.maxIndex, this.minIndex, this.steps.length - 1) | 0;
            this.index = clamp(this.index, this.minIndex, this.maxIndex) | 0;
            this.target = clamp(this.target, this.min, this.max);
            this.current = clamp(this.current, this.min, this.max);
        }

        if (typeof cfg.minIndex === "number") this.minIndex = clamp(cfg.minIndex | 0, 0, this.steps.length - 1) | 0;
        if (typeof cfg.maxIndex === "number") this.maxIndex = clamp(cfg.maxIndex | 0, this.minIndex, this.steps.length - 1) | 0;

        if (typeof cfg.index === "number") this.index = clamp(cfg.index | 0, this.minIndex, this.maxIndex) | 0;
        if (typeof cfg.smooth === "number") this.smooth = num(cfg.smooth, this.smooth);
        if (typeof cfg.cooldown === "number") this.cooldown = Math.max(0, num(cfg.cooldown, this.cooldown));
        if (typeof cfg.invertWheel === "boolean") this.invertWheel = cfg.invertWheel;

        if (typeof cfg.min === "number") this.min = num(cfg.min, this.min);
        if (typeof cfg.max === "number") this.max = Math.max(this.min, num(cfg.max, this.max));

        if (typeof cfg.stepStride === "number") this.stepStride = Math.max(1, cfg.stepStride | 0);

        // resync target to current index
        this.target = clamp(num(this.steps[this.index], this.target), this.min, this.max);
        this.current = clamp(this.current, this.min, this.max);

        return this;
    }

    reset(value) {
        if (typeof value === "number" && Number.isFinite(value)) {
            this.current = clamp(value, this.min, this.max);
            this.target = this.current;
        }
        this._cd = 0;
        return this;
    }

    value() { return this.current; }
    targetValue() { return this.target; }
    stepIndex() { return this.index; }

    setIndex(idx, snap) {
        const i = clamp((idx | 0), this.minIndex, this.maxIndex) | 0;
        this.index = i;
        this.target = clamp(num(this.steps[this.index], this.target), this.min, this.max);
        if (snap) this.current = this.target;
        return this;
    }

    _consumeWheel() {
        try {
            if (typeof INP !== "undefined" && INP && typeof INP.consumeWheelDelta === "function") {
                return num(INP.consumeWheelDelta(), 0);
            }
        } catch (_) {}
        return 0;
    }

    /**
     * update(dt, ctx)
     * ctx optional: { input:{zoomIn:boolean, zoomOut:boolean} }
     */
    update(dt, ctx) {
        dt = clamp(num(dt, 1 / 60), 0, 0.05);

        this._cd = Math.max(0, this._cd - dt);

        // read intent
        let want = 0;

        // wheel
        let w = this._consumeWheel();
        if (this.invertWheel) w = -w;
        if (w !== 0) want += (w > 0) ? 1 : -1;

        // optional key zoom
        const inp = ctx && ctx.input;
        if (inp) {
            if (inp.zoomIn) want += 1;
            if (inp.zoomOut) want -= 1;
        }

        if (want !== 0 && this._cd <= 0) {
            // convention: want>0 means "zoom in" => smaller distance => index--
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