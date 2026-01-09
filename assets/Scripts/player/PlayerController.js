"use strict";

const {PlayerPawn} = require("./PlayerPawn.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerController {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[PlayerController] ctx required");
        this.cfg = cfg || null;

        this.pawn = new PlayerPawn(this.ctx, this.cfg).init();

        const ENGINE = req(globalThis.ENGINE, "[PlayerController] ENGINE required");
        req(ENGINE.controllers, "[PlayerController] ENGINE.controllers required");

        this.ec = ENGINE.controllers.entity("player", this.ctx, this.pawn, this.cfg);
        if (!this.ec) throw new Error("[PlayerController] ENGINE.controllers.entity(...) returned null");
    }

    update(dt) {
        this.ec.update(dt);
    }

    dispose() {
        this.ec.dispose();
        this.ec = null;

        this.pawn.destroy();
        this.pawn = null;

        this.ctx = null;
        this.cfg = null;
    }
}

module.exports = {PlayerController};