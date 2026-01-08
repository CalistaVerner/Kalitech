"use strict";

const {EntityControllerLink} = require("../core/controller/EntityControllerLink.js");
const {ControllerStack} = require("../core/controller/ControllerStack.js");

const {PlayerPawn} = require("./PlayerPawn.js");

const {PlayerEventsController} = require("./controllers/PlayerEventsController.js");
const {PlayerGameplayController} = require("./controllers/PlayerGameplayController.js");
const {PlayerCameraController} = require("./controllers/PlayerCameraController.js");
const {PlayerUIController} = require("./controllers/PlayerUIController.js");

/**
 * PlayerController (ROOT, UE6-style binder)
 *
 * - Creates PlayerPawn
 * - Creates controller stack (modules)
 * - Binds via EntityControllerLink
 *
 * This is the only place that "knows everything".
 * No legacy. No extends. No scattered ctx.
 */
class PlayerController {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || null;

        this.pawn = null;
        this.stack = null;
        this.link = null;

        this._alive = false;
    }

    init() {
        if (this._alive) return this;

        this.pawn = new PlayerPawn(this.ctx, this.cfg).init();

        this.stack = new ControllerStack([
            new PlayerEventsController(),
            new PlayerGameplayController(),
            new PlayerCameraController(),
            new PlayerUIController()
        ]);

        this.link = new EntityControllerLink(this.ctx, this.pawn, this.stack);

        this._alive = true;
        return this;
    }

    update(dt) {
        if (!this._alive) return;
        this.link.update(dt);
    }

    dispose() {
        if (!this._alive) return;

        this.link.dispose();
        this.link = null;

        this.stack = null;

        this.pawn.destroy();
        this.pawn = null;

        this._alive = false;
    }
}

module.exports = {PlayerController};