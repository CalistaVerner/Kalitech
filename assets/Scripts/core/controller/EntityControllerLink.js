// FILE: Scripts/core/EntityControllerLink.js
"use strict";

function must(v, name) {
    if (v == null) throw new Error("[EntityControllerLink] " + name + " is required");
    return v;
}

/**
 * Hard binder: ties entity + controller into one runtime unit.
 * No magic. No optional paths. No fallbacks.
 */
class EntityControllerLink {
    constructor(ctx, entity, controller) {
        this.ctx = must(ctx, "ctx");
        this.entity = must(entity, "entity");
        this.controller = must(controller, "controller");

        if (typeof this.controller.bind !== "function") throw new Error("[EntityControllerLink] controller.bind(ctx,entity) required");
        if (typeof this.controller._tick !== "function") throw new Error("[EntityControllerLink] controller._tick(dt) required");
        if (typeof this.controller._shutdown !== "function") throw new Error("[EntityControllerLink] controller._shutdown() required");

        this.controller.bind(this.ctx, this.entity);

        this._dead = false;
    }

    update(dt) {
        if (this._dead) return;
        this.controller._tick(dt);
    }

    dispose() {
        if (this._dead) return;
        this._dead = true;
        this.controller._shutdown();
    }
}

module.exports = {EntityControllerLink};
