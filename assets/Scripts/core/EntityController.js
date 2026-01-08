// FILE: Scripts/core/EntityController.js
"use strict";

/**
 * Base OOP controller for any entity.
 *
 * Contract:
 *  - bind(ctx, entity): once
 *  - onStart(): once
 *  - onUpdate(dt): every frame
 *  - onStop(): once
 *
 * NOTE:
 *  - Use EntityControllerLink to drive lifecycle.
 */
class EntityController {
    constructor() {
        this.ctx = null;
        this.entity = null;
        this._started = false;
    }

    bind(ctx, entity) {
        if (ctx == null) throw new Error("[EntityController] ctx is required");
        if (entity == null) throw new Error("[EntityController] entity is required");
        this.ctx = ctx;
        this.entity = entity;
        return this;
    }

    onStart() {
    }

    onUpdate(dt) {
    }

    onStop() {
    }

    _start() {
        if (this._started) return;
        this._started = true;
        this.onStart();
    }

    _tick(dt) {
        if (!Number.isFinite(dt)) throw new Error("[EntityController] dt must be finite");
        if (!this._started) this._start();
        this.onUpdate(dt);
    }

    _shutdown() {
        if (!this._started) return;
        this.onStop();
        this._started = false;
    }
}

module.exports = {EntityController};