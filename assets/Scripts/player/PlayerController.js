// FILE: Scripts/player/PlayerController.js
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
        const ec = this.ec;
        if (ec) ec.update(dt);
    }

    dispose() {
        // idempotent + crash-safe: always try to kill pawn/entity
        const ec = this.ec;
        const pawn = this.pawn;

        this.ec = null;
        this.pawn = null;

        this.ctx = null;
        this.cfg = null;

        if (ec && typeof ec.dispose === "function") {
            try {
                ec.dispose();
            } catch (_) {
            }
        }

        if (pawn && typeof pawn.destroy === "function") {
            try {
                pawn.destroy();
            } catch (_) {
            }
        }
    }
}

module.exports = {PlayerController};