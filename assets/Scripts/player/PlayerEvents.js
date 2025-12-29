// Author: Calista Verner
"use strict";

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

        // jump/land bookkeeping
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

        // тестовый телепорт (можешь убрать)
        //this.player.entity.warp({ x: 200, y: 8, z: -300 });

        const sound = SND.create({
            soundFile: "Sounds/adventure.ogg",
            volume: 1.0,
            pitch: 1.0,
            looping: false
        });
        //sound.play();
    }

    /**
     * state: {
     *   grounded: boolean,
     *   bodyId?: number,
     *   jump?: boolean,      // input (optional)
     *   fallSpeed?: number   // positive when falling down (optional)
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

            // landed
            if (grounded) {
                // map fallSpeed to impact strength
                // (tune: 0..~20 => 0.4..4.0)
                const strength = clamp(0.4 + (fallSpeed / 6.0), 0.4, 4.0);

                try { if (p && p.camera && typeof p.camera.onLand === "function") p.camera.onLand(strength); } catch (_) {}
                //engine.log().info("[player] land strength=" + strength.toFixed(2));
            } else {
                // left ground (jump start if jump input was pressed)
                if (jumpIn || this._wasJumpInput) {
                    try { if (p && p.camera && typeof p.camera.onJump === "function") p.camera.onJump(1.0); } catch (_) {}
                    //engine.log().info("[player] jump");
                }
            }

            this._wasGrounded = grounded;
        }

        this._wasJumpInput = jumpIn;

        // optional throttled continuous logs
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
