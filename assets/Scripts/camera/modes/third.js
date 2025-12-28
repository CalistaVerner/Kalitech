// FILE: Scripts/camera/modes/third.js
"use strict";

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function num(v, fallback) {
    const n = +v;
    return Number.isFinite(n) ? n : (fallback || 0);
}
function vx(v, fb) {
    if (!v) return fb || 0;
    try { const x = v.x; if (typeof x === "function") return num(x.call(v), fb); if (typeof x === "number") return x; } catch (_) {}
    return fb || 0;
}
function vy(v, fb) {
    if (!v) return fb || 0;
    try { const y = v.y; if (typeof y === "function") return num(y.call(v), fb); if (typeof y === "number") return y; } catch (_) {}
    return fb || 0;
}
function vz(v, fb) {
    if (!v) return fb || 0;
    try { const z = v.z; if (typeof z === "function") return num(z.call(v), fb); if (typeof z === "number") return z; } catch (_) {}
    return fb || 0;
}

class ThirdPersonCameraMode {
    constructor() {
        this.distance = 3.4;
        this.minDistance = 0.8;
        this.maxDistance = 10.0;

        this.height = 1.55;
        this.side = 0.25;

        this.zoomSpeed = 1.0;

        this.follow = { smoothing: 10.0, _x: 0, _y: 0, _z: 0, _inited: false };

        this.dynamic = { runDistAdd: 0.7, strafeShoulder: 0.18 };

        this.debug = { enabled: false, everyFrames: 60, _f: 0 };
    }

    configure(cfg) {
        if (!cfg) return;
        if (typeof cfg.distance === "number") this.distance = cfg.distance;
        if (typeof cfg.minDistance === "number") this.minDistance = cfg.minDistance;
        if (typeof cfg.maxDistance === "number") this.maxDistance = cfg.maxDistance;
        if (typeof cfg.height === "number") this.height = cfg.height;
        if (typeof cfg.side === "number") this.side = cfg.side;
        if (typeof cfg.zoomSpeed === "number") this.zoomSpeed = cfg.zoomSpeed;

        if (cfg.follow) {
            if (typeof cfg.follow.smoothing === "number") this.follow.smoothing = Math.max(0.1, cfg.follow.smoothing);
        }
        if (cfg.dynamic) {
            if (typeof cfg.dynamic.runDistAdd === "number") this.dynamic.runDistAdd = cfg.dynamic.runDistAdd;
            if (typeof cfg.dynamic.strafeShoulder === "number") this.dynamic.strafeShoulder = cfg.dynamic.strafeShoulder;
        }

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }
    }

    onEnter() { this.follow._inited = false; }
    onExit() {}

    update(ctx) {
        if (!ctx.bodyPos) return;

        if (ctx.input && ctx.input.wheel) {
            this.distance = clamp(this.distance - ctx.input.wheel * this.zoomSpeed, this.minDistance, this.maxDistance);
        }

        const yaw = ctx.look ? (ctx.look._yawS || 0) : 0;

        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const dt = (ctx.input && ctx.input.dt) ? ctx.input.dt : 0.016;

        const m = ctx.motion || null;
        const sp = m ? (m.speed || 0) : 0;
        const run = m ? !!m.running : false;
        const k = clamp(sp / 8.0, 0, 1);

        const distAdd = run ? (this.dynamic.runDistAdd * k) : 0;
        const dist = clamp(this.distance + distAdd, this.minDistance, this.maxDistance);

        const bx = -sin * dist;
        const bz = -cos * dist;

        const strafe = ctx.input ? (ctx.input.mx || 0) : 0;
        const shoulder = this.side + (strafe * this.dynamic.strafeShoulder);

        const rx =  cos * shoulder;
        const rz = -sin * shoulder;

        const tx = vx(ctx.bodyPos, 0) + bx + rx;
        const ty = vy(ctx.bodyPos, 0) + this.height;
        const tz = vz(ctx.bodyPos, 0) + bz + rz;

        const f = this.follow;
        if (!f._inited) {
            f._x = tx; f._y = ty; f._z = tz;
            f._inited = true;
        }
        const a = 1 - Math.exp(-f.smoothing * dt);

        f._x += (tx - f._x) * a;
        f._y += (ty - f._y) * a;
        f._z += (tz - f._z) * a;

        ctx.cam.setLocation(f._x, f._y, f._z);

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    LOG.info(
                        "cam.third bodyId=" + (ctx.bodyId | 0) +
                        " dist=" + dist.toFixed(2) +
                        " speed=" + sp.toFixed(2) +
                        " run=" + (run ? 1 : 0) +
                        " cam=(" + f._x.toFixed(2) + "," + f._y.toFixed(2) + "," + f._z.toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = ThirdPersonCameraMode;