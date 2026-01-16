// FILE: Scripts/player/modes/third.js
"use strict";

const U = require("../camUtil.js");

function clamp(v, lo, hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

function smoothstep(t) {
    t = clamp(t, 0, 1);
    return t * t * (3 - 2 * t);
}

function expAlpha(rate, dt) {
    return 1.0 - Math.exp(-Math.max(0, rate) * Math.max(0, dt));
}

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";
        this.meta = {supportsZoom: true, hasCollision: true, numRays: 8, playerModelVisible: true};

        // CP2077-style framing (over-the-shoulder with soft recenter)
        this.pivotOffset = {x: 0.0, y: 1.46, z: 0.0};

        this.shoulderX = 0.42;
        this.shoulderAimX = 0.26;
        this.verticalLift = 0.14;

        this.forwardLead = 0.28;
        this.forwardLeadAim = 0.08;
        this.leadSmooth = 12.0;

        this.pivotSmoothPos = 22.0;
        this.pivotSmoothY = 26.0;

        this.camSmoothPos = 18.0;
        this.camSmoothY = 20.0;

        this.recenterEnabled = true;
        this.recenterRate = 2.2;
        this.recenterDeadZone = 0.38;
        this.recenterMax = 1.25;

        this.pitchOrbitScale = 1.0;
        this.pitchMin = -1.15;
        this.pitchMax = 0.90;
        this.pitchSoft = 0.18;

        // Collision tuning (pear corridor)
        this.collisionEnabled = true;

        this.camRadius = 0.28;
        this.nearRadius = 0.06;
        this.pearK = 1.9;
        this.pearSamples = 9;

        this.surfacePadding = 0.08;
        this.obstaclePasses = 2;

        this.useTerrainHeight = true;
        this.terrainWorld = true;

        this.floorPadding = 0.22;
        this.slopePadScale = 0.45;

        this.groundRayLift = 1.25;
        this.maxRayLenDown = 12.0;
        this.groundSnapPen = 0.60;

        this.zoomRadiusBoost = 0.10;
        this.zoomNearBoost = 0.06;
        this.zoomFloorBoost = 0.20;
        this.zoomBoostStart = 6.0;
        this.zoomBoostFull = 26.0;

        this.debugCapsule = true;
        this.debugGroundCapsule = true;

        this._init = false;

        this._pivot = {x: 0, y: 0, z: 0};
        this._cam = {x: 0, y: 0, z: 0};
        this._lead = {x: 0, y: 0, z: 0};

        this._shoulderSide = +1;
    }

    _ensureModeConfig(ctx) {
        let mc = ctx.modeConfig;
        if (!mc) {
            mc = {};
            ctx.modeConfig = mc;
        }
        return mc;
    }

    _applyCollisionOverrides(ctx, dist) {
        // Publish collision tuning as modeConfig (baseline), zones will blend over it later.
        const mc = this._ensureModeConfig(ctx);

        // If a volume zone explicitly disables collision, respect it by not overriding.
        const zoRaw = ctx.zoneOverridesRaw;
        if (zoRaw && zoRaw.collisionEnabled === false) {
            mc.collisionEnabled = false;
            return;
        }

        mc.collisionEnabled = !!this.collisionEnabled;

        const z0 = this.zoomBoostStart;
        const z1 = Math.max(z0 + 0.001, this.zoomBoostFull);
        const k = clamp((dist - z0) / (z1 - z0), 0, 1);

        const camR = this.camRadius * (1.0 + this.zoomRadiusBoost * k);
        const nearR = this.nearRadius * (1.0 + this.zoomNearBoost * k);
        const floorPad = this.floorPadding * (1.0 + this.zoomFloorBoost * k);

        mc.camRadius = camR;
        mc.nearRadius = nearR;
        mc.pearK = this.pearK;
        mc.pearSamples = this.pearSamples;

        mc.surfacePadding = this.surfacePadding;
        mc.obstaclePasses = this.obstaclePasses;

        mc.useTerrainHeight = !!this.useTerrainHeight;
        mc.terrainWorld = !!this.terrainWorld;

        mc.floorPadding = floorPad;
        mc.slopePadScale = this.slopePadScale;

        mc.groundRayLift = this.groundRayLift;
        mc.maxRayLenDown = this.maxRayLenDown;
        mc.groundSnapPen = this.groundSnapPen;

        mc.debugCapsule = !!this.debugCapsule;
        mc.debugGroundCapsule = !!this.debugGroundCapsule;
    }

    _readMove(ctx) {
        const mv = ctx.moveDir || ctx.move || ctx.motion || null;
        const vel = ctx.bodyVel || ctx.vel || null;

        let dx = 0, dz = 0, sp = 0;

        if (mv) {
            dx = +U.vx(mv, 0) || 0;
            dz = +U.vz(mv, 0) || 0;
            const len = Math.hypot(dx, dz);
            if (len > 1e-6) {
                dx /= len;
                dz /= len;
            }
            sp = clamp(+mv.speed || +mv.mag || +mv.length || 0, 0, 1);
        } else if (vel) {
            const vx = +U.vx(vel, 0) || 0;
            const vz = +U.vz(vel, 0) || 0;
            const len = Math.hypot(vx, vz);
            if (len > 1e-6) {
                dx = vx / len;
                dz = vz / len;
            }
            sp = clamp(len / 6.0, 0, 1);
        }

        return {dx, dz, sp};
    }

    _softClampPitch(p) {
        const lo = this.pitchMin, hi = this.pitchMax;
        p = clamp(p, lo - 0.35, hi + 0.35);

        if (p < lo) {
            const t = clamp((lo - p) / Math.max(1e-6, this.pitchSoft), 0, 1);
            p = lo - (1.0 - smoothstep(1.0 - t)) * this.pitchSoft;
        } else if (p > hi) {
            const t = clamp((p - hi) / Math.max(1e-6, this.pitchSoft), 0, 1);
            p = hi + (1.0 - smoothstep(1.0 - t)) * this.pitchSoft;
        }
        return clamp(p, lo, hi);
    }

    _updateShoulderSide(ctx) {
        const inp = ctx.input || null;
        const swap = !!(ctx.shoulderSwap || (inp && inp.shoulderSwap));
        if (swap && !this._swapHeld) {
            this._shoulderSide = -this._shoulderSide;
            this._swapHeld = true;
        } else if (!swap) {
            this._swapHeld = false;
        }

        const zo = ctx.zoneOverrides;
        if (zo && (zo.shoulderSide === -1 || zo.shoulderSide === 1)) {
            this._shoulderSide = zo.shoulderSide;
        }
    }

    update(ctx) {
        const dt = Math.max(0, +ctx.dt || 0);

        const p = ctx.bodyPos;
        // Pre-mode zone-only overrides are exposed as ctx.zoneOverrides.
        const zo = ctx.zoneOverrides;

        const isAiming = !!(ctx.aiming || (ctx.input && ctx.input.aiming));

        const po = (zo && zo.pivotOffset) ? zo.pivotOffset : this.pivotOffset;

        const baseShoulder = (zo && zo.shoulderX != null) ? +zo.shoulderX : this.shoulderX;
        const aimShoulder = (zo && zo.shoulderAimX != null) ? +zo.shoulderAimX : this.shoulderAimX;
        const lift = (zo && zo.verticalLift != null) ? +zo.verticalLift : this.verticalLift;

        this._updateShoulderSide(ctx);

        let yaw = +ctx.look.yaw || 0;
        let pitch = +ctx.look.pitch || 0;

        pitch = this._softClampPitch(pitch);

        if (this.recenterEnabled) {
            const mv = this._readMove(ctx);
            if (mv.sp > 0.08) {
                const moveYaw = Math.atan2(mv.dx, mv.dz);
                let dy = yaw - moveYaw;
                while (dy > Math.PI) dy -= Math.PI * 2;
                while (dy < -Math.PI) dy += Math.PI * 2;

                const abs = Math.abs(dy);
                if (abs > this.recenterDeadZone) {
                    const over = clamp(abs - this.recenterDeadZone, 0, this.recenterMax);
                    const pull = smoothstep(over / Math.max(1e-6, this.recenterMax));
                    const a = expAlpha(this.recenterRate * (0.35 + 0.65 * mv.sp), dt) * pull;
                    yaw = lerp(yaw, moveYaw, a);
                }
            }
        }

        const sinY = Math.sin(yaw);
        const cosY = Math.cos(yaw);

        const rx = cosY, rz = -sinY;
        const fx = sinY, fz = cosY;

        const basePx = U.vx(p, 0) + po.x;
        const basePy = U.vy(p, 0) + po.y;
        const basePz = U.vz(p, 0) + po.z;

        const mv = this._readMove(ctx);
        const leadMax = isAiming ? this.forwardLeadAim : this.forwardLead;
        const leadK = leadMax * mv.sp;

        const leadAx = expAlpha(this.leadSmooth, dt);
        const desiredLeadX = (mv.sp > 1e-4) ? mv.dx * leadK : fx * (leadK * 0.35);
        const desiredLeadZ = (mv.sp > 1e-4) ? mv.dz * leadK : fz * (leadK * 0.35);

        if (!this._init) {
            this._lead.x = desiredLeadX;
            this._lead.z = desiredLeadZ;
        } else {
            this._lead.x = lerp(this._lead.x, desiredLeadX, leadAx);
            this._lead.z = lerp(this._lead.z, desiredLeadZ, leadAx);
        }

        const aimBlend = isAiming ? 1.0 : 0.0;
        const shoulder = lerp(baseShoulder, aimShoulder, aimBlend) * this._shoulderSide;

        const rawPx = basePx + rx * shoulder + this._lead.x;
        const rawPy = basePy;
        const rawPz = basePz + rz * shoulder + this._lead.z;

        if (!this._init) {
            this._init = true;
            this._pivot.x = rawPx;
            this._pivot.y = rawPy;
            this._pivot.z = rawPz;

            this._cam.x = rawPx;
            this._cam.y = rawPy + lift;
            this._cam.z = rawPz;
        } else {
            const ax = expAlpha(this.pivotSmoothPos, dt);
            const ay = expAlpha(this.pivotSmoothY, dt);

            this._pivot.x = lerp(this._pivot.x, rawPx, ax);
            this._pivot.y = lerp(this._pivot.y, rawPy, ay);
            this._pivot.z = lerp(this._pivot.z, rawPz, ax);
        }

        ctx.target.x = this._pivot.x;
        ctx.target.y = this._pivot.y;
        ctx.target.z = this._pivot.z;

        const dist = Math.max(0.05, +ctx.zoom.value());
        this._applyCollisionOverrides(ctx, dist);

        const p2 = pitch * this.pitchOrbitScale;
        const cp = Math.cos(p2);
        const sp = Math.sin(p2);

        const horiz = dist * cp;

        const outX = this._pivot.x - sinY * horiz;
        const outZ = this._pivot.z - cosY * horiz;
        const outY = this._pivot.y + lift + sp * dist;

        const cx = expAlpha(this.camSmoothPos, dt);
        const cy = expAlpha(this.camSmoothY, dt);

        this._cam.x = lerp(this._cam.x, outX, cx);
        this._cam.y = lerp(this._cam.y, outY, cy);
        this._cam.z = lerp(this._cam.z, outZ, cx);

        ctx.outPos.x = this._cam.x;
        ctx.outPos.y = this._cam.y;
        ctx.outPos.z = this._cam.z;

        if (ctx.outLook) {
            ctx.outLook.yaw = yaw;
            ctx.outLook.pitch = pitch;
        }
    }
}

module.exports = ThirdPersonCameraMode;