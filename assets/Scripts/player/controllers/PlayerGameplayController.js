"use strict";

const MovementSystem = require("../systems/MovementSystem.js");
const ShootSystem = require("../systems/ShootSystem.js");

/**
 * PlayerGameplayController (module, no extends)
 *
 * Owns gameplay loop for PlayerPawn:
 *  - beginFrame(dt)
 *  - syncPose()
 *  - movement.update(frame, characterCfg)
 *  - shoot.update(frame, ownerBodyId)
 */
class PlayerGameplayController {
    constructor() {
        this.ctx = null;
        this.entity = null;

        this.movement = null;
        this.shoot = null;
    }

    bind(ctx, entity) {
        this.ctx = ctx;
        this.entity = entity;
        return this;
    }

    onStart() {
        const pawn = this.entity;

        if (!pawn || typeof pawn.beginFrame !== "function") throw new Error("[PlayerGameplay] entity must be PlayerPawn (beginFrame)");
        if (typeof pawn.syncPose !== "function") throw new Error("[PlayerGameplay] entity must be PlayerPawn (syncPose)");
        if (!pawn.frame) throw new Error("[PlayerGameplay] pawn.frame is required");
        if ((pawn.bodyId | 0) <= 0) throw new Error("[PlayerGameplay] pawn.bodyId must be > 0");

        this.movement = new MovementSystem((pawn.cfg && pawn.cfg.movement) || null);
        this.shoot = new ShootSystem(pawn);
    }

    onUpdate(dt) {
        const pawn = this.entity;

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
        if (this.shoot && typeof this.shoot.destroy === "function") this.shoot.destroy();
        this.shoot = null;
        this.movement = null;
    }
}

module.exports = {PlayerGameplayController};
