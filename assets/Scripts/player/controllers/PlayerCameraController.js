"use strict";

const PlayerCamera = require("../PlayerCamera.js");

class PlayerCameraController {
    constructor() {
        this.ctx = null;
        this.entity = null;
        this.impl = null;
    }

    bind(ctx, entity) {
        this.ctx = ctx;
        this.entity = entity;
        return this;
    }

    onStart() {
        this.impl = new PlayerCamera(this.entity);
        this.impl.attach();
    }

    onUpdate(dt) {
        const pawn = this.entity;

        this.impl.update(pawn.frame);

        pawn.frame.view.yaw = this.impl.getYaw();
        pawn.frame.view.pitch = this.impl.getPitch();
        pawn.frame.view.type = this.impl.getType();
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") this.impl.destroy();
        this.impl = null;
    }
}

module.exports = {PlayerCameraController};