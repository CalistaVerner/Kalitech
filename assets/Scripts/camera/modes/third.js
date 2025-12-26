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

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }
    }

    onEnter() {}
    onExit() {}

    update(ctx) {
        if (!ctx.bodyPos) return;

        // zoom
        if (ctx.input && ctx.input.wheel) {
            this.distance = clamp(this.distance - ctx.input.wheel * this.zoomSpeed, this.minDistance, this.maxDistance);
        }

        const yaw = ctx.look ? (ctx.look._yawS || 0) : 0;

        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const bx = -sin * this.distance;
        const bz = -cos * this.distance;

        const rx =  cos * this.side;
        const rz = -sin * this.side;

        const x = vx(ctx.bodyPos, 0) + bx + rx;
        const y = vy(ctx.bodyPos, 0) + this.height;
        const z = vz(ctx.bodyPos, 0) + bz + rz;

        ctx.cam.setLocation(x, y, z);

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    engine.log().info("[cam:third] bodyId=" + (ctx.bodyId | 0) +
                        " dist=" + this.distance.toFixed(2) +
                        " bodyPos=(" + vx(ctx.bodyPos, 0).toFixed(2) + "," + vy(ctx.bodyPos, 0).toFixed(2) + "," + vz(ctx.bodyPos, 0).toFixed(2) + ")" +
                        " cam=(" + x.toFixed(2) + "," + y.toFixed(2) + "," + z.toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = ThirdPersonCameraMode;