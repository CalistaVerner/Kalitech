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

function moveTowards(current, target, maxDelta) {
    if (current < target) return (current + maxDelta < target) ? (current + maxDelta) : target;
    if (current > target) return (current - maxDelta > target) ? (current - maxDelta) : target;
    return target;
}

function hypot2(x, z) { return Math.sqrt(x * x + z * z); }

class MovementSystem {
    constructor(cfg) {
        this.configure(cfg || {});

        // jump timing state
        this._coyoteT = 0;
        this._jumpBufT = 0;
        this._jumpCooldownT = 0;
        this._wasGrounded = false;

        // re-use objects
        this._tmpN    = { x: 0, z: 0 };
        this._tmpDir  = { x: 0, z: 0 };
        this._tmpFrom = { x: 0, y: 0, z: 0 };
        this._tmpTo   = { x: 0, y: 0, z: 0 };
        this._tmpVel  = { x: 0, y: 0, z: 0 };
        this._tmpJump = { x: 0, y: 0, z: 0 };

        // step-up temps
        this._tmpHitFrom = { x: 0, y: 0, z: 0 };
        this._tmpHitTo   = { x: 0, y: 0, z: 0 };
        this._tmpPos     = { x: 0, y: 0, z: 0 };

        // ✅ avoid per-frame allocations when calling raycast
        this._rayArgsGround = { from: this._tmpFrom, to: this._tmpTo };
        this._rayArgsStep   = { from: this._tmpHitFrom, to: this._tmpHitTo };
    }

    configure(cfg) {
        // --- speed ---
        this.walkSpeed   = read(cfg, ["speed", "walk"], 6.0);
        this.runSpeed    = read(cfg, ["speed", "run"], 10.0);
        this.accel       = read(cfg, ["speed", "acceleration"], 22.0);
        this.decel       = read(cfg, ["speed", "deceleration"], 28.0);
        this.backwardMul = read(cfg, ["speed", "backwardMul"], 1.0);
        this.strafeMul   = read(cfg, ["speed", "strafeMul"], 1.0);

        // --- air ---
        this.airControl = clamp(read(cfg, ["air", "control"], 0.35), 0, 1);
        this.airAccel   = read(cfg, ["air", "acceleration"], 8.5);
        this.airMaxSpeedMul = read(cfg, ["air", "maxSpeedMul"], 0.92);

        // --- jump ---
        this.jumpImpulse = read(cfg, ["jump", "impulse"], 320.0);
        this.coyoteTime  = clamp(read(cfg, ["jump", "coyoteTime"], 0.12), 0, 2);
        this.bufferTime  = clamp(read(cfg, ["jump", "bufferTime"], 0.14), 0, 2);
        this.jumpCooldown = clamp(read(cfg, ["jump", "cooldown"], 0.05), 0, 1);

        // --- ground ---
        this.groundRay   = read(cfg, ["ground", "rayLength"], 1.2);
        this.groundEps   = read(cfg, ["ground", "eps"], 0.08);
        this.maxSlopeDot = read(cfg, ["ground", "maxSlopeDot"], 0.55);
        this.stickForce  = read(cfg, ["ground", "stickForce"], 18.0);
        this.snapDownSpeed = read(cfg, ["ground", "snapDownSpeed"], 22.0);

        // --- friction ---
        this.frictionGround = read(cfg, ["friction", "ground"], 10.0);
        this.frictionAir    = read(cfg, ["friction", "air"], 1.2);

        // --- step up ---
        this.stepEnabled = !!read(cfg, ["stepUp", "enabled"], false);
        this.stepMaxHeight = read(cfg, ["stepUp", "maxHeight"], 0.45);
        this.stepForwardProbe = read(cfg, ["stepUp", "forwardProbe"], 0.35);
        this.stepUpProbe = read(cfg, ["stepUp", "upProbe"], 0.55);
        this.stepSnapUpSpeed = read(cfg, ["stepUp", "snapUpSpeed"], 24.0);
        this.stepMinClearNormalY = read(cfg, ["stepUp", "minClearNormalY"], 0.35);

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
        try { hit = body.raycast(this._rayArgsGround); } catch (_) {}
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? num((typeof n.y === "function") ? n.y() : n.y, 1) : 1;
        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        return dist <= (this.groundRay - this.groundEps);
    }

    _applyVelocity(body, vxNew, vyNew, vzNew) {
        this._tmpVel.x = vxNew;
        this._tmpVel.y = vyNew;
        this._tmpVel.z = vzNew;
        try { body.velocity(this._tmpVel); } catch (_) {}
    }

    _readPos_into(body, out) {
        let p = null;
        try { p = body.position(); } catch (_) {}
        out.x = vx(p, 0);
        out.y = vy(p, 0);
        out.z = vz(p, 0);
        return out;
    }

    _raycast(body, fx, fy, fz, tx, ty, tz) {
        this._tmpHitFrom.x = fx; this._tmpHitFrom.y = fy; this._tmpHitFrom.z = fz;
        this._tmpHitTo.x   = tx; this._tmpHitTo.y   = ty; this._tmpHitTo.z   = tz;
        let hit = null;
        try { hit = body.raycast(this._rayArgsStep); } catch (_) { hit = null; }
        return hit;
    }

    _tryStepUp(body, grounded, moveX, moveZ) {
        if (!this.stepEnabled || !grounded) return false;

        const l = moveX * moveX + moveZ * moveZ;
        if (l < 1e-6) return false;

        const inv = 1.0 / Math.sqrt(l);
        const dx = moveX * inv;
        const dz = moveZ * inv;

        const pos = this._readPos_into(body, this._tmpPos);

        const shinY = pos.y + 0.10;
        const fx = pos.x;
        const fz = pos.z;
        const tx = pos.x + dx * this.stepForwardProbe;
        const tz = pos.z + dz * this.stepForwardProbe;

        const hitLow = this._raycast(body, fx, shinY, fz, tx, shinY, tz);
        if (!hitLow || !hitLow.hit) return false;

        const nLow = hitLow.normal || null;
        const nyLow = nLow ? num((typeof nLow.y === "function") ? nLow.y() : nLow.y, 0) : 0;
        if (nyLow >= this.maxSlopeDot) return false;

        const upY = pos.y + this.stepMaxHeight;
        const hitHigh = this._raycast(body, fx, upY, fz, tx, upY, tz);
        if (hitHigh && hitHigh.hit) return false;

        const downFromY = pos.y + this.stepMaxHeight + this.stepUpProbe;
        const downToY = pos.y - this.groundRay;
        const hitDown = this._raycast(body, tx, downFromY, tz, tx, downToY, tz);
        if (!hitDown || !hitDown.hit) return false;

        const n = hitDown.normal || null;
        const ny = n ? num((typeof n.y === "function") ? n.y() : n.y, 1) : 1;
        if (ny < this.stepMinClearNormalY) return false;

        const dist = num(hitDown.distance, 9999);
        const contactY = downFromY - dist;
        const stepH = contactY - pos.y;
        if (stepH < 0.02 || stepH > (this.stepMaxHeight + 1e-4)) return false;

        this._tmpPos.x = tx;
        this._tmpPos.y = contactY + 0.01;
        this._tmpPos.z = tz;

        try { body.warp(this._tmpPos); } catch (_) { return false; }

        // minimal snap-up to avoid immediate re-collide
        let vel = null;
        try { vel = body.velocity(); } catch (_) { vel = null; }
        const vxOld = vx(vel, 0);
        const vzOld = vz(vel, 0);
        const vyOld = vy(vel, 0);
        const vyNew = Math.max(vyOld, this.stepSnapUpSpeed);
        this._applyVelocity(body, vxOld, vyNew, vzOld);

        return true;
    }

    _doJump(body) {
        this._tmpJump.x = 0;
        this._tmpJump.y = this.jumpImpulse;
        this._tmpJump.z = 0;
        try { body.applyImpulse(this._tmpJump); } catch (_) {}

        this._jumpBufT = 0;
        this._jumpCooldownT = this.jumpCooldown;
        this._coyoteT = 0;
        this._wasGrounded = false;
    }

    // groundedOverride: boolean from Player pose (avoids second raycast)
    // pose: optional {vx,vy,vz} from Player (avoids second velocity read)
    update(tpf, body, input, yaw, groundedOverride, pose) {
        if (!body || !input) return;

        tpf = num(tpf, 0);
        if (!(tpf > 0)) tpf = 0;

        const ax = input.ax | 0;
        const az = input.az | 0;
        const run = !!input.run;
        const wantJump = !!input.jump; // InputRouter gives JUST_PRESSED

        const grounded = (groundedOverride === true || groundedOverride === false)
            ? groundedOverride
            : this._grounded(body);

        // timers
        if (this._jumpCooldownT > 0) this._jumpCooldownT = Math.max(0, this._jumpCooldownT - tpf);

        if (grounded) this._coyoteT = this.coyoteTime;
        else if (this._coyoteT > 0) this._coyoteT = Math.max(0, this._coyoteT - tpf);

        if (wantJump) this._jumpBufT = this.bufferTime;
        else if (this._jumpBufT > 0) this._jumpBufT = Math.max(0, this._jumpBufT - tpf);

        if (this._jumpBufT > 0 && this._jumpCooldownT <= 0 && (grounded || this._coyoteT > 0)) {
            this._doJump(body);
        }

        // ✅ optionally reuse velocity from Player pose (computed once per frame)
        let vxOld, vyOld, vzOld;
        if (pose && Number.isFinite(+pose.vx) && Number.isFinite(+pose.vy) && Number.isFinite(+pose.vz)) {
            vxOld = +pose.vx; vyOld = +pose.vy; vzOld = +pose.vz;
        } else {
            let vel = null;
            try { vel = body.velocity(); } catch (_) { vel = null; }
            vxOld = vx(vel, 0);
            vyOld = vy(vel, 0);
            vzOld = vz(vel, 0);
        }

        // no input: friction + ground stick
        if (ax === 0 && az === 0) {
            const fr = grounded ? this.frictionGround : this.frictionAir;
            const k = Math.max(0, 1.0 - fr * tpf);
            const vxN = vxOld * k;
            const vzN = vzOld * k;

            let vyN = vyOld;
            if (grounded && this._jumpCooldownT <= 0) {
                if (vyN > 0) vyN = 0;
                vyN = Math.min(vyN, -this.stickForce);
                vyN = Math.max(vyN, -this.snapDownSpeed);
            }

            this._applyVelocity(body, vxN, vyN, vzN);
            this._wasGrounded = grounded;
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

        let vxT = this._tmpDir.x * speed;
        let vzT = this._tmpDir.z * speed;

        if (!grounded) {
            const maxAir = this.runSpeed * this.airMaxSpeedMul;
            const tLen = hypot2(vxT, vzT);
            if (tLen > maxAir && tLen > 1e-6) {
                const sMul = maxAir / tLen;
                vxT *= sMul;
                vzT *= sMul;
            }
        }

        const curLen = hypot2(vxOld, vzOld);
        const tarLen = hypot2(vxT, vzT);
        const useAccel = grounded ? this.accel : this.airAccel;
        const useDecel = grounded ? this.decel : this.airAccel;
        const rate = (tarLen > curLen) ? useAccel : useDecel;
        const maxDelta = Math.max(0, rate * tpf);

        const vxN = moveTowards(vxOld, vxT, maxDelta);
        const vzN = moveTowards(vzOld, vzT, maxDelta);

        this._tryStepUp(body, grounded, vxT, vzT);

        let vyN = vyOld;
        if (grounded && this._jumpCooldownT <= 0) {
            if (vyN > 0) vyN = 0;
            vyN = Math.min(vyN, -this.stickForce);
            vyN = Math.max(vyN, -this.snapDownSpeed);
        }

        this._applyVelocity(body, vxN, vyN, vzN);
        this._wasGrounded = grounded;
    }
}

module.exports = MovementSystem;
