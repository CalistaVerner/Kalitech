"use strict";

function requireEngineCamera() {
    const E = globalThis.ENGINE;
    if (!E) throw new Error("[PlayerCamera] global ENGINE is not available");

    const cam = E.camera;
    if (!cam) throw new Error("[PlayerCamera] ENGINE.camera is not registered (camera module missing in manifest)");
    if (typeof cam.createOrchestrator !== "function") {
        throw new Error("[PlayerCamera] ENGINE.camera.createOrchestrator(player) is required");
    }
    return cam;
}

class PlayerCamera {
    constructor(player) {
        if (!player) throw new Error("[PlayerCamera] player is required");
        this.player = player;
        this.orch = null;
    }

    attach() {
        if (this.orch) return;
        const cam = requireEngineCamera();
        this.orch = cam.createOrchestrator(this.player);
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
        if (this.orch && frame) this.orch.update(frame.dt, frame);
    }

    destroy() {
        if (!this.orch) return;
        this.orch.destroy();
        this.orch = null;
    }
}

module.exports = PlayerCamera;