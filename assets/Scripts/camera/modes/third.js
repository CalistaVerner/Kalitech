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
function expSmooth(cur, target, smooth, dt) {
    const s = Math.max(0, num(smooth, 0));
    if (s <= 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

class ThirdPersonCameraMode {
    constructor() {
        this.distance = 3.4;
        this.minDistance = 0.8;
        this.maxDistance = 10.0;

        this.height = 1.55;
        this.side = 0.25;

        this.zoomSpeed = 1.0;

        // Smoothing / follow
        this.follow = { smoothing: 10.0, _x: 0, _y: 0, _z: 0, _inited: false };

        // Dynamics based on motion/input
        this.dynamic = {
            runDistAdd: 0.7,
            strafeShoulder: 0.18
        };

        // AAA additions
        this.zoom = {
            smooth: 18.0,     // how fast current distance follows target
            _dist: 3.4,       // current used dist
            _distT: 3.4       // target dist from wheel and modes
        };

        this.shoulder = {
            enabled: true,
            key: "V",
            smooth: 16.0,
            side: 1,           // +1 right shoulder, -1 left shoulder
            _sideS: 1
        };

        this.aimAssist = {
            enabled: true,
            // when aiming, we usually go closer, more centered
            distance: 2.2,
            side: 0.18,
            height: 1.6,
            smooth: 18.0
        };

        this.debug = { enabled: false, everyFrames: 60, _f: 0 };
        this._kc = Object.create(null);
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

        // AAA config
        if (typeof cfg.zoomSmooth === "number") this.zoom.smooth = Math.max(0.1, cfg.zoomSmooth);
        if (cfg.zoom) {
            if (typeof cfg.zoom.smooth === "number") this.zoom.smooth = Math.max(0.1, cfg.zoom.smooth);
        }

        if (typeof cfg.shoulderSwap === "boolean") this.shoulder.enabled = cfg.shoulderSwap;
        if (typeof cfg.shoulderSwapKey === "string") this.shoulder.key = cfg.shoulderSwapKey;
        if (typeof cfg.shoulderSide === "number") this.shoulder.side = (cfg.shoulderSide < 0) ? -1 : 1;
        if (cfg.shoulder) {
            if (typeof cfg.shoulder.enabled === "boolean") this.shoulder.enabled = cfg.shoulder.enabled;
            if (typeof cfg.shoulder.key === "string") this.shoulder.key = cfg.shoulder.key;
            if (typeof cfg.shoulder.smooth === "number") this.shoulder.smooth = Math.max(0.1, cfg.shoulder.smooth);
            if (typeof cfg.shoulder.side === "number") this.shoulder.side = (cfg.shoulder.side < 0) ? -1 : 1;
        }

        if (cfg.aimAssist) {
            if (typeof cfg.aimAssist.enabled === "boolean") this.aimAssist.enabled = cfg.aimAssist.enabled;
            if (typeof cfg.aimAssist.distance === "number") this.aimAssist.distance = cfg.aimAssist.distance;
            if (typeof cfg.aimAssist.side === "number") this.aimAssist.side = cfg.aimAssist.side;
            if (typeof cfg.aimAssist.height === "number") this.aimAssist.height = cfg.aimAssist.height;
            if (typeof cfg.aimAssist.smooth === "number") this.aimAssist.smooth = Math.max(0.1, cfg.aimAssist.smooth);
        }

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }

        // sanitize
        this.minDistance = Math.max(0.05, this.minDistance);
        this.maxDistance = Math.max(this.minDistance + 0.05, this.maxDistance);

        // reset cache for keys
        this._kc = Object.create(null);
    }

    onEnter() {
        this.follow._inited = false;
        // reset zoom state to config distance
        this.zoom._dist = clamp(this.distance, this.minDistance, this.maxDistance);
        this.zoom._distT = this.zoom._dist;

        // reset shoulder smoothing
        this.shoulder._sideS = (this.shoulder.side < 0) ? -1 : 1;
    }

    onExit() {}

    _keyCode(name) {
        const k = String(name || "").trim().toUpperCase();
        if (!k) return -1;
        if (this._kc[k] !== undefined) return this._kc[k] | 0;
        let code = -1;
        try {
            // best-effort: engine.input().keyCode("V") -> int
            code =INP.keyCode ? (INP.keyCode(k) | 0) : -1;
        } catch (_) { code = -1; }
        this._kc[k] = code | 0;
        return code | 0;
    }

    _justPressed(ctx, keyName) {
        const snap = ctx && ctx.inputSnap;
        // If orchestrator doesn't pass snapshot, we can't detect justPressed reliably.
        // BUT: in your orchestrator snapshot exists at update(dt, snap). If you want shoulder swap,
        // pass snap into ctx as inputSnap. If you don't, this will just be disabled.
        if (!snap || !snap.justPressed) return false;
        const code = this._keyCode(keyName);
        if (code < 0) return false;
        const jp = snap.justPressed;
        for (let i = 0, n = jp.length | 0; i < n; i++) if ((jp[i] | 0) === code) return true;
        return false;
    }

    _isAiming(ctx) {
        // best-effort aim detection:
        // - ctx.input.aim (boolean) if you set it
        // - ctx.input.rmb (boolean) if you set it
        // - ctx.input.mouseMask (int) with RMB bit if you pass it
        const inp = ctx && ctx.input;
        if (!inp) return false;
        if (typeof inp.aim === "boolean") return inp.aim;
        if (typeof inp.rmb === "boolean") return inp.rmb;

        const mm = inp.mouseMask;
        if (typeof mm === "number") {
            // typical: RMB is button index 1 -> (1<<1)=2
            return ((mm & (1 << 1)) !== 0);
        }
        return false;
    }

    update(ctx) {
        if (!ctx || !ctx.bodyPos) return;

        const dt = (ctx.input && ctx.input.dt) ? ctx.input.dt : 0.016;

        // --- Shoulder swap (requires ctx.inputSnap to be passed if you want it)
        if (this.shoulder.enabled) {
            if (this._justPressed(ctx, this.shoulder.key)) {
                this.shoulder.side = (this.shoulder.side > 0) ? -1 : 1;
            }
            const targetSide = (this.shoulder.side > 0) ? 1 : -1;
            this.shoulder._sideS = expSmooth(this.shoulder._sideS, targetSide, this.shoulder.smooth, dt);
        } else {
            this.shoulder._sideS = 1;
        }

        // --- Zoom: wheel modifies target distance, actual distance is smoothed
        if (ctx.input && ctx.input.wheel) {
            const curT = this.zoom._distT;
            const nextT = clamp(curT - ctx.input.wheel * this.zoomSpeed, this.minDistance, this.maxDistance);
            this.zoom._distT = nextT;
        }

        // base target distance: "distance" can be treated as initial baseline
        // if someone reconfigures this.distance at runtime, we don't want to fight zoom
        // so we keep zoom._distT as authority, but clamp it anyway:
        this.zoom._distT = clamp(this.zoom._distT, this.minDistance, this.maxDistance);
        this.zoom._dist = expSmooth(this.zoom._dist, this.zoom._distT, this.zoom.smooth, dt);

        const yaw = ctx.look ? (ctx.look._yawS || 0) : 0;
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const m = ctx.motion || null;
        const sp = m ? (m.speed || 0) : 0;
        const run = m ? !!m.running : false;
        const k = clamp(sp / 8.0, 0, 1);

        const distAdd = run ? (this.dynamic.runDistAdd * k) : 0;

        // Aiming blend (optional)
        const aiming = this.aimAssist.enabled && this._isAiming(ctx);
        const aimA = aiming ? 1 : 0;

        // Distance target: base zoom dist + run add, then blend toward aim distance
        const baseDist = clamp(this.zoom._dist + distAdd, this.minDistance, this.maxDistance);
        const aimDist = clamp(this.aimAssist.distance, this.minDistance, this.maxDistance);
        const distT = baseDist + (aimDist - baseDist) * aimA;

        // --- Camera back offset
        const bx = -sin * distT;
        const bz = -cos * distT;

        // --- Side (shoulder) + strafe shoulder
        const strafe = ctx.input ? (ctx.input.mx || 0) : 0;

        // base side is configured "this.side", aim side is smaller
        const baseSideAbs = Math.abs(this.side);
        const aimSideAbs = Math.abs(this.aimAssist.side);

        const sideAbs = baseSideAbs + (aimSideAbs - baseSideAbs) * aimA;

        // shoulder side sign
        const sideSign = (this.shoulder._sideS >= 0) ? 1 : -1;

        // strafe shoulder should go OUTWARD relative to current shoulder
        // so multiply by sideSign
        const shoulder = (sideAbs * sideSign) + (strafe * this.dynamic.strafeShoulder * sideSign);

        const rx =  cos * shoulder;
        const rz = -sin * shoulder;

        // --- Height: blend to aim height if aiming
        const baseH = this.height;
        const aimH = this.aimAssist.height;
        const hT = baseH + (aimH - baseH) * aimA;

        const tx = vx(ctx.bodyPos, 0) + bx + rx;
        const ty = vy(ctx.bodyPos, 0) + hT;
        const tz = vz(ctx.bodyPos, 0) + bz + rz;

        // Follow smoothing (world-space)
        const f = this.follow;
        if (!f._inited) {
            f._x = tx; f._y = ty; f._z = tz;
            f._inited = true;
        }

        // follow smoothing speed can be slightly higher while aiming for snappier feel
        const followSmooth = aiming ? Math.max(f.smoothing, this.aimAssist.smooth) : f.smoothing;
        const a = 1 - Math.exp(-followSmooth * dt);

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
                        " dist=" + distT.toFixed(2) +
                        " distT=" + this.zoom._distT.toFixed(2) +
                        " speed=" + sp.toFixed(2) +
                        " run=" + (run ? 1 : 0) +
                        " aim=" + (aiming ? 1 : 0) +
                        " shoulder=" + (sideSign > 0 ? "R" : "L") +
                        " cam=(" + f._x.toFixed(2) + "," + f._y.toFixed(2) + "," + f._z.toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }
}

module.exports = ThirdPersonCameraMode;