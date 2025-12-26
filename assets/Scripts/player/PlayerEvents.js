// FILE: Scripts/player/PlayerEvents.js
// Author: Calista Verner
"use strict";

/**
 * PlayerEvents — наблюдатель/эмиттер событий игрока (пока только логи).
 * Не управляет движением/камерой — только следит за фактом и сообщает.
 *
 * События (пока log-only):
 *  - player:spawn
 *  - player:ground (каждый кадр на земле, с throttling)
 *  - player:air    (каждый кадр в воздухе, с throttling)
 *  - player:land   (переход air -> ground)
 *  - player:leaveGround (ground -> air)
 */

function nowMs() { return Date.now ? Date.now() : 0; }

class PlayerEvents {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        cfg = cfg || {};

        this.enabled = (cfg.enabled !== undefined) ? !!cfg.enabled : true;

        this.throttleMs = (cfg.throttleMs !== undefined) ? (cfg.throttleMs | 0) : 250;

        this._spawned = false;
        this._wasGrounded = false;

        this._lastGroundLog = 0;
        this._lastAirLog = 0;
    }

    onSpawn(player) {
        if (!this.enabled) return;
        if (this._spawned) return;
        this._spawned = true;

        try {
            engine.log().info(
                "[player][ev] spawn entity=" + (player.entityId | 0) +
                " surfaceId=" + (player.surfaceId | 0) +
                " bodyId=" + (player.bodyId | 0)
            );
        } catch (_) {}
    }

    /**
     * @param {Object} s - summary { grounded:boolean, bodyId:int, pos?, vel? }
     */
    onState(player, s) {
        if (!this.enabled) return;

        const grounded = !!(s && s.grounded);
        const t = nowMs();

        // transitions
        if (grounded && !this._wasGrounded) {
            // land
            try { engine.log().info("[player][ev] land bodyId=" + (player.bodyId | 0)); } catch (_) {}
        } else if (!grounded && this._wasGrounded) {
            // leave ground
            try { engine.log().info("[player][ev] leaveGround bodyId=" + (player.bodyId | 0)); } catch (_) {}
        }

        // continuous (throttled)
        if (grounded) {
            if ((t - this._lastGroundLog) >= this.throttleMs) {
                this._lastGroundLog = t;
                try { engine.log().info("[player][ev] ground bodyId=" + (player.bodyId | 0)); } catch (_) {}
            }
        } else {
            if ((t - this._lastAirLog) >= this.throttleMs) {
                this._lastAirLog = t;
                try { engine.log().info("[player][ev] air bodyId=" + (player.bodyId | 0)); } catch (_) {}
            }
        }

        this._wasGrounded = grounded;
    }

    reset() {
        this._spawned = false;
        this._wasGrounded = false;
        this._lastGroundLog = 0;
        this._lastAirLog = 0;
    }
}

module.exports = PlayerEvents;