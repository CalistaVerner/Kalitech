// FILE: Scripts/player/PlayerEvents.js
"use strict";

class PlayerEvents {
    constructor(player) {
        this.player = player;

        const cfg = (player.cfg && player.cfg.events) || Object.create(null);
        this.enabled = (cfg.enabled !== undefined) ? !!cfg.enabled : true;

        this._spawned = false;
        this._wasGrounded = false;
        this._wasJump = false;
    }

    reset() {
        this._spawned = false;
        this._wasGrounded = false;
        this._wasJump = false;
    }

    onSpawn() {
        if (!this.enabled || this._spawned) return;
        this._spawned = true;
        this.player.camera.attach();
    }

    onState(state) {
        if (!this.enabled) return;

        const grounded = !!state.grounded;
        const jump = !!state.jump;
        const fallSpeed = U.num(state.fallSpeed, 0);

        if (grounded !== this._wasGrounded) {
            const cam = this.player.camera && this.player.camera.orch;

            if (grounded) {
                if (cam && typeof cam.onLand === "function") cam.onLand(0.4 + fallSpeed / 6.0);
            } else {
                if ((jump || this._wasJump) && cam && typeof cam.onJump === "function") cam.onJump(1.0);
            }

            this._wasGrounded = grounded;
        }

        this._wasJump = jump;
    }
}

const U = require("./util.js");
module.exports = PlayerEvents;