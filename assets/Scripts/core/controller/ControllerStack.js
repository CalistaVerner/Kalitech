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
    constructor(modules) {
        this.ctx = null;
        this.entity = null;

        this.modules = Array.isArray(modules) ? modules : [];
        this._started = false;
        this._modsStarted = new Array(this.modules.length).fill(false);
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
}

module.exports = {ControllerStack};