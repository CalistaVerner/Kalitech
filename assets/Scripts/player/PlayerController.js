// FILE: Scripts/player/PlayerController.js
"use strict";

const { PlayerPawn } = require("./PlayerPawn.js");
const { createPlayerControllers } = require("./PlayerControllers.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

/**
 * PlayerController
 *
 * Owns PlayerPawn + all controllers.
 * No "bind" magic: every controller receives the player instance in constructor.
 */
class PlayerController {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[Player] ctx required");
        this.cfg = cfg || null;

        this.pawn = new PlayerPawn(this.ctx, this.cfg).init();

        // Provide explicit access point for everyone (no hidden globals).
        this.controllers = [];
        this._started = false;

        // Create controllers with strict OOP injection.
        this.controllers = createPlayerControllers(this);

        // Deterministic lifecycle.
        this.start();
    }

    start() {
        if (this._started) return;
        for (let i = 0; i < this.controllers.length; i++) {
            const c = this.controllers[i];
            if (c && typeof c.onStart === "function") c.onStart();
        }
        this._started = true;
    }

    update(dt) {
        if (!this._started) this.start();
        for (let i = 0; i < this.controllers.length; i++) {
            const c = this.controllers[i];
            if (c && typeof c.onUpdate === "function") c.onUpdate(dt);
        }
    }

    dispose() {
        const list = this.controllers;

        this.controllers = [];
        this._started = false;

        // Stop controllers in reverse order.
        for (let i = list.length - 1; i >= 0; i--) {
            const c = list[i];
            if (c && typeof c.onStop === "function") {
                try { c.onStop(); } catch (_) {}
            }
        }

        const pawn = this.pawn;
        this.pawn = null;

        const ctx = this.ctx;
        this.ctx = null;
        this.cfg = null;

        if (pawn && typeof pawn.destroy === "function") {
            try { pawn.destroy(); } catch (_) {}
        }

        // ctx is managed by world runtime; do not dispose it here.
        void ctx;
    }
}

module.exports = { PlayerController };