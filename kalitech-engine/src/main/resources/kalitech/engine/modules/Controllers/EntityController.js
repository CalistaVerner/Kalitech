// FILE: @module/Controllers/EntityController.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class EntityController {
    constructor(ctx, entity, stack) {
        this.ctx = req(ctx, "[EntityController] ctx is required");
        this.entity = req(entity, "[EntityController] entity is required");
        this.stack = req(stack, "[EntityController] stack is required");
        this._alive = true;

        if (typeof this.stack.bind === "function") this.stack.bind(this.ctx, this.entity);
    }

    update(dt) {
        if (!this._alive) throw new Error("[EntityController] update on disposed");
        if (this.stack) this.stack._tick(dt);
    }

    dispose() {
        if (!this._alive) return;
        this._alive = false;

        if (this.stack && typeof this.stack._shutdown === "function") this.stack._shutdown();
        this.stack = null;

        this.ctx = null;
        this.entity = null;
    }
}

module.exports = {EntityController};