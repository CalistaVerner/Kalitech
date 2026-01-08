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
    if (typeof body.teleport === "function") {
        body.teleport({x, y, z});
        return true;
    }
    if (typeof body.warp === "function") {
        body.warp({x, y, z});
        return true;
    }
    return false;
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
    constructor(movCfg) {
        const cfg = (movCfg && typeof movCfg === "object") ? movCfg : Object.create(null);

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

        this.maxHorizSpeed = cfgNum(cfg, "maxHorizSpeed", DEFAULT_CFG.maxHorizSpeed);
        this.maxFallSpeed = cfgNum(cfg, "maxFallSpeed", DEFAULT_CFG.maxFallSpeed);

        this._coyote = 0;
        this._jumpBuf = 0;

        this._wishLocal = { x: 0, z: 0 };
        this._wishWorld = { x: 0, z: 0 };
        this._wishDir = { x: 0, z: 0 };

        this._stepUpCd = 0;
    }

    _raycastEx(frame, fx, fy, fz, tx, ty, tz, ignoreBodyId) {
        const PHYS = frame.physics;
        if (!PHYS || typeof PHYS.raycastEx !== "function") throw new Error("[move] frame.physics.raycastEx required");
        return PHYS.raycastEx({from: [fx, fy, fz], to: [tx, ty, tz], ignoreBodyId: ignoreBodyId | 0});
    }

    _tryStepUp(frame, body, cc, wishDirWorld, dt) {
        const su = cc.stepUp;
        if (!su || !su.enabled) return false;
        if (this._stepUpCd > 0) {
            this._stepUpCd = Math.max(0, this._stepUpCd - dt);
            return false;
        }

        const p = body.position();
        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

        const r = U.num(cc.radius, 0.35);
        const h = U.num(cc.height, 1.80);
        const footY = py - ((h * 0.5) - r);

        const ignoreId = (typeof body.id === "function") ? (body.id() | 0) : 0;

        const dirx = wishDirWorld.x;
        const dirz = wishDirWorld.z;

        const fwd = U.num(su.forwardProbe, 0.35);
        const up = U.num(su.upProbe, 0.60);
        const maxH = U.num(su.maxHeight, 0.40);
        const minNy = U.num(su.minClearNormalY, 0.25);

        if ((dirx * dirx + dirz * dirz) < 1e-8) return false;

        const probeX = px + dirx * (r + fwd);
        const probeZ = pz + dirz * (r + fwd);

        const yLow = footY + 0.05;
        const yHigh = footY + maxH;

        const hitLow = this._raycastEx(frame, px, yLow, pz, probeX, yLow, probeZ, ignoreId);
        if (!hitLow || hitLow.hit !== true) return false;

        const hitHigh = this._raycastEx(frame, px, yHigh, pz, probeX, yHigh, probeZ, ignoreId);
        if (hitHigh && hitHigh.hit === true) return false;

        const downFromY = footY + maxH + up;
        const downToY = footY - 0.10;

        const hitTop = this._raycastEx(frame, probeX, downFromY, probeZ, probeX, downToY, probeZ, ignoreId);
        if (!hitTop || hitTop.hit !== true) return false;

        const ny = hitTop.normal ? U.num(hitTop.normal.y, 1) : 1;
        if (ny < minNy) return false;

        const dist = U.num(hitTop.distance, NaN);
        if (!Number.isFinite(dist)) return false;

        const hitY = downFromY - dist;
        const targetFootY = hitY + 0.01;
        const targetCenterY = targetFootY + ((h * 0.5) - r);

        const dy = targetCenterY - py;
        if (dy <= 0 || dy > (maxH + 0.10)) return false;

        const ok = teleportBody(body, px, py + dy, pz);
        if (!ok) return false;

        this._stepUpCd = U.clamp(U.num(su.warpCooldown, 0.07), 0, 0.25);
        return true;
    }

    update(frame, body, characterCfg) {
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

        const cc = characterCfg || frame.character || null;
        const g = frame.ground;

        if (!jumpedThisTick && grounded && hasMove && cc && cc.stepUp && cc.stepUp.enabled) {
            const stepped = this._tryStepUp(frame, body, cc, this._wishWorld, dt);
            if (stepped) {
                frame.probeGroundCapsule(body, cc);
                frame.pose.grounded = !!frame.ground.grounded;
            }
        }

        const sd = cc && cc.stepDown ? cc.stepDown : null;
        const stickVel = sd ? U.num(sd.stickVel, 1.6) : 1.6;
        const stepDownMax = sd ? U.num(sd.max, 0.28) : 0.28;
        const deadZone = sd ? U.num(sd.deadZone, 0.01) : 0.01;
        const stepDownEnabled = sd ? !!sd.enabled : true;

        const allowStick = stepDownEnabled && grounded && !g.steep && !jumpedThisTick && vy <= 0;

        if (allowStick) {
            if (vy > -stickVel) vy = -stickVel;

            if (g.hasHit) {
                const fd = U.num(g.footDistance, 0);
                if (fd < -deadZone) {
                    const down = -fd;
                    if (down <= stepDownMax) {
                        const p = body.position();
                        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);
                        if (teleportBody(body, px, py + fd, pz)) vy = -stickVel;
                    }
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