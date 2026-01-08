// FILE: Scripts/player/controllers/PlayerCameraController.js
"use strict";

const {EntityController} = require("../../core/controller/EntityController.js");
const PlayerCamera = require("../PlayerCamera.js");

class PlayerCameraController extends EntityController {
    constructor() {
        super();
        this.impl = null;
    }

    onStart() {
        this.impl = new PlayerCamera(this.entity); // PlayerPawn
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