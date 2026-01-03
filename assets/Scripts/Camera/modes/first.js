"use strict";

const U = require("../camUtil.js");

class FirstPersonCameraMode {
    constructor() {
        this.id = "first";
        this.meta = {
            supportsZoom: false,
            hasCollision: false,
            numRays: 0,
            playerModelVisible: false
        };
        this.headOffset = { x: 0.0, y: 1.65, z: 0.0 };
    }

    update(ctx) {
        const p = ctx.bodyPos;

        const x = U.vx(p) + this.headOffset.x;
        const y = U.vy(p) + this.headOffset.y;
        const z = U.vz(p) + this.headOffset.z;

        ctx.outPos.x = x; ctx.outPos.y = y; ctx.outPos.z = z;
        ctx.target.x = U.vx(p); ctx.target.y = y; ctx.target.z = U.vz(p);
    }
}

module.exports = FirstPersonCameraMode;