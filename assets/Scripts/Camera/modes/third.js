"use strict";

const U = require("../camUtil.js");

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";
        this.meta = {supportsZoom: true, hasCollision: true, numRays: 8, playerModelVisible: true};

        this.pivotOffset = {x: 0.0, y: 1.45, z: 0.0};
        this.shoulderX = 0.35;
        this.verticalLift = 0.15;

        this.pivotSmooth = 24.0;

        this._pivot = {x: 0, y: 0, z: 0};
        this._init = false;
    }

    update(ctx) {
        const p = ctx.bodyPos;
        const zo = ctx.zoneOverrides;

        const po = (zo && zo.pivotOffset) ? zo.pivotOffset : this.pivotOffset;
        const shoulder = (zo && zo.shoulderX != null) ? +zo.shoulderX : this.shoulderX;
        const lift = (zo && zo.verticalLift != null) ? +zo.verticalLift : this.verticalLift;

        const rawPx = U.vx(p, 0) + po.x + shoulder;
        const rawPy = U.vy(p, 0) + po.y;
        const rawPz = U.vz(p, 0) + po.z;

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

        const yaw = ctx.look.yaw || 0;
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);
        const dist = ctx.zoom.value();

        ctx.outPos.x = this._pivot.x - sin * dist;
        ctx.outPos.y = this._pivot.y + lift;
        ctx.outPos.z = this._pivot.z - cos * dist;
    }
}

module.exports = ThirdPersonCameraMode;