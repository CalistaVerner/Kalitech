// FILE: Scripts/player/PlayerEvents.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

function nowMs() { return Date.now(); }
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

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

        this._wasJumpInput = false;
    }

    reset() {
        this._spawned = false;
        this._wasGrounded = false;
        this._tAir = 0;
        this._tGround = 0;
        this._wasJumpInput = false;
    }

    onSpawn() {
        if (!this.enabled || this._spawned) return;
        this._spawned = true;

        // Example sound hook (keep disabled by default)
        // const sound = SND.create({ soundFile: "Sounds/adventure.ogg", volume: 1.0, pitch: 1.0, looping: false });
        // sound.play();
    }

    /**
     * state: {
     *   grounded: boolean,
     *   jump?: boolean,
     *   fallSpeed?: number
     * }
     */
    onState(state) {
        if (!this.enabled || !state) return;

        const grounded = !!state.grounded;
        const jumpIn = !!state.jump;
        const fallSpeed = +state.fallSpeed || 0;
        const t = nowMs();

        // --- transitions ---
        if (grounded !== this._wasGrounded) {
            const p = this.player;

            if (grounded) {
                const strength = clamp(0.4 + (fallSpeed / 6.0), 0.4, 4.0);
                const cam = p && p.camera;
                if (cam && typeof cam.onLand === "function") {
                    try { cam.onLand(strength); }
                    catch (e) { if (LOG && LOG.error) LOG.error("[player] camera.onLand failed: " + U.errStr(e)); }
                }
            } else {
                if (jumpIn || this._wasJumpInput) {
                    const cam = p && p.camera;
                    if (cam && typeof cam.onJump === "function") {
                        try { cam.onJump(1.0); }
                        catch (e) { if (LOG && LOG.error) LOG.error("[player] camera.onJump failed: " + U.errStr(e)); }
                    }
                }
            }

            this._wasGrounded = grounded;
        }

        this._wasJumpInput = jumpIn;

        // throttled "heartbeat" (optional)
        if (grounded) {
            if ((t - this._tGround) >= this.throttleMs) this._tGround = t;
        } else {
            if ((t - this._tAir) >= this.throttleMs) this._tAir = t;
        }
    }
}

module.exports = PlayerEvents;
