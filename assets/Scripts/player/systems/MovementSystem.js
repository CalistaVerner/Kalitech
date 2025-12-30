// Author: Calista Verner
"use strict";

const U = require("../util.js");

function hypot2(x, z) { return Math.sqrt(x * x + z * z); }

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

class MovementSystem {

    constructor(cfg) {
        this.configure(cfg || {});

        // jump state
        this._coyoteT = 0;
        this._jumpBufT = 0;
        this._jumpCooldownT = 0;

        // misc
        this._stepWarpCooldownT = 0;

        // temp vectors
        this._tmpN = { x: 0, z: 0 };
        this._tmpDir = { x: 0, z: 0 };
        this._tmpVel = { x: 0, y: 0, z: 0 };
        this._tmpJump = { x: 0, y: 0, z: 0 };

        // debug
        this._dbg = { t: 0, every: 60 };
    }

    configure(cfg) {
        // speed
        this.walkSpeed = U.readNum(cfg, ["speed", "walk"], 6.0);
        this.runSpeed  = U.readNum(cfg, ["speed", "run"], 10.0);
        this.accel     = U.readNum(cfg, ["speed", "acceleration"], 22.0);
        this.decel     = U.readNum(cfg, ["speed", "deceleration"], 28.0);
        this.backwardMul = U.readNum(cfg, ["speed", "backwardMul"], 1.0);
        this.strafeMul   = U.readNum(cfg, ["speed", "strafeMul"], 1.0);

        // air
        this.airAccel       = U.readNum(cfg, ["air", "acceleration"], 8.5);
        this.airMaxSpeedMul = U.readNum(cfg, ["air", "maxSpeedMul"], 0.92);

        // jump (AAA)
        this.jumpImpulse  = U.readNum(cfg, ["jump", "impulse"], 320.0);
        this.jumpVelocity = U.readNum(cfg, ["jump", "velocity"], 8.5);

        this.coyoteTime   = U.clamp(U.readNum(cfg, ["jump", "coyoteTime"], 0.12), 0, 2);
        this.bufferTime   = U.clamp(U.readNum(cfg, ["jump", "bufferTime"], 0.25), 0, 2);
        this.jumpCooldown = U.clamp(U.readNum(cfg, ["jump", "cooldown"], 0.05), 0, 1);

        // ground
        this.stickForce    = U.readNum(cfg, ["ground", "stickForce"], 18.0);
        this.snapDownSpeed = U.readNum(cfg, ["ground", "snapDownSpeed"], 22.0);

        // friction
        this.frictionGround = U.readNum(cfg, ["friction", "ground"], 10.0);
        this.frictionAir    = U.readNum(cfg, ["friction", "air"], 1.2);

        // rotation
        this.alignToCamera = U.readBool(cfg, ["rotation", "alignToCamera"], true);

        return this;
    }

    // ------------------------------------------------------------

    _applyVelocity(body, vx, vy, vz) {
        if (!body || typeof body.velocity !== "function") {
            throw new Error("[move] body.velocity() missing");
        }
        this._tmpVel.x = vx;
        this._tmpVel.y = vy;
        this._tmpVel.z = vz;
        body.velocity(this._tmpVel);
    }

    _resolveImpulseFn(body) {
        if (!body) return null;
        if (typeof body.applyImpulse === "function") return body.applyImpulse.bind(body);
        if (typeof body.impulse === "function") return body.impulse.bind(body);
        if (typeof body.addImpulse === "function") return body.addImpulse.bind(body);
        return null;
    }

    _setVerticalVelocity(body, minVy) {
        if (!body || typeof body.velocity !== "function") return false;
        const v = body.velocity();
        const vx = U.vx(v, 0);
        const vy = U.vy(v, 0);
        const vz = U.vz(v, 0);
        this._applyVelocity(body, vx, Math.max(vy, minVy), vz);
        return true;
    }

    _doJump(body, frame) {
        const fn = this._resolveImpulseFn(body);
        if (fn) {
            this._tmpJump.x = 0;
            this._tmpJump.y = this.jumpImpulse;
            this._tmpJump.z = 0;
            fn(this._tmpJump);
        }

        if (!this._setVerticalVelocity(body, this.jumpVelocity)) {
            throw new Error("[move] jump failed: velocity() missing");
        }

        this._jumpBufT = 0;
        this._jumpCooldownT = this.jumpCooldown;
        this._coyoteT = 0;

        if (LOG && LOG.info) {
            LOG.info("[move] JUMP fired grounded=" + !!(frame.pose && frame.pose.grounded));
        }
    }

    // ------------------------------------------------------------

    update(frame, body) {
        if (!frame || !body) return;

        const dt = U.num(frame.dt, 0);
        const input = frame.input;
        if (!input) return;

        // timers
        if (this._jumpCooldownT > 0) this._jumpCooldownT = Math.max(0, this._jumpCooldownT - dt);
        if (this._stepWarpCooldownT > 0) this._stepWarpCooldownT = Math.max(0, this._stepWarpCooldownT - dt);

        const grounded = !!(frame.pose && frame.pose.grounded);

        // coyote
        if (grounded) this._coyoteT = this.coyoteTime;
        else if (this._coyoteT > 0) this._coyoteT = Math.max(0, this._coyoteT - dt);

        // jump buffer
        if (input.jump) this._jumpBufT = this.bufferTime;
        else if (this._jumpBufT > 0) this._jumpBufT = Math.max(0, this._jumpBufT - dt);

        // debug jump state
        this._dbg.t++;
        if (input.jump && LOG && LOG.info) {
            LOG.info("[move] jump input grounded=" + grounded +
                " coyote=" + this._coyoteT.toFixed(3) +
                " buf=" + this._jumpBufT.toFixed(3) +
                " cd=" + this._jumpCooldownT.toFixed(3));
        }

        // jump fire
        if (this._jumpBufT > 0 && this._jumpCooldownT <= 0 && (grounded || this._coyoteT > 0)) {
            this._doJump(body, frame);
        }

        // current velocity
        const vxOld = frame.pose ? U.num(frame.pose.vx, 0) : 0;
        const vyOld = frame.pose ? U.num(frame.pose.vy, 0) : 0;
        const vzOld = frame.pose ? U.num(frame.pose.vz, 0) : 0;

        const ax = input.ax | 0;
        const az = input.az | 0;
        const run = !!input.run;

        // no input → friction
        if (ax === 0 && az === 0) {
            const fr = grounded ? this.frictionGround : this.frictionAir;
            const k = Math.max(0, 1 - fr * dt);

            let vyN = vyOld;
            if (grounded && this._jumpCooldownT <= 0 && vyN <= 0) {
                vyN = Math.max(-this.snapDownSpeed, -this.stickForce);
            }

            this._applyVelocity(body, vxOld * k, vyN, vzOld * k);
            return;
        }

        // direction
        norm2_into(ax, az, this._tmpN);

        const yaw = this.alignToCamera ? U.num(frame.view ? frame.view.yaw : 0, 0) : 0;
        rotateByYaw_into(this._tmpN.x, this._tmpN.z, yaw, this._tmpDir);

        let speed = run ? this.runSpeed : this.walkSpeed;
        if (az < 0) speed *= this.backwardMul;
        if (ax !== 0) speed *= this.strafeMul;

        let vxT = this._tmpDir.x * speed;
        let vzT = this._tmpDir.z * speed;

        // air clamp
        if (!grounded) {
            const maxAir = this.runSpeed * this.airMaxSpeedMul;
            const len = hypot2(vxT, vzT);
            if (len > maxAir && len > 1e-6) {
                const s = maxAir / len;
                vxT *= s;
                vzT *= s;
            }
        }

        const curLen = hypot2(vxOld, vzOld);
        const tarLen = hypot2(vxT, vzT);
        const rate = (tarLen > curLen)
            ? (grounded ? this.accel : this.airAccel)
            : (grounded ? this.decel : this.airAccel);

        const maxDelta = Math.max(0, rate * dt);

        const vxN = moveTowards(vxOld, vxT, maxDelta);
        const vzN = moveTowards(vzOld, vzT, maxDelta);

        let vyN = vyOld;
        if (grounded && this._jumpCooldownT <= 0 && vyN <= 0) {
            vyN = Math.max(-this.snapDownSpeed, -this.stickForce);
        }

        this._applyVelocity(body, vxN, vyN, vzN);
    }
}

module.exports = MovementSystem;