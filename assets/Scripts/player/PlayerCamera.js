// FILE: Scripts/player/PlayerCamera.js
"use strict";

const CameraOrchestrator = require("../Camera/CameraOrchestrator.js");

class PlayerCamera {
    constructor(player) {
        this.orch = new CameraOrchestrator(player);
    }

    setType(type) {
        //this.orch.setType(type);
        }
    getType() { return this.orch.getType(); }

    getYaw() { return this.orch.look.yaw; }
    getPitch() { return this.orch.look.pitch; }

    update(frame) { this.orch.update(frame.dt, frame.snap); }

    destroy() { }
}

module.exports = PlayerCamera;