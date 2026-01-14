// FILE: Scripts/player/modes/first.js
"use strict";

const U = require("../camUtil.js");

/**
 * CP2077-style First Person:
 *  - head-bob + micro sway (optional, subtle)
 *  - pitch clamp + soft edges
 *  - publishes per-mode config: sensitivity + FOV (+ ADS variants)
 *
 * Contract assumptions:
 *  - ctx.look.yaw / ctx.look.pitch exist (radians)
 *  - ctx.dt exists
 *  - ctx.zoom.value() exists (distance / zoom; used as "aim amount" if you prefer)
 *  - Optional:
 *      ctx.input.aiming (boolean) OR ctx.aiming
 *      ctx.moveDir or ctx.bodyVel for speed-based bob
 *  - Camera system reads:
 *      ctx.modeConfig (or ctx.zoneOverrides) for sensitivity/fov
 */
function clamp(v, lo, hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

function expAlpha(rate, dt) {
    return 1.0 - Math.exp(-Math.max(0, rate) * Math.max(0, dt));
}

function smoothstep(t) {
    t = clamp(t, 0, 1);
    return t * t * (3 - 2 * t);
}

class FirstPersonCameraMode {
    constructor() {
        this.id = "first";
        this.meta = {supportsZoom: true, hasCollision: true, numRays: 0, playerModelVisible: false};

        // camera anchor (relative to body)
        this.headOffset = {x: 0.0, y: 1.65, z: 0.0};

        // =========================
        // CONFIG (publishable)
        // =========================
        // base (hip-fire) feel
        this.cfg = {
            // sensitivity (multipliers; your input system can use them as scale)
            sensX: 1.00,
            sensY: 1.00,

            // FOV in degrees (engine typically wants degrees)
            fov: 78.0,

            // ADS / aiming
            adsSensMul: 0.72,     // CP-like: slower aim
            adsFov: 62.0,
            adsBlendRate: 16.0,   // how quickly we blend to ADS values

            // pitch limits (radians)
            pitchMin: -1.35,      // ~ -77 deg
            pitchMax: 1.20,      // ~  69 deg
            pitchSoft: 0.18,      // soften at ends

            // subtle camera motion (can be 0 to disable)
            bobEnabled: true,
            bobRate: 10.0,        // frequency
            bobAmpY: 0.030,       // meters
            bobAmpX: 0.015,       // meters

            swayEnabled: true,
            swayAmpX: 0.010,      // meters (camera local right)
            swayAmpY: 0.006,      // meters (camera local up)
            swayRate: 10.0,

            // smoothing for offsets (not look input!)
            offsetSmooth: 22.0
        };

        // internal state
        this._init = false;
        this._ads = 0.0;                 // 0..1
        this._t = 0.0;                   // time accumulator for bob/sway
        this._off = {x: 0, y: 0, z: 0};// smoothed offset
    }

    _ensureModeConfig(ctx) {
        // publish to a predictable place; camera pipeline should read from here.
        // If your pipeline already reads ctx.zoneOverrides, swap to that.
        let mc = ctx.modeConfig;
        if (!mc) {
            mc = {};
            ctx.modeConfig = mc;
        }
        return mc;
    }

    _readMoveSpeed01(ctx) {
        const mv = ctx.moveDir || ctx.move || ctx.motion || null;
        const vel = ctx.bodyVel || ctx.vel || null;

        if (mv) {
            const s = +mv.speed || +mv.mag || +mv.length || 0;
            return clamp(s, 0, 1);
        }
        if (vel) {
            const vx = +U.vx(vel, 0) || 0;
            const vz = +U.vz(vel, 0) || 0;
            const len = Math.hypot(vx, vz);
            return clamp(len / 6.0, 0, 1);
        }
        return 0;
    }

    _softClampPitch(p) {
        const c = this.cfg;
        const lo = c.pitchMin, hi = c.pitchMax;

        p = clamp(p, lo - 0.35, hi + 0.35);

        if (p < lo) {
            const t = clamp((lo - p) / Math.max(1e-6, c.pitchSoft), 0, 1);
            p = lo - (1.0 - smoothstep(1.0 - t)) * c.pitchSoft;
        } else if (p > hi) {
            const t = clamp((p - hi) / Math.max(1e-6, c.pitchSoft), 0, 1);
            p = hi + (1.0 - smoothstep(1.0 - t)) * c.pitchSoft;
        }
        return clamp(p, lo, hi);
    }

    update(ctx) {
        const dt = Math.max(0, +ctx.dt || 0);

        // read aim flag
        const aiming = !!(ctx.aiming || (ctx.input && ctx.input.aiming));

        // clamp pitch (we don't rewrite ctx.look unless your pipeline wants it)
        const yaw = +ctx.look.yaw || 0;
        let pitch = +ctx.look.pitch || 0;
        pitch = this._softClampPitch(pitch);

        // ADS blend (0..1)
        const a = expAlpha(this.cfg.adsBlendRate, dt);
        this._ads = lerp(this._ads, aiming ? 1.0 : 0.0, a);

        // publish sensitivity + fov to config
        const mc = this._ensureModeConfig(ctx);

        const ads = this._ads;
        const sensMul = lerp(1.0, this.cfg.adsSensMul, ads);
        mc.sensX = this.cfg.sensX * sensMul;
        mc.sensY = this.cfg.sensY * sensMul;

        mc.fov = lerp(this.cfg.fov, this.cfg.adsFov, ads);

        // If your camera system expects different keys, also mirror them:
        mc.sensitivityX = mc.sensX;
        mc.sensitivityY = mc.sensY;
        mc.fovDeg = mc.fov;

        // base head position
        const p = ctx.bodyPos;

        let x = U.vx(p, 0) + this.headOffset.x;
        let y = U.vy(p, 0) + this.headOffset.y;
        let z = U.vz(p, 0) + this.headOffset.z;

        // subtle CP-like motion
        const sp01 = this._readMoveSpeed01(ctx);

        if (!this._init) {
            this._init = true;
            this._t = 0;
            this._off.x = 0;
            this._off.y = 0;
            this._off.z = 0;
        } else {
            this._t += dt;
        }

        // camera local basis from yaw (flat)
        const sinY = Math.sin(yaw);
        const cosY = Math.cos(yaw);
        const rx = cosY, rz = -sinY; // right
        const fx = sinY, fz = cosY;  // forward (flat)

        let ox = 0, oy = 0;

        if (this.cfg.bobEnabled && sp01 > 0.01) {
            const w = this.cfg.bobRate;
            const t = this._t * w;

            // CP-ish: mostly vertical + little lateral; reduced in ADS
            const bobScale = (1.0 - 0.65 * ads) * sp01;

            oy += Math.sin(t) * this.cfg.bobAmpY * bobScale;
            ox += Math.sin(t * 0.5 + 1.1) * this.cfg.bobAmpX * bobScale;
        }

        if (this.cfg.swayEnabled) {
            const w = this.cfg.swayRate;
            const t = this._t * w;

            // tiny "breathing"/hand sway, reduced in ADS
            const swayScale = (1.0 - 0.75 * ads);

            ox += Math.sin(t * 0.35) * this.cfg.swayAmpX * swayScale;
            oy += Math.sin(t * 0.27 + 0.7) * this.cfg.swayAmpY * swayScale;
        }

        // smooth offsets
        const oA = expAlpha(this.cfg.offsetSmooth, dt);
        this._off.x = lerp(this._off.x, ox, oA);
        this._off.y = lerp(this._off.y, oy, oA);

        // apply offsets in camera local space (right/up)
        x += rx * this._off.x;
        z += rz * this._off.x;
        y += this._off.y;

        // output position
        ctx.outPos.x = x;
        ctx.outPos.y = y;
        ctx.outPos.z = z;

        // target: keep same height, look toward forward plane
        ctx.target.x = x + fx;
        ctx.target.y = y + Math.sin(pitch); // slight pitch influence
        ctx.target.z = z + fz;

        // optional: publish corrected pitch back if your camera pipeline wants it
        if (ctx.outLook) {
            ctx.outLook.yaw = yaw;
            ctx.outLook.pitch = pitch;
        }
    }
}

module.exports = FirstPersonCameraMode;