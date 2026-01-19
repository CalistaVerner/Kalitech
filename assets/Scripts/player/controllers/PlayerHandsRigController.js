// FILE: Scripts/player/controllers/PlayerHandsRigController.js
"use strict";

const { PlayerHandsRig } = require("../PlayerHandsRig.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

/**
 * PlayerHandsRigController
 *
 * No bind-magic. Receives player instance in constructor.
 */
class PlayerHandsRigController {
    constructor(player) {
        this.player = req(player, "[PlayerHandsRigController] player is required");
        this._started = false;
        this._rig = null;
    }

    onStart() {
        if (this._started) return;

        const player = this.player;
        const pawn = req(player.pawn, "[PlayerHandsRigController] player.pawn is required");

        this._rig = new PlayerHandsRig(pawn).bind();
        pawn.handsRig = this._rig;

        this._started = true;
    }

    onUpdate(dt) {
        if (!this._started) this.onStart();
        void dt;
    }

    onStop() {
        const player = this.player;
        const pawn = player ? player.pawn : null;

        if (pawn && pawn.handsRig === this._rig) pawn.handsRig = null;

        if (this._rig && typeof this._rig.destroy === "function") {
            try { this._rig.destroy(); } catch (_) {}
        }

        this._rig = null;
        this._started = false;
    }
}

module.exports = { PlayerHandsRigController };