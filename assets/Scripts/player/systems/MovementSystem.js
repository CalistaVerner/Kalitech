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

function canTeleport(bodyAccess) {
    return !!(bodyAccess && typeof bodyAccess.teleport === "function");
}

function teleportBody(bodyAccess, x, y, z) {
    bodyAccess.teleport(x, y, z);
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
        return PHYS.raycastEx({
            from: [fx, fy, fz],
            to: [tx, ty, tz],
            ignoreBodyId: ignoreBodyId | 0
        });
    }

    _tryStepUp(frame, bodyAccess, cc, wishDirWorld, dt) {
        const su = cc.stepUp;
        if (!su || !su.enabled) return false;
        if (!canTeleport(bodyAccess)) return false;

        if (this._stepUpCd > 0) {
            this._stepUpCd = Math.max(0, this._stepUpCd - dt);
            return false;
        }

        const dirx = wishDirWorld.x;
        const dirz = wishDirWorld.z;
        if ((dirx * dirx + dirz * dirz) < 1e-8) return false;

        const p = bodyAccess.position();
        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

        const r = U.num(cc.radius, 0.35);
        const h = U.num(cc.height, 1.80);
        const footY = py - ((h * 0.5) - r);

        const ignoreId = (frame.bodyId | 0) || 0;

        const fwd = U.num(su.forwardProbe, 0.35);
        const up = U.num(su.upProbe, 0.60);
        const maxH = U.num(su.maxHeight, 0.40);
        const minH = Math.max(0, U.num(su.minHeight, 0.04));
        const minNy = U.num(su.minClearNormalY, 0.25);

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
        if (dy < minH) return false;

        teleportBody(bodyAccess, px, py + dy, pz);

        this._stepUpCd = U.clamp(U.num(su.warpCooldown, 0.07), 0, 0.25);
        return true;
    }

    update(frame, characterCfg) {
        if (!this.enabled) return;

        const body = frame.bodyAccess;
        if (!body) throw new Error("[move] frame.bodyAccess required");

        const dt = U.clamp(U.num(frame.dt, 1 / 60), 0, 0.05);

        const input = frame.input;
        const yaw = U.num(frame.view.yaw, 0);

        const grounded = !!frame.pose.grounded;

        this._coyote = grounded ? this.coyoteTime : Math.max(0, this._coyote - dt);
        this._jumpBuf = input.jump ? this.jumpBuffer : Math.max(0, this._jumpBuf - dt);

        const v0 = body.getVel();
        let vx = U.vx(v0, 0);
        let vy = U.vy(v0, 0);
        let vz = U.vz(v0, 0);

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

        const cc = characterCfg || frame.character;
        const g = frame.ground;

        if (!jumpedThisTick && grounded && hasMove && cc && cc.stepUp && cc.stepUp.enabled) {
            const stepped = this._tryStepUp(frame, body, cc, this._wishWorld, dt);
            if (stepped) {
                const probe = frame.probeGroundCapsule;
                if (typeof probe === "function") {
                    if (probe.length >= 3) probe.call(frame, body, cc, frame.bodyId | 0);
                    else probe.call(frame, body, cc);
                    frame.pose.grounded = !!frame.ground.grounded;
                }
            }
        }

        const sd = cc && cc.stepDown ? cc.stepDown : null;
        const stepDownEnabled = sd ? !!sd.enabled : true;
        const stickVel = sd ? U.num(sd.stickVel, 1.6) : 1.6;
        const stepDownMax = sd ? U.num(sd.max, 0.28) : 0.28;
        const deadZone = sd ? U.num(sd.deadZone, 0.015) : 0.015;

        const allowStick = stepDownEnabled && grounded && !g.steep && !jumpedThisTick && vy <= 0 && g.hasHit;

        if (allowStick) {
            if (vy > -stickVel) vy = -stickVel;

            const fd = U.num(g.footDistance, 0);
            if (fd < -deadZone && canTeleport(body)) {
                const down = -fd;
                if (down <= stepDownMax) {
                    const p = body.position();
                    const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);
                    teleportBody(body, px, py + fd, pz);
                    vy = -stickVel;
                }
            }
        }

        if (body.mode === "SET_VEL") {
            body.setVel({x: vx, y: vy, z: vz});
        } else {
            const cur = body.getVel();
            const cvx = U.vx(cur, 0), cvy = U.vy(cur, 0), cvz = U.vz(cur, 0);

            const m = (cc && cc.mass != null) ? U.num(cc.mass, 80) : 80;

            const ix = (vx - cvx) * m;
            const iy = (vy - cvy) * m;
            const iz = (vz - cvz) * m;

            body.applyImpulse(ix, iy, iz);
        }

        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;
        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = (vy < 0) ? -vy : 0;
    }
}

module.exports = MovementSystem;
