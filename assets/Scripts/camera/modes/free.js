// FILE: Scripts/camera/modes/free.js
"use strict";

class FreeCameraMode {
    constructor() {
        this.speed = 90;
        this.accel = 18;
        this.drag = 6.5;

        this._vx = 0; this._vy = 0; this._vz = 0;
    }

    configure(cfg) {
        if (!cfg) return;
        if (typeof cfg.speed === "number") this.speed = cfg.speed;
        if (typeof cfg.accel === "number") this.accel = cfg.accel;
        if (typeof cfg.drag === "number") this.drag = cfg.drag;
    }

    onEnter() {}
    onExit() {}

    update(ctx) {
        const cam = ctx.cam;
        const dt = ctx.input.dt;

        const mx = ctx.input.mx;
        const my = ctx.input.my;
        const mz = ctx.input.mz;

        let dx = mx, dy = my, dz = mz;
        const len = Math.hypot(dx, dy, dz) || 0;
        if (len > 0) { dx /= len; dy /= len; dz /= len; }

        const tvx = dx * this.speed;
        const tvy = dy * this.speed;
        const tvz = dz * this.speed;

        const a = 1 - Math.exp(-this.accel * dt);
        this._vx += (tvx - this._vx) * a;
        this._vy += (tvy - this._vy) * a;
        this._vz += (tvz - this._vz) * a;

        const d = Math.exp(-this.drag * dt);
        this._vx *= d; this._vy *= d; this._vz *= d;

        cam.moveLocal(this._vx * dt, this._vy * dt, this._vz * dt);
    }
}

module.exports = FreeCameraMode;