// FILE: Scripts/player/PlayerCamera.js
"use strict";

const CameraOrchestrator = require("../Camera/CameraOrchestrator.js");

class PlayerCamera {
    constructor(player) {
        this.player = player;
        this.orch = null; // IMPORTANT: создаём оркестратор ТОЛЬКО после спавна модели
    }

    attach() {
        if (this.orch) return;
        this.orch = new CameraOrchestrator(this.player);
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
        if (!this.orch) return; // пока не attach — камера не работает
        this.orch.update(frame.dt, frame.snap);
    }

    destroy() {
        if (!this.orch) return;
        this.orch.destroy();
        this.orch = null;
    }
}

module.exports = PlayerCamera;