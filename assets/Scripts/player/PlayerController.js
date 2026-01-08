// FILE: Scripts/player/PlayerController.js
"use strict";

const {PlayerPawn} = require("./PlayerPawn.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerController {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[PlayerController] ctx is required");
        this.cfg = cfg || null;

        // create pawn
        this.pawn = new PlayerPawn(this.ctx, this.cfg);
        if (this.pawn && typeof this.pawn.init === "function") this.pawn.init();

        // entity controller via engine controllers
        const ENGINE = globalThis.ENGINE;
        if (!ENGINE || !ENGINE.controllers) throw new Error("[PlayerController] ENGINE.controllers is not available");
        this.ec = ENGINE.controllers.entity("player", this.ctx, this.pawn, this.cfg);
    }

    update(dt) {
        if (this.ec) this.ec.update(dt);
    }

    dispose() {
        if (this.ec) this.ec.dispose();
        this.ec = null;

        if (this.pawn && typeof this.pawn.destroy === "function") this.pawn.destroy();
        this.pawn = null;

        this.ctx = null;
        this.cfg = null;
    }
}

module.exports = {PlayerController};