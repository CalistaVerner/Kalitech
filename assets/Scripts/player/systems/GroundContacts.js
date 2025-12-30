// FILE: Scripts/player/systems/GroundContacts.js
"use strict";

class GroundContacts {
    constructor(player) {
        this.player = player;
        this.contacts = new Set(); // bodyId'ы, с кем мы в контакте
        this._onBegin = this._onBegin.bind(this);
        this._onEnd = this._onEnd.bind(this);
        this._bound = false;
    }

    bind(bus) {
        if (this._bound) return;
        if (!bus || typeof bus.on !== "function") throw new Error("[ground] bus.on missing");
        bus.on("engine.physics.collision.begin", this._onBegin);
        bus.on("engine.physics.collision.end", this._onEnd);
        this._bound = true;
    }

    unbind(bus) {
        if (!this._bound) return;
        if (bus && typeof bus.off === "function") {
            bus.off("engine.physics.collision.begin", this._onBegin);
            bus.off("engine.physics.collision.end", this._onEnd);
        }
        this.contacts.clear();
        this._bound = false;
    }

    _onBegin(e) {
        const my = this.player.bodyId | 0;
        const a = e && e.a, b = e && e.b;
        const aId = a && (a.bodyId | 0);
        const bId = b && (b.bodyId | 0);
        if (!my || !aId || !bId) return;

        const other = (aId === my) ? bId : (bId === my) ? aId : 0;
        if (!other) return;

        this.contacts.add(other);

        if (LOG && LOG.info) {
            LOG.info(`[ground] begin my=${my} other=${other} count=${this.contacts.size}`);
        }
    }

    _onEnd(e) {
        const my = this.player.bodyId | 0;
        const a = e && e.a, b = e && e.b;
        const aId = a && (a.bodyId | 0);
        const bId = b && (b.bodyId | 0);
        if (!my || !aId || !bId) return;

        const other = (aId === my) ? bId : (bId === my) ? aId : 0;
        if (!other) return;

        this.contacts.delete(other);

        if (LOG && LOG.info) {
            LOG.info(`[ground] end my=${my} other=${other} count=${this.contacts.size}`);
        }
    }

    grounded() {
        return this.contacts.size > 0;
    }
}

module.exports = GroundContacts;