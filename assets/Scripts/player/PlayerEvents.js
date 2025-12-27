// FILE: Scripts/player/PlayerEvents.js
// Author: Calista Verner
"use strict";

function nowMs() { return Date.now(); }

class PlayerEvents {
    constructor(player) {
        this.player = player;

        const cfg = (player.cfg && player.cfg.events) || {};
        this.enabled = (cfg.enabled !== undefined) ? !!cfg.enabled : true;
        this.throttleMs = (cfg.throttleMs | 0) || 250;

        this._spawned = false;
        this._wasGrounded = false;
        this._tAir = 0;
        this._tGround = 0;
    }

    reset() {
        this._spawned = false;
        this._wasGrounded = false;
        this._tAir = 0;
        this._tGround = 0;
    }

    onSpawn() {
        if (!this.enabled || this._spawned) return;
        this._spawned = true;

        const p = this.player;
        //engine.log().info("[player] spawn entity=" + (p.entityId | 0) + " bodyId=" + (p.bodyId | 0));

        // тестовый телепорт (можешь убрать)
        this.player.entity.warp({ x: 200, y: 8, z: -300 });
    }

    onState(state) {
        if (!this.enabled) return;

        const grounded = !!state.grounded;
        const t = nowMs();

        // transitions only
        if (grounded !== this._wasGrounded) {
            //engine.log().info(grounded ? "[player] land" : "[player] leaveGround");
            this._wasGrounded = grounded;
        }

        // optional throttled continuous logs (оставь или выкинь)
        if (grounded) {
            if ((t - this._tGround) >= this.throttleMs) {
                this._tGround = t;
                //engine.log().info("[player] ground");
            }
        } else {
            if ((t - this._tAir) >= this.throttleMs) {
                this._tAir = t;
                //engine.log().info("[player] air");
            }
        }
    }
}

module.exports = PlayerEvents;