// FILE: Scripts/player/controllers/PlayerGameplayController.js
"use strict";

const MovementSystem = require("../systems/MovementSystem.js");
const ShootSystem = require("../systems/ShootSystem.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

/**
 * PlayerGameplayController
 *
 * Owns gameplay loop for PlayerPawn:
 *  - beginFrame(dt)
 *  - syncPose()
 *  - movement.update(frame, characterCfg)
 *  - shoot.update(frame, ownerBodyId)
 */
class PlayerGameplayController {
    constructor(player) {
        this.player = req(player, "[PlayerGameplayController] player is required");
        this.movement = null;
        this.shoot = null;
    }

    onStart() {
        const pawn = req(this.player.pawn, "[PlayerGameplay] player.pawn is required");

        if (typeof pawn.beginFrame !== "function") throw new Error("[PlayerGameplay] pawn.beginFrame required");
        if (typeof pawn.syncPose !== "function") throw new Error("[PlayerGameplay] pawn.syncPose required");
        if (!pawn.frame) throw new Error("[PlayerGameplay] pawn.frame is required");
        if ((pawn.bodyId | 0) <= 0) throw new Error("[PlayerGameplay] pawn.bodyId must be > 0");

        this.movement = new MovementSystem((pawn.cfg && pawn.cfg.movement) || null);
        this.shoot = new ShootSystem(pawn);
    }

    onUpdate(dt) {
        const pawn = req(this.player.pawn, "[PlayerGameplay] player.pawn is required");

        pawn.beginFrame(dt);
        pawn.syncPose();

        const frame = pawn.frame;
        if (!frame.input || typeof frame.input !== "object") {
            throw new Error("[PlayerGameplay] frame.input must be an object (InputRouter state)");
        }

        this.movement.update(frame, pawn.characterCfg);
        this.shoot.update(frame, pawn.bodyId | 0);
    }

    onStop() {
        if (this.shoot && typeof this.shoot.destroy === "function") {
            try { this.shoot.destroy(); } catch (_) {}
        }
        this.shoot = null;
        this.movement = null;
    }
}

module.exports = { PlayerGameplayController };