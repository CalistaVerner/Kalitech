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

function expSmooth(cur, target, smooth, dt) {
    const s = Math.max(0, num(smooth, 0));
    if (s <= 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

class FirstPersonCameraMode {
    constructor() {
        // LEGACY offset: still supported, but main anchor is ctx.target (head pivot) from orchestrator
        this.offset = { x: 0.0, y: 1.65, z: 0.0 };

        this.style = {
            shoulder: 0.035,
            forward: 0.015,
            pitchForwardMul: 0.010,
            strafeLeanMul: 1.0
        };

        // NEW: hard head lock stability
        this.follow = {
            smooth: 48.0,        // bigger = tighter to head (AAA “locked” feel)
            maxPullPerSec: 90.0, // cap to avoid snaps on teleports/physics pops
            _inited: false,
            _x: 0, _y: 0, _z: 0
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

        // NEW: follow tuning
        if (cfg.follow) {
            const f = cfg.follow;
            if (typeof f.smooth === "number") this.follow.smooth = Math.max(0, f.smooth);
            if (typeof f.maxPullPerSec === "number") this.follow.maxPullPerSec = Math.max(1, f.maxPullPerSec);
        } else {
            // optional short names
            if (typeof cfg.followSmooth === "number") this.follow.smooth = Math.max(0, cfg.followSmooth);
            if (typeof cfg.maxPullPerSec === "number") this.follow.maxPullPerSec = Math.max(1, cfg.maxPullPerSec);
        }

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }
    }

    onEnter() {
        this.follow._inited = false;
    }

    onExit() {}

    update(ctx) {
        // We prefer orchestrator-provided head target (bodyPos + target.headOffset).
        // Fallback to old bodyPos + this.offset for compatibility.
        if (!ctx) return;

        const dt = (ctx.input && ctx.input.dt) ? (+ctx.input.dt || 0.016) : 0.016;

        let ax = 0, ay = 0, az = 0;

        if (ctx.target) {
            ax = num(ctx.target.x, 0);
            ay = num(ctx.target.y, 0);
            az = num(ctx.target.z, 0);
        } else if (ctx.bodyPos) {
            ax = vx(ctx.bodyPos, 0) + this.offset.x;
            ay = vy(ctx.bodyPos, 0) + this.offset.y;
            az = vz(ctx.bodyPos, 0) + this.offset.z;
        } else {
            return;
        }

        const yaw = ctx.look ? (ctx.look._yawS || 0) : 0;
        const pitch = ctx.look ? (ctx.look._pitchS || 0) : 0;

        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const strafe = ctx.input ? (ctx.input.mx || 0) : 0;

        // “Cyberpunk-ish” micro shoulder + tiny forward bias with pitch
        const shoulder = this.style.shoulder * clamp(strafe, -1, 1) * this.style.strafeLeanMul;
        const forward = this.style.forward + Math.abs(pitch) * this.style.pitchForwardMul;

        // local -> world (yaw only)
        const ox = (cos * shoulder) + (-sin * forward);
        const oz = (-sin * shoulder) + (-cos * forward);

        const tx = ax + ox;
        const ty = ay;
        const tz = az + oz;

        // --- HARD LOCK FOLLOW (smooth + max speed clamp)
        const f = this.follow;

        if (!f._inited) {
            f._x = tx; f._y = ty; f._z = tz;
            f._inited = true;
        }

        const nx = expSmooth(f._x, tx, f.smooth, dt);
        const ny = expSmooth(f._y, ty, f.smooth, dt);
        const nz = expSmooth(f._z, tz, f.smooth, dt);

        // clamp step to prevent rare “physics pop” fly
        const dx = nx - f._x, dy = ny - f._y, dz = nz - f._z;
        const step = Math.hypot(dx, dy, dz) || 0;
        const maxStep = Math.max(0.02, f.maxPullPerSec * dt);

        if (step > maxStep) {
            const inv = 1 / Math.max(1e-6, step);
            f._x += dx * inv * maxStep;
            f._y += dy * inv * maxStep;
            f._z += dz * inv * maxStep;
        } else {
            f._x = nx; f._y = ny; f._z = nz;
        }

        ctx.cam.setLocation(f._x, f._y, f._z);

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    LOG.info(
                        "cam.first LOCK bodyId=" + (ctx.bodyId | 0) +
                        " head=(" + ax.toFixed(2) + "," + ay.toFixed(2) + "," + az.toFixed(2) + ")" +
                        " cam=(" + f._x.toFixed(2) + "," + f._y.toFixed(2) + "," + f._z.toFixed(2) + ")" +
                        " dt=" + dt.toFixed(4)
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = FirstPersonCameraMode;