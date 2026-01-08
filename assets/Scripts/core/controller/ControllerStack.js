// FILE: Scripts/core/ControllerStack.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqFn(fn, msg) {
    if (typeof fn !== "function") throw new Error(msg);
    return fn;
}

class ControllerStack {
    constructor(modules, ids) {
        this.ctx = null;
        this.entity = null;

        this.modules = Array.isArray(modules) ? modules : [];
        this.ids = Array.isArray(ids) ? ids : new Array(this.modules.length);

        this._started = false;
        this._modsStarted = new Array(this.modules.length).fill(false);
    }

    static fromRegistry(registry, ctx, entity, cfg) {
        const built = registry.build(ctx, entity, cfg);
        const stack = new ControllerStack(built.list, built.ids);
        return stack.bind(ctx, entity);
    }

    bind(ctx, entity) {
        this.ctx = req(ctx, "[Stack] ctx is required");
        this.entity = req(entity, "[Stack] entity is required");

        for (let i = 0; i < this.modules.length; i++) {
            const m = req(this.modules[i], "[Stack] module is null at index " + i);
            reqFn(m.bind, "[Stack] module.bind(ctx,entity) required at index " + i);
            m.bind(this.ctx, this.entity);
        }
        return this;
    }

    _start() {
        if (this._started) return;
        this._started = true;

        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            this._modsStarted[i] = true;
            if (typeof m.onStart === "function") m.onStart();
        }
    }

    _tick(dt) {
        if (!this._started) this._start();
        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            if (typeof m.onUpdate === "function") m.onUpdate(dt);
        }
    }

    _shutdown() {
        if (!this._started) return;

        for (let i = this.modules.length - 1; i >= 0; i--) {
            const m = this.modules[i];
            if (!this._modsStarted[i]) continue;
            this._modsStarted[i] = false;
            if (typeof m.onStop === "function") m.onStop();
        }

        this._started = false;
    }

    rebuildFromRegistry(registry, cfg) {
        registry = req(registry, "[Stack] registry is required");
        if (!this.ctx || !this.entity) throw new Error("[Stack] rebuild requires bound stack");

        // 1) stop old
        this._shutdown();

        // 2) build new
        const built = registry.build(this.ctx, this.entity, cfg);

        // 3) swap
        this.modules = built.list;
        this.ids = built.ids;
        this._modsStarted = new Array(this.modules.length).fill(false);

        // 4) bind new modules to same ctx/entity
        for (let i = 0; i < this.modules.length; i++) {
            const m = req(this.modules[i], "[Stack] module is null at index " + i);
            reqFn(m.bind, "[Stack] module.bind(ctx,entity) required at index " + i);
            m.bind(this.ctx, this.entity);
        }

        // start will happen on next _tick()
    }
}

module.exports = {ControllerStack};