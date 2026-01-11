// FILE: Scripts/player/modes/third.js
"use strict";

const U = require("../camUtil.js");

function clamp(v, lo, hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";
        this.meta = {supportsZoom: true, hasCollision: true, numRays: 8, playerModelVisible: true};

        // base pivot relative to player body
        this.pivotOffset = {x: 0.0, y: 1.45, z: 0.0};

        // shoulder offset (applied along camera "right" by yaw)
        this.shoulderX = 0.35;

        // extra lift on top of orbit (small)
        this.verticalLift = 0.15;

        // orbit pitch contribution scale
        this.pitchOrbitScale = 1.0;

        // smoothing for pivot follow
        this.pivotSmooth = 24.0;

        // ------------------------------------------------------------
        // Collision control for THIRD PERSON (published as overrides)
        // ------------------------------------------------------------
        this.collisionEnabled = true;

        // "pear" volume defaults (camera corridor)
        this.camRadius = 0.28;     // far end radius (camera end)
        this.nearRadius = 0.06;    // near end radius (attachment end)
        this.pearK = 1.9;
        this.pearSamples = 9;

        // obstacle tuning
        this.surfacePadding = 0.08;
        this.obstaclePasses = 2;

        // ground / terrain safety
        this.useTerrainHeight = true;
        this.terrainWorld = true;

        this.floorPadding = 0.22;
        this.slopePadScale = 0.45;

        this.groundRayLift = 1.25;
        this.maxRayLenDown = 12.0;
        this.groundSnapPen = 0.60;

        // dynamic scaling by zoom distance (AAA feel)
        // radius grows a bit when zoomed out -> prevents "thin camera" clipping/terrain dive.
        this.zoomRadiusBoost = 0.10;      // + up to this fraction on camRadius
        this.zoomNearBoost = 0.06;        // + up to this fraction on nearRadius
        this.zoomFloorBoost = 0.20;       // + up to this fraction on floorPadding
        this.zoomBoostStart = 6.0;        // start boosting after this distance
        this.zoomBoostFull = 26.0;        // full boost at this distance

        // debug helpers (optional)
        this.debugCapsule = true;
        this.debugGroundCapsule = true;

        this._pivot = {x: 0, y: 0, z: 0};
        this._init = false;
    }

    _applyCollisionOverrides(ctx, dist) {
        // Use zoneOverrides if present, else create one.
        // We DO mutate the object, because it's typically a per-frame blended object.
        // This lets CameraCollisionSolver read overrides without changes.
        let zo = ctx.zoneOverrides;
        if (!zo) {
            zo = {};
            ctx.zoneOverrides = zo;
        }

        // Allow zones to disable collision; third-mode can also disable if needed.
        // Priority: if zone explicitly disables -> always off.
        if (zo.collisionEnabled === false) {
            zo.collisionEnabled = false;
            return;
        }
        zo.collisionEnabled = !!this.collisionEnabled;

        // compute zoom boost factor 0..1
        const z0 = this.zoomBoostStart;
        const z1 = Math.max(z0 + 0.001, this.zoomBoostFull);
        const k = clamp((dist - z0) / (z1 - z0), 0, 1);

        // boosted pear radii / floor pad
        const camR = this.camRadius * (1.0 + this.zoomRadiusBoost * k);
        const nearR = this.nearRadius * (1.0 + this.zoomNearBoost * k);
        const floorPad = this.floorPadding * (1.0 + this.zoomFloorBoost * k);

        // publish overrides for CameraCollisionSolver
        zo.camRadius = camR;
        zo.nearRadius = nearR;
        zo.pearK = this.pearK;
        zo.pearSamples = this.pearSamples;

        zo.surfacePadding = this.surfacePadding;
        zo.obstaclePasses = this.obstaclePasses;

        zo.useTerrainHeight = !!this.useTerrainHeight;
        zo.terrainWorld = !!this.terrainWorld;

        zo.floorPadding = floorPad;
        zo.slopePadScale = this.slopePadScale;

        zo.groundRayLift = this.groundRayLift;
        zo.maxRayLenDown = this.maxRayLenDown;
        zo.groundSnapPen = this.groundSnapPen;

        // debug toggles for collision solver (if it supports them)
        zo.debugCapsule = !!this.debugCapsule;
        zo.debugGroundCapsule = !!this.debugGroundCapsule;
    }

    update(ctx) {
        const p = ctx.bodyPos;
        const zo = ctx.zoneOverrides;

        const po = (zo && zo.pivotOffset) ? zo.pivotOffset : this.pivotOffset;
        const shoulder = (zo && zo.shoulderX != null) ? +zo.shoulderX : this.shoulderX;
        const lift = (zo && zo.verticalLift != null) ? +zo.verticalLift : this.verticalLift;

        const yaw = +ctx.look.yaw || 0;
        const pitch = +ctx.look.pitch || 0;

        // camera basis from yaw (flat)
        const sinY = Math.sin(yaw);
        const cosY = Math.cos(yaw);

        // right = (cosY, 0, -sinY)
        const rx = cosY, rz = -sinY;

        // raw pivot in world
        const basePx = U.vx(p, 0) + po.x;
        const basePy = U.vy(p, 0) + po.y;
        const basePz = U.vz(p, 0) + po.z;

        // apply shoulder along RIGHT
        const rawPx = basePx + rx * shoulder;
        const rawPy = basePy;
        const rawPz = basePz + rz * shoulder;

        // smooth pivot follow
        if (!this._init) {
            this._init = true;
            this._pivot.x = rawPx;
            this._pivot.y = rawPy;
            this._pivot.z = rawPz;
        } else {
            const dt = ctx.dt;
            this._pivot.x = U.expSmooth(this._pivot.x, rawPx, this.pivotSmooth, dt);
            this._pivot.y = U.expSmooth(this._pivot.y, rawPy, this.pivotSmooth, dt);
            this._pivot.z = U.expSmooth(this._pivot.z, rawPz, this.pivotSmooth, dt);
        }

        ctx.target.x = this._pivot.x;
        ctx.target.y = this._pivot.y;
        ctx.target.z = this._pivot.z;

        // orbit by yaw + pitch
        const dist = ctx.zoom.value();

        // Publish collision controls for THIS camera view
        // (so solver gets "pear" and ground safety tuned for third-person)
        this._applyCollisionOverrides(ctx, dist);

        const cp = Math.cos(pitch * this.pitchOrbitScale);
        const sp = Math.sin(pitch * this.pitchOrbitScale);

        const horiz = dist * cp;

        // place camera behind the target (opposite forward)
        ctx.outPos.x = this._pivot.x - sinY * horiz;
        ctx.outPos.z = this._pivot.z - cosY * horiz;

        // pitch contributes to vertical orbit
        ctx.outPos.y = this._pivot.y + lift + sp * dist;
    }
}

module.exports = ThirdPersonCameraMode;