"use strict";

const U = require("../util.js");

function hypot2(x, z) { return Math.sqrt(x * x + z * z); }

function moveTowards(cur, target, maxDelta) {
    if (cur < target) return (cur + maxDelta < target) ? (cur + maxDelta) : target;
    if (cur > target) return (cur - maxDelta > target) ? (cur - maxDelta) : target;
    return target;
}

function rotateByYaw(localX, localZ, yaw, out) {
    const s = Math.sin(yaw), c = Math.cos(yaw);
    out.x = localX * c + localZ * s;
    out.z = localZ * c - localX * s;
    return out;
}

function norm2(x, z, out) {
    const l2 = x * x + z * z;
    if (l2 < 1e-12) { out.x = 0; out.z = 0; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv;
    out.z = z * inv;
    return out;
}

function teleportBody(body, x, y, z) {
    if (typeof body.teleport === "function") { body.teleport({ x, y, z }); return; }
    if (typeof body.warp === "function") { body.warp({ x, y, z }); return; }
    throw new Error("[move] body.teleport/warp required for step-down");
}

const DEFAULT_CFG = Object.freeze({
    enabled: true,
    walkSpeed: 4.4,
    runSpeed: 7.2,
    accelGround: 38.0,
    decelGround: 42.0,
    accelAir: 10.0,
    decelAir: 6.0,
    jumpSpeed: 6.6,
    coyoteTime: 0.12,
    jumpBuffer: 0.10,
    stepDownMax: 0.28,
    stickDownVel: 1.6,
    stickOnlyWhenMoving: true,
    maxHorizSpeed: 11.0,
    maxFallSpeed: 60.0
});

function cfgNum(cfg, k, fb) {
    const v = cfg && cfg[k];
    return (v === undefined || v === null) ? fb : U.num(v, fb);
}
function cfgBool(cfg, k, fb) {
    const v = cfg && cfg[k];
    return (v === undefined || v === null) ? fb : !!v;
}

class MovementSystem {
    constructor(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : Object.create(null);

        this.enabled = cfgBool(cfg, "enabled", DEFAULT_CFG.enabled);

        this.walkSpeed = cfgNum(cfg, "walkSpeed", DEFAULT_CFG.walkSpeed);
        this.runSpeed = cfgNum(cfg, "runSpeed", DEFAULT_CFG.runSpeed);

        this.accelGround = cfgNum(cfg, "accelGround", DEFAULT_CFG.accelGround);
        this.decelGround = cfgNum(cfg, "decelGround", DEFAULT_CFG.decelGround);
        this.accelAir = cfgNum(cfg, "accelAir", DEFAULT_CFG.accelAir);
        this.decelAir = cfgNum(cfg, "decelAir", DEFAULT_CFG.decelAir);

        this.jumpSpeed = cfgNum(cfg, "jumpSpeed", DEFAULT_CFG.jumpSpeed);
        this.coyoteTime = cfgNum(cfg, "coyoteTime", DEFAULT_CFG.coyoteTime);
        this.jumpBuffer = cfgNum(cfg, "jumpBuffer", DEFAULT_CFG.jumpBuffer);

        this.stepDownMax = cfgNum(cfg, "stepDownMax", DEFAULT_CFG.stepDownMax);
        this.stickDownVel = cfgNum(cfg, "stickDownVel", DEFAULT_CFG.stickDownVel);
        this.stickOnlyWhenMoving = cfgBool(cfg, "stickOnlyWhenMoving", DEFAULT_CFG.stickOnlyWhenMoving);

        this.maxHorizSpeed = cfgNum(cfg, "maxHorizSpeed", DEFAULT_CFG.maxHorizSpeed);
        this.maxFallSpeed = cfgNum(cfg, "maxFallSpeed", DEFAULT_CFG.maxFallSpeed);

        this._coyote = 0;
        this._jumpBuf = 0;

        this._wishLocal = { x: 0, z: 0 };
        this._wishWorld = { x: 0, z: 0 };
        this._wishDir = { x: 0, z: 0 };
    }

    update(frame, body) {
        if (!this.enabled) return;
        if (!frame || !frame.input || !frame.view || !frame.pose) return;
        if (!frame.ground) throw new Error("[move] frame.ground required (probeGroundCapsule first)");
        if (!body) return;

        if (typeof body.velocity !== "function") throw new Error("[move] body.velocity() required");
        if (typeof body.position !== "function") throw new Error("[move] body.position() required");

        const dt = U.clamp(U.num(frame.dt, 1 / 60), 0, 0.05);

        const input = frame.input;
        const yaw = U.num(frame.view.yaw, 0);
        const grounded = !!frame.pose.grounded;

        if (grounded) this._coyote = this.coyoteTime;
        else this._coyote = Math.max(0, this._coyote - dt);

        if (input.jump) this._jumpBuf = this.jumpBuffer;
        else this._jumpBuf = Math.max(0, this._jumpBuf - dt);

        const v = body.velocity();
        let vx = U.vx(v, 0);
        let vy = U.vy(v, 0);
        let vz = U.vz(v, 0);

        if (vy < -this.maxFallSpeed) vy = -this.maxFallSpeed;

        this._wishLocal.x = input.ax | 0;
        this._wishLocal.z = input.az | 0;

        norm2(this._wishLocal.x, this._wishLocal.z, this._wishDir);
        rotateByYaw(this._wishDir.x, this._wishDir.z, yaw, this._wishWorld);

        const hasMove = (this._wishDir.x !== 0 || this._wishDir.z !== 0);
        const targetSpeed = input.run ? this.runSpeed : this.walkSpeed;

        const targetVx = hasMove ? (this._wishWorld.x * targetSpeed) : 0;
        const targetVz = hasMove ? (this._wishWorld.z * targetSpeed) : 0;

        const accel = grounded ? this.accelGround : this.accelAir;
        const decel = grounded ? this.decelGround : this.decelAir;

        if (hasMove) {
            vx = moveTowards(vx, targetVx, accel * dt);
            vz = moveTowards(vz, targetVz, accel * dt);
        } else {
            vx = moveTowards(vx, 0, decel * dt);
            vz = moveTowards(vz, 0, decel * dt);
        }

        const hs = hypot2(vx, vz);
        if (hs > this.maxHorizSpeed) {
            const k = this.maxHorizSpeed / hs;
            vx *= k;
            vz *= k;
        }

        let jumpedThisTick = false;
        if (this._jumpBuf > 0 && this._coyote > 0) {
            this._jumpBuf = 0;
            this._coyote = 0;

            if (vy < 0) vy = 0;
            vy = this.jumpSpeed;
            jumpedThisTick = true;
        }

        const g = frame.ground;
        const allowStick = grounded && !g.steep && !jumpedThisTick && vy <= 0;

        if (allowStick) {
            if (!this.stickOnlyWhenMoving || hasMove) {
                if (vy > -this.stickDownVel) vy = -this.stickDownVel;
            }

            if (g.hasHit && g.footDistance < 0) {
                const down = -g.footDistance;
                if (down > 1e-4 && down <= this.stepDownMax) {
                    const p = body.position();
                    const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

                    teleportBody(body, px, py + g.footDistance, pz);
                    vy = -this.stickDownVel;
                }
            }
        }

        body.velocity({ x: vx, y: vy, z: vz });

        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;
        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = (vy < 0) ? -vy : 0;
    }
}

module.exports = MovementSystem;