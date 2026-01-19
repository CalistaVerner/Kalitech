// FILE: Scripts/player/controllers/PlayerCameraController.js
"use strict";

const PlayerCamera = require("../PlayerCamera.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerCameraController {
    constructor(player) {
        this.player = req(player, "[PlayerCameraController] player is required");
        this.impl = null;
    }

    onStart() {
        const pawn = req(this.player.pawn, "[PlayerCameraController] player.pawn is required");
        this.impl = new PlayerCamera(pawn);
        this.impl.attach();
    }

    onUpdate(dt) {
        const pawn = req(this.player.pawn, "[PlayerCameraController] player.pawn is required");
        const impl = req(this.impl, "[PlayerCameraController] impl is null");

        impl.update(pawn.frame);

        pawn.frame.view.yaw = impl.getYaw();
        pawn.frame.view.pitch = impl.getPitch();
        pawn.frame.view.type = impl.getType();

        void dt;
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") {
            try { this.impl.destroy(); } catch (_) {}
        }
        this.impl = null;
    }
}

module.exports = { PlayerCameraController };