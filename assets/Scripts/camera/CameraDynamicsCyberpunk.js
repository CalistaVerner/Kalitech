"use strict";

// Cyberpunk/AAA camera dynamics layer (mode-agnostic).
// Works as a post-pass after mode.update().

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

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

class CameraDynamicsCyberpunk {
    constructor() {
        this.t = 0;

        // head bob
        this.bob = {
            walkFreq: 7.2,
            runFreq: 10.2,
            walkAmpY: 0.030,
            runAmpY: 0.060,
            walkAmpX: 0.020,
            runAmpX: 0.040,
            smooth: 14.0,
            y: 0,
            x: 0
        };

        // mouse/aim sway
        this.sway = {
            yawMul: 0.045,
            pitchMul: 0.035,
            smooth: 18.0,
            x: 0,
            y: 0
        };

        // handheld micro drift (very subtle)
        this.handheld = {
            amp: 0.006,
            freq: 1.2,
            smooth: 6.0,
            x: 0,
            y: 0
        };

        // jump/land spring
        this.spring = {
            y: 0,
            vy: 0,
            stiffness: 55.0,
            damping: 11.0
        };

        // velocity/acceleration kick
        this.kick = {
            z: 0,
            vz: 0,
            stiffness: 45.0,
            damping: 10.0
        };

        // optional FOV
        this.fov = {
            enabled: true,
            base: 0,
            runAdd: 8.0,     // degrees-ish, depending on your camera API
            smooth: 10.0,
            _inited: false
        };
    }

    configure(cfg) {
        if (!cfg) return;
        if (cfg.bob) Object.assign(this.bob, cfg.bob);
        if (cfg.sway) Object.assign(this.sway, cfg.sway);
        if (cfg.handheld) Object.assign(this.handheld, cfg.handheld);
        if (cfg.spring) Object.assign(this.spring, cfg.spring);
        if (cfg.kick) Object.assign(this.kick, cfg.kick);
        if (cfg.fov) Object.assign(this.fov, cfg.fov);
    }

    // events from gameplay (preferred)
    onJump(strength) {
        const s = clamp(num(strength, 1), 0.2, 3.0);
        // push down camera a bit (takeoff)
        this.spring.vy += -0.9 * s;
        this.kick.vz += 0.35 * s;
    }

    onLand(strength) {
        const s = clamp(num(strength, 1), 0.2, 6.0);
        // compress then rebound
        this.spring.vy += 1.4 * s;
        this.kick.vz += -0.55 * s;
    }

    apply(ctx) {
        const cam = ctx.cam;
        const dt = clamp(num(ctx.dt, 1 / 60), 1 / 240, 1 / 20);
        this.t += dt;

        const grounded = ctx.grounded !== false; // default true
        const running = ctx.running === true;
        const speed = Math.max(0, num(ctx.speed, 0));

        // -------------- SWAY from mouse deltas --------------
        const dx = num(ctx.mouseDx, 0);
        const dy = num(ctx.mouseDy, 0);

        const targetSwayX = -dx * this.sway.yawMul;
        const targetSwayY = -dy * this.sway.pitchMul;

        const as = 1 - Math.exp(-this.sway.smooth * dt);
        this.sway.x += (targetSwayX - this.sway.x) * as;
        this.sway.y += (targetSwayY - this.sway.y) * as;

        // -------------- HANDHELD micro drift (subtle) --------------
        const hh = this.handheld;
        const hx = Math.sin(this.t * hh.freq) * hh.amp;
        const hy = Math.cos(this.t * (hh.freq * 1.37)) * (hh.amp * 0.85);

        const ah = 1 - Math.exp(-hh.smooth * dt);
        this.handheld.x += (hx - this.handheld.x) * ah;
        this.handheld.y += (hy - this.handheld.y) * ah;

        // -------------- HEAD BOB (only grounded + moving) --------------
        let bobX = 0, bobY = 0;
        if (grounded && speed > 0.12) {
            const b = this.bob;
            const f = running ? b.runFreq : b.walkFreq;
            const ampY = running ? b.runAmpY : b.walkAmpY;
            const ampX = running ? b.runAmpX : b.walkAmpX;

            // normalize speed influence (tune 0..1)
            const k = clamp(speed / (running ? 8.0 : 5.0), 0, 1);

            // cyberpunk-ish: a bit snappier
            bobY = Math.sin(this.t * f) * ampY * k;
            bobX = Math.cos(this.t * (f * 0.5)) * ampX * k;
        }

        const ab = 1 - Math.exp(-this.bob.smooth * dt);
        this.bob.x += (bobX - this.bob.x) * ab;
        this.bob.y += (bobY - this.bob.y) * ab;

        // -------------- SPRING (jump/land) --------------
        // simple damped spring towards 0
        const sp = this.spring;
        const ay = (-sp.stiffness * sp.y - sp.damping * sp.vy);
        sp.vy += ay * dt;
        sp.y += sp.vy * dt;

        // -------------- KICK (accel/brake feel) --------------
        const kk = this.kick;
        const az = (-kk.stiffness * kk.z - kk.damping * kk.vz);
        kk.vz += az * dt;
        kk.z += kk.vz * dt;

        // -------------- APPLY local offsets --------------
        // moveLocal(x,y,z) exists in your FreeCam usage. :contentReference[oaicite:1]{index=1}
        const offX = this.sway.x + this.bob.x + this.handheld.x;
        const offY = this.sway.y + this.bob.y + this.handheld.y + sp.y;
        const offZ = kk.z;

        try { cam.moveLocal(offX, offY, offZ); } catch (_) {}

        // -------------- OPTIONAL FOV kick --------------
        this._applyFov(cam, dt, running, speed);

        // -------------- OPTIONAL roll (if exists) --------------
        // If your engine camera supports roll(), setRoll(), etc - do it gently.
        // We feature-detect to avoid crashes.
        try {
            const roll = clamp(offX * 0.85, -0.12, 0.12);
            if (typeof cam.setRoll === "function") cam.setRoll(roll);
            else if (typeof cam.roll === "function") cam.roll(roll);
        } catch (_) {}
    }

    _applyFov(cam, dt, running, speed) {
        if (!this.fov.enabled) return;
        try {
            // detect API
            let cur = 0;
            if (typeof cam.fov === "function") cur = num(cam.fov(), 0);
            else if (typeof cam.getFov === "function") cur = num(cam.getFov(), 0);
            else return;

            if (!this.fov._inited) {
                this.fov.base = cur;
                this.fov._inited = true;
            }

            const k = clamp(speed / 8.0, 0, 1);
            const target = this.fov.base + (running ? (this.fov.runAdd * k) : 0);

            const a = 1 - Math.exp(-this.fov.smooth * dt);
            const next = cur + (target - cur) * a;

            if (typeof cam.setFov === "function") cam.setFov(next);
            else if (typeof cam.fov === "function") cam.fov(next);
        } catch (_) {}
    }
}

module.exports = CameraDynamicsCyberpunk;