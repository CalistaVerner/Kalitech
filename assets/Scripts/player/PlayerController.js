// FILE: Scripts/player/PlayerController.js
"use strict";

const {EntityController} = require("../core/EntityController.js");
const MovementSystem = require("./systems/MovementSystem.js");
const ShootSystem = require("./systems/ShootSystem.js");

/**
 * PlayerController (UE6-style, hard OOP)
 *
 * Owns the gameplay loop for PlayerPawn:
 *  - beginFrame(dt)
 *  - syncPose()
 *  - movement.update(frame, characterCfg)
 *  - shoot.update(frame, ownerBodyId)
 *
 * Contract:
 *  - entity is PlayerPawn (has beginFrame/syncPose/frame/bodyId/characterCfg)
 *  - PlayerPawn.beginFrame MUST already fill frame.input as a STRUCT (InputRouter)
 */
class PlayerController extends EntityController {
    constructor() {
        super();
        this.movement = null;
        this.shoot = null;
    }

    onStart() {
        const pawn = this.entity;

        if (!pawn || typeof pawn.beginFrame !== "function") throw new Error("[PlayerController] entity must be PlayerPawn (beginFrame)");
        if (typeof pawn.syncPose !== "function") throw new Error("[PlayerController] entity must be PlayerPawn (syncPose)");
        if (!pawn.frame) throw new Error("[PlayerController] pawn.frame is required");
        if ((pawn.bodyId | 0) <= 0) throw new Error("[PlayerController] pawn.bodyId must be > 0");

        this.movement = new MovementSystem((pawn.cfg && pawn.cfg.movement) || null);
        this.shoot = new ShootSystem(pawn);
    }

    onUpdate(dt) {
        const pawn = this.entity;

        pawn.beginFrame(dt);
        pawn.syncPose();

        const frame = pawn.frame;

        // hard contract: frame.input is a state object, not InputApi
        if (!frame.input || typeof frame.input !== "object") {
            throw new Error("[PlayerController] frame.input must be an object (InputRouter state)");
        }

        this.movement.update(frame, pawn.characterCfg);
        this.shoot.update(frame, pawn.bodyId | 0);
    }

    onStop() {
        if (this.shoot && typeof this.shoot.destroy === "function") this.shoot.destroy();
        this.shoot = null;
        this.movement = null;
    }
}

module.exports = {PlayerController};