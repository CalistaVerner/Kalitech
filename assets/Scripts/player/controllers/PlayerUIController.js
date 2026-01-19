// FILE: Scripts/player/controllers/PlayerUIController.js
"use strict";

const PlayerUI = require("../PlayerUI.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerUIController {
    constructor(player) {
        this.player = req(player, "[PlayerUIController] player is required");
        this.impl = null;
        this._started = false;
    }

    onStart() {
        if (this._started && this.impl) return;

        const pawn = req(this.player.pawn, "[PlayerUIController] player.pawn is required");
        const d = req(pawn.d, "[PlayerUIController] pawn.d is required");
        req(d.hud, "[PlayerUIController] domains.hud is required for PlayerUI");

        if (d.hudNative && typeof d.hudNative.setCursorEnabled === "function") {
            d.hudNative.setCursorEnabled(false, true);
        }

        this.impl = new PlayerUI(pawn).create();
        this._started = true;
    }

    onUpdate(dt) {
        if (!this.impl) this.onStart();
        if (!this.impl) throw new Error("[PlayerUIController] UI impl is null after onStart()");

        const pawn = req(this.player.pawn, "[PlayerUIController] player.pawn is required");

        this.impl.refresh();
        pawn.endFrame();

        void dt;
    }

    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") {
            try { this.impl.destroy(); } catch (_) {}
        }
        this.impl = null;
        this._started = false;
    }
}

module.exports = { PlayerUIController };