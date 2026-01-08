// FILE: @module/Controllers/EntityControllerLink.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class EntityControllerLink {
    constructor(id, entity, controller) {
        this.id = String(id || "");
        this.entity = req(entity, "[EntityControllerLink] entity is required");
        this.controller = req(controller, "[EntityControllerLink] controller is required");
        this._alive = true;
    }

    setController(controller) {
        if (!this._alive) throw new Error("[EntityControllerLink] setController on disposed");
        this.controller = req(controller, "[EntityControllerLink] controller is required");
        return this;
    }

    update(dt) {
        if (!this._alive) throw new Error("[EntityControllerLink] update on disposed");
        const c = this.controller;
        if (c && typeof c.update === "function") c.update(dt);
    }

    dispose() {
        if (!this._alive) return;
        this._alive = false;

        const c = this.controller;
        this.controller = null;

        if (c && typeof c.dispose === "function") c.dispose();

        this.entity = null;
        this.id = "";
    }
}

module.exports = {EntityControllerLink};