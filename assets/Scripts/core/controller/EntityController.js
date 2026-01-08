// FILE: Scripts/core/controller/EntityController.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqFn(fn, msg) {
    if (typeof fn !== "function") throw new Error(msg);
    return fn;
}

class EntityController {
    constructor(ctx, entity, modules) {
        this.ctx = req(ctx, "[EntityController] ctx is required");
        this.entity = req(entity, "[EntityController] entity is required");

        this.modules = Array.isArray(modules) ? modules : [];
        this._started = false;
        this._alive = true;

        // bind once
        for (let i = 0; i < this.modules.length; i++) {
            const m = req(this.modules[i], "[EntityController] module is null at index " + i);
            reqFn(m.bind, "[EntityController] module.bind(ctx,entity) required at index " + i);
            m.bind(this.ctx, this.entity);
        }
    }

    _startOnce() {
        if (this._started) return;
        this._started = true;
        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            if (typeof m.onStart === "function") m.onStart();
        }
    }

    update(dt) {
        if (!this._alive) throw new Error("[EntityController] update() on disposed controller");
        if (!Number.isFinite(dt)) throw new Error("[EntityController] dt must be finite");

        this._startOnce();

        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            if (typeof m.onUpdate === "function") m.onUpdate(dt);
        }
    }

    dispose() {
        if (!this._alive) return;
        this._alive = false;

        if (this._started) {
            for (let i = this.modules.length - 1; i >= 0; i--) {
                const m = this.modules[i];
                if (typeof m.onStop === "function") m.onStop();
            }
        }

        this._started = false;
        this.modules = [];
        this.entity = null;
        this.ctx = null;
    }
}

module.exports = {EntityController};