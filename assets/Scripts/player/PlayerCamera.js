"use strict";

const CameraOrchestrator = require("../Camera/CameraOrchestrator.js");

class PlayerCamera {
    constructor(player) {
        this.player = player;
        this.orch = null;
    }

    attach() {
        if (!this.orch) this.orch = new CameraOrchestrator(this.player);
    }

    getType() {
        return this.orch ? this.orch.getType() : "third";
    }

    getYaw() {
        return this.orch ? this.orch.look.yaw : 0;
    }

    getPitch() {
        return this.orch ? this.orch.look.pitch : 0;
    }

    update(frame) {
        if (this.orch) this.orch.update(frame.dt, frame);
    }

    destroy() {
        if (!this.orch) return;
        this.orch.destroy();
        this.orch = null;
    }
}

module.exports = PlayerCamera;