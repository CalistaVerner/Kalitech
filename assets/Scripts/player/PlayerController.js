// FILE: Scripts/player/PlayerController.js
"use strict";

const {ControllerStack} = require("../core/controller/ControllerStack.js");
const {PlayerPawn} = require("./PlayerPawn.js");
const {createPlayerRegistry} = require("./PlayerControllers.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerController {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[PlayerController] ctx is required");
        this.cfg = cfg || null;

        this.pawn = new PlayerPawn(this.ctx, this.cfg).init();

        // registry -> stack -> bind to pawn
        this.registry = createPlayerRegistry();
        this.stack = ControllerStack.fromRegistry(this.registry, this.ctx, this.pawn, this.cfg);
    }

    update(dt) {
        this.stack._tick(dt);
    }

    dispose() {
        if (this.stack) this.stack._shutdown();
        this.stack = null;
        this.registry = null;

        if (this.pawn) this.pawn.destroy();
        this.pawn = null;
        this.ctx = null;
        this.cfg = null;
    }
}

module.exports = {PlayerController};