// FILE: Scripts/player/systems/MovementSystem.js
// Author: Calista Verner
"use strict";

function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : fb; }

function read(obj, path, fb) {
    if (!obj) return fb;
    let v = obj;
    for (let i = 0; i < path.length; i++) {
        if (v == null) return fb;
        v = v[path[i]];
    }
    return (typeof v === "number" || typeof v === "string") ? num(v, fb) : fb;
}

function _readMember(v, key, fb) {
    if (!v) return fb;
    try {
        const m = v[key];
        if (typeof m === "function") return num(m.call(v), fb);
        if (typeof m === "number") return m;
        if (typeof m === "string") return num(m, fb);
    } catch (_) {}
    return fb;
}
function vx(v, fb) { return _readMember(v, "x", fb); }
function vy(v, fb) { return _readMember(v, "y", fb); }
function vz(v, fb) { return _readMember(v, "z", fb); }

function norm2_into(x, z, out) {
    const l2 = x * x + z * z;
    if (l2 < 1e-12) { out.x = 0; out.z = 0; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv;
    out.z = z * inv;
    return out;
}

function rotateByYaw_into(localX, localZ, yaw, out) {
    const s = Math.sin(yaw), c = Math.cos(yaw);
    out.x = localX * c + localZ * s;
    out.z = localZ * c - localX * s;
    return out;
}

class MovementSystem {
    constructor(cfg) {
        this.configure(cfg || {});

        // re-use objects
        this._tmpN    = { x: 0, z: 0 };
        this._tmpDir  = { x: 0, z: 0 };
        this._tmpFrom = { x: 0, y: 0, z: 0 };
        this._tmpTo   = { x: 0, y: 0, z: 0 };
        this._tmpVel  = { x: 0, y: 0, z: 0 };
        this._tmpJump = { x: 0, y: 0, z: 0 };
    }

    configure(cfg) {
        // --- speed ---
        this.walkSpeed   = read(cfg, ["speed", "walk"], 6.0);
        this.runSpeed    = read(cfg, ["speed", "run"], 10.0);
        this.backwardMul = read(cfg, ["speed", "backwardMul"], 1.0);
        this.strafeMul   = read(cfg, ["speed", "strafeMul"], 1.0);

        // --- air ---
        this.airControl = clamp(read(cfg, ["air", "control"], 0.35), 0, 1);

        // --- jump ---
        this.jumpImpulse = read(cfg, ["jump", "impulse"], 320.0);

        // --- ground ---
        this.groundRay   = read(cfg, ["ground", "rayLength"], 1.2);
        this.groundEps   = read(cfg, ["ground", "eps"], 0.08);
        this.maxSlopeDot = read(cfg, ["ground", "maxSlopeDot"], 0.55);

        // --- rotation ---
        this.alignToCamera = !!read(cfg, ["rotation", "alignToCamera"], true);

        // --- debug ---
        this.debugEnabled = !!read(cfg, ["debug", "enabled"], false);
        this.debugEvery   = read(cfg, ["debug", "everyFrames"], 120);

        return this;
    }

    _grounded(body) {
        if (!body) return false;

        let p = null;
        try { p = body.position(); } catch (_) {}
        if (!p) return false;

        const px = vx(p, 0), py = vy(p, 0), pz = vz(p, 0);

        this._tmpFrom.x = px;
        this._tmpFrom.y = py + 0.05;
        this._tmpFrom.z = pz;

        this._tmpTo.x = px;
        this._tmpTo.y = py - this.groundRay;
        this._tmpTo.z = pz;

        let hit = null;
        try { hit = body.raycast({ from: this._tmpFrom, to: this._tmpTo }); } catch (_) {}
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? num((typeof n.y === "function") ? n.y() : n.y, 1) : 1;
        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        return dist <= (this.groundRay - this.groundEps);
    }

    _applyPlanarVelocity(body, vxNew, vzNew) {
        let vel = null;
        try { vel = body.velocity(); } catch (_) {}
        if (!vel) return;

        this._tmpVel.x = vxNew;
        this._tmpVel.y = vy(vel, 0);
        this._tmpVel.z = vzNew;

        try { body.velocity(this._tmpVel); } catch (_) {}
    }

    update(tpf, body, input, yaw) {
        if (!body || !input) return;

        const ax = input.ax | 0;
        const az = input.az | 0;
        const run = !!input.run;
        const wantJump = !!input.jump;

        const grounded = this._grounded(body);

        if (ax === 0 && az === 0) {
            if (grounded && wantJump) {
                this._tmpJump.x = 0;
                this._tmpJump.y = this.jumpImpulse;
                this._tmpJump.z = 0;
                try { body.applyImpulse(this._tmpJump); } catch (_) {}
            }
            return;
        }

        norm2_into(ax, az, this._tmpN);

        rotateByYaw_into(
            this._tmpN.x,
            this._tmpN.z,
            this.alignToCamera ? num(yaw, 0) : 0,
            this._tmpDir
        );

        let speed = run ? this.runSpeed : this.walkSpeed;
        if (az < 0) speed *= this.backwardMul;
        if (ax !== 0) speed *= this.strafeMul;

        let vel = null;
        try { vel = body.velocity(); } catch (_) {}

        const vxOld = vx(vel, 0);
        const vzOld = vz(vel, 0);

        const vxT = this._tmpDir.x * speed;
        const vzT = this._tmpDir.z * speed;

        const k = grounded ? 1.0 : this.airControl;

        this._applyPlanarVelocity(
            body,
            vxOld + (vxT - vxOld) * k,
            vzOld + (vzT - vzOld) * k
        );

        if (grounded && wantJump) {
            this._tmpJump.x = 0;
            this._tmpJump.y = this.jumpImpulse;
            this._tmpJump.z = 0;
            try { body.applyImpulse(this._tmpJump); } catch (_) {}
        }
    }
}

module.exports = MovementSystem;
