// FILE: Scripts/camera/modes/first.js
"use strict";

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

function num(v, fallback) {
    const n = +v;
    return Number.isFinite(n) ? n : (fallback || 0);
}

function vx(v, fb) {
    if (!v) return fb || 0;
    try {
        const x = v.x;
        if (typeof x === "function") return num(x.call(v), fb);
        if (typeof x === "number") return x;
    } catch (_) {}
    return fb || 0;
}
function vy(v, fb) {
    if (!v) return fb || 0;
    try {
        const y = v.y;
        if (typeof y === "function") return num(y.call(v), fb);
        if (typeof y === "number") return y;
    } catch (_) {}
    return fb || 0;
}
function vz(v, fb) {
    if (!v) return fb || 0;
    try {
        const z = v.z;
        if (typeof z === "function") return num(z.call(v), fb);
        if (typeof z === "number") return z;
    } catch (_) {}
    return fb || 0;
}

class FirstPersonCameraMode {
    constructor() {
        this.offset = { x: 0.0, y: 1.65, z: 0.0 };

        this.style = {
            shoulder: 0.035,
            forward: 0.015,
            pitchForwardMul: 0.010,
            strafeLeanMul: 1.0
        };

        this.debug = { enabled: false, everyFrames: 60, _f: 0 };
    }

    configure(cfg) {
        if (!cfg) return;
        if (cfg.offset) {
            if (typeof cfg.offset.x === "number") this.offset.x = cfg.offset.x;
            if (typeof cfg.offset.y === "number") this.offset.y = cfg.offset.y;
            if (typeof cfg.offset.z === "number") this.offset.z = cfg.offset.z;
        }
        if (cfg.style) {
            const s = cfg.style;
            if (typeof s.shoulder === "number") this.style.shoulder = s.shoulder;
            if (typeof s.forward === "number") this.style.forward = s.forward;
            if (typeof s.pitchForwardMul === "number") this.style.pitchForwardMul = s.pitchForwardMul;
            if (typeof s.strafeLeanMul === "number") this.style.strafeLeanMul = s.strafeLeanMul;
        }
        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }
    }

    onEnter() {}
    onExit() {}

    update(ctx) {
        if (!ctx.bodyPos) return;

        const baseX = vx(ctx.bodyPos, 0) + this.offset.x;
        const baseY = vy(ctx.bodyPos, 0) + this.offset.y;
        const baseZ = vz(ctx.bodyPos, 0) + this.offset.z;

        const yaw = ctx.look ? (ctx.look._yawS || 0) : 0;
        const pitch = ctx.look ? (ctx.look._pitchS || 0) : 0;

        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const strafe = ctx.input ? (ctx.input.mx || 0) : 0;

        const shoulder = this.style.shoulder * clamp(strafe, -1, 1) * this.style.strafeLeanMul;
        const forward = this.style.forward + Math.abs(pitch) * this.style.pitchForwardMul;

        const bx = (cos * shoulder) + (-sin * forward);
        const bz = (-sin * shoulder) + (-cos * forward);

        const x = baseX + bx;
        const y = baseY;
        const z = baseZ + bz;

        ctx.cam.setLocation(x, y, z);

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    LOG.info(
                        "cam.first bodyId=" + (ctx.bodyId | 0) +
                        " bodyPos=(" + vx(ctx.bodyPos, 0).toFixed(2) + "," + vy(ctx.bodyPos, 0).toFixed(2) + "," + vz(ctx.bodyPos, 0).toFixed(2) + ")" +
                        " cam=(" + x.toFixed(2) + "," + y.toFixed(2) + "," + z.toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = FirstPersonCameraMode;
