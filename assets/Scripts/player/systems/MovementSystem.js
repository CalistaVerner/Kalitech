// FILE: Scripts/player/systems/MovementSystem.js
// Author: Calista Verner
"use strict";

function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

function _readMember(v, key, fb) {
    if (!v) return fb || 0;
    try {
        const m = v[key];
        if (typeof m === "function") return num(m.call(v), fb);
        if (typeof m === "number") return m;
        if (typeof m === "string") return num(m, fb);
    } catch (_) {}
    return fb || 0;
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
        cfg = cfg || {};

        this.speed       = (cfg.speed      !== undefined) ? num(cfg.speed, 6.0) : 6.0;
        this.runSpeed    = (cfg.runSpeed   !== undefined) ? num(cfg.runSpeed, 10.0) : 10.0;
        this.airControl  = (cfg.airControl !== undefined) ? clamp(num(cfg.airControl, 0.35), 0, 1) : 0.35;
        this.jumpImpulse = (cfg.jumpImpulse !== undefined) ? num(cfg.jumpImpulse, 320.0) : 320.0;

        this.groundRay   = (cfg.groundRay   !== undefined) ? num(cfg.groundRay, 1.2) : 1.2;
        this.groundEps   = (cfg.groundEps   !== undefined) ? num(cfg.groundEps, 0.08) : 0.08;
        this.maxSlopeDot = (cfg.maxSlopeDot !== undefined) ? num(cfg.maxSlopeDot, 0.55) : 0.55;

        // re-use objects
        this._tmpN = { x: 0, z: 0 };
        this._tmpDir = { x: 0, z: 0 };
        this._tmpFrom = { x: 0, y: 0, z: 0 };
        this._tmpTo   = { x: 0, y: 0, z: 0 };
        this._tmpVel  = { x: 0, y: 0, z: 0 };
        this._tmpJump = { x: 0, y: 0, z: 0 };
    }

    configure(cfg) {
        cfg = cfg || {};
        if (cfg.speed !== undefined) this.speed = num(cfg.speed, this.speed);
        if (cfg.runSpeed !== undefined) this.runSpeed = num(cfg.runSpeed, this.runSpeed);
        if (cfg.airControl !== undefined) this.airControl = clamp(num(cfg.airControl, this.airControl), 0, 1);
        if (cfg.jumpImpulse !== undefined) this.jumpImpulse = num(cfg.jumpImpulse, this.jumpImpulse);

        if (cfg.groundRay !== undefined) this.groundRay = num(cfg.groundRay, this.groundRay);
        if (cfg.groundEps !== undefined) this.groundEps = num(cfg.groundEps, this.groundEps);
        if (cfg.maxSlopeDot !== undefined) this.maxSlopeDot = num(cfg.maxSlopeDot, this.maxSlopeDot);
        return this;
    }

    _grounded(body /* PHYS.ref */) {
        if (!body) return false;

        let p = null;
        try { p = body.position(); } catch (_) {}
        if (!p) return false;

        const px = vx(p, 0), py = vy(p, 0), pz = vz(p, 0);

        // небольшой подъём старта: стабильнее на ступеньках/наклонах
        this._tmpFrom.x = px; this._tmpFrom.y = py + 0.05; this._tmpFrom.z = pz;
        this._tmpTo.x   = px; this._tmpTo.y   = py - this.groundRay; this._tmpTo.z = pz;

        let hit = null;
        try { hit = body.raycast({ from: this._tmpFrom, to: this._tmpTo }); } catch (_) {}
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? (num((typeof n.y === "function") ? n.y() : n.y, 1)) : 1;
        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        return dist <= (this.groundRay - this.groundEps);
    }

    _applyPlanarVelocity(body /* PHYS.ref */, vxNew, vzNew) {
        if (!body) return;

        let vel = null;
        try { vel = body.velocity(); } catch (_) {}
        if (!vel) return;

        const vyOld = vy(vel, 0);
        this._tmpVel.x = vxNew;
        this._tmpVel.y = vyOld;
        this._tmpVel.z = vzNew;

        try { body.velocity(this._tmpVel); } catch (_) {}
    }

    update(tpf, body /* PHYS.ref */, inputState, yaw) {
        if (!body || !inputState) return;

        const ax = inputState.ax | 0;
        const az = inputState.az | 0;
        const run = !!inputState.run;
        const wantJump = !!inputState.jump;

        const grounded = this._grounded(body);

        // no move input
        if (ax === 0 && az === 0) {
            if (grounded && wantJump) {
                this._tmpJump.x = 0; this._tmpJump.y = this.jumpImpulse; this._tmpJump.z = 0;
                try { body.applyImpulse(this._tmpJump); } catch (_) {}
            }
            return;
        }

        // local -> normalize
        norm2_into(ax, az, this._tmpN);

        // rotate by yaw from PlayerDomain (authoritative camera view)
        rotateByYaw_into(this._tmpN.x, this._tmpN.z, num(yaw, 0), this._tmpDir);

        const sp = run ? this.runSpeed : this.speed;

        let vel = null;
        try { vel = body.velocity(); } catch (_) {}
        const vxOld = vx(vel, 0);
        const vzOld = vz(vel, 0);

        const vxT = this._tmpDir.x * sp;
        const vzT = this._tmpDir.z * sp;

        const k = grounded ? 1.0 : clamp(this.airControl, 0, 1);
        const vxNew = vxOld + (vxT - vxOld) * k;
        const vzNew = vzOld + (vzT - vzOld) * k;

        this._applyPlanarVelocity(body, vxNew, vzNew);

        if (grounded && wantJump) {
            this._tmpJump.x = 0; this._tmpJump.y = this.jumpImpulse; this._tmpJump.z = 0;
            try { body.applyImpulse(this._tmpJump); } catch (_) {}
        }
    }
}

module.exports = MovementSystem;