// FILE: Scripts/camera/modes/top.js
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

class TopDownCameraMode {
    constructor() {
        this.height = 18.0;
        this.minHeight = 4.0;
        this.maxHeight = 120.0;

        this.panSpeed = 14.0;
        this.zoomSpeed = 2.0;

        this.pitch = -Math.PI * 0.48;

        this.debug = { enabled: false, everyFrames: 90, _f: 0 };
    }

    configure(cfg) {
        if (!cfg) return;
        if (typeof cfg.height === "number") this.height = cfg.height;
        if (typeof cfg.minHeight === "number") this.minHeight = cfg.minHeight;
        if (typeof cfg.maxHeight === "number") this.maxHeight = cfg.maxHeight;
        if (typeof cfg.panSpeed === "number") this.panSpeed = cfg.panSpeed;
        if (typeof cfg.zoomSpeed === "number") this.zoomSpeed = cfg.zoomSpeed;
        if (typeof cfg.pitch === "number") this.pitch = cfg.pitch;

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }
    }

    onEnter() {}
    onExit() {}

    update(ctx) {
        // zoom height by wheel
        if (ctx.input && ctx.input.wheel) {
            this.height = clamp(this.height - ctx.input.wheel * this.zoomSpeed, this.minHeight, this.maxHeight);
        }

        if (ctx.bodyPos) {
            const x = vx(ctx.bodyPos, 0);
            const z = vz(ctx.bodyPos, 0);
            ctx.cam.setLocation(x, this.height, z);
        } else {
            // no attachment -> keep XZ from current cam location, enforce height
            const p = ctx.cam.location();
            const x = vx(p, 0);
            const z = vz(p, 0);
            ctx.cam.setLocation(x, this.height, z);
        }

        // lock pitch
        try {
            ctx.cam.setYawPitch(ctx.look ? (ctx.look._yawS || 0) : 0, this.pitch);
        } catch (_) {}

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    const p = ctx.cam.location();
                    engine.log().info("[cam:top] bodyId=" + (ctx.bodyId | 0) +
                        " height=" + this.height.toFixed(2) +
                        " cam=(" + vx(p, 0).toFixed(2) + "," + vy(p, 0).toFixed(2) + "," + vz(p, 0).toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = TopDownCameraMode;