"use strict";

const U = require("../camUtil.js");

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";
        this.meta = {
            supportsZoom: true,
            hasCollision: true,
            numRays: 6,
            playerModelVisible: true
        };

        this.pivotOffset = { x: 0.0, y: 1.2, z: 0.0 };
        this.height = 0.4;
    }

    update(ctx) {
        const p = ctx.bodyPos;

        const px = U.vx(p) + this.pivotOffset.x;
        const py = U.vy(p) + this.pivotOffset.y;
        const pz = U.vz(p) + this.pivotOffset.z;

        const yaw = ctx.look.yaw || 0;
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const dist = ctx.zoom.value();

        ctx.outPos.x = px - sin * dist;
        ctx.outPos.y = py + this.height;
        ctx.outPos.z = pz - cos * dist;

        ctx.target.x = px; ctx.target.y = py; ctx.target.z = pz;
    }
}

module.exports = ThirdPersonCameraMode;