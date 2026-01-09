"use strict";

const U = require("./util.js");

class PlayerEvents {
    constructor(player) {
        this.player = player;

        const cfg = (player.cfg && player.cfg.events) || Object.create(null);
        this.enabled = (cfg.enabled !== undefined) ? !!cfg.enabled : true;

        this.bus = this.enabled ? player.d.bus : null;
        if (this.enabled && !this.bus) throw new Error("[PlayerEvents] enabled but bus missing");

        this._subs = [];
        this._wasGrounded = false;
    }

    on(topic, fn) {
        if (!this.enabled) return 0;
        const id = (this.bus.on(topic, fn) | 0);
        if (id) this._subs.push(id);
        return id;
    }

    emit(topic, payload) {
        if (!this.enabled) return;
        this.bus.emit(topic, payload);
    }

    tick(frame) {
        if (!this.enabled) return;

        const grounded = !!frame.pose.grounded;
        if (grounded !== this._wasGrounded) {
            if (grounded) this.emit("player.land", {fallSpeed: U.num(frame.pose.fallSpeed, 0)});
            else this.emit("player.air", {});
            this._wasGrounded = grounded;
        }

        if (frame.input.jump) this.emit("player.jump", {});
    }

    destroy() {
        if (!this.enabled) return;
        if (typeof this.bus.off !== "function") throw new Error("[PlayerEvents] bus.off(id) required");
        for (let i = 0; i < this._subs.length; i++) this.bus.off(this._subs[i] | 0);
        this._subs.length = 0;
        this._wasGrounded = false;
    }
}

module.exports = PlayerEvents;
