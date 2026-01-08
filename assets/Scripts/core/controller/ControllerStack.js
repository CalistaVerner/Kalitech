"use strict";

/**
 * ControllerStack (Orchestrator)
 *
 * - No inheritance
 * - Hard lifecycle
 * - Drives child modules in order
 * - Shutdown in reverse order
 *
 * Child module contract:
 *   bind(ctx, entity)
 *   onStart()
 *   onUpdate(dt)
 *   onStop()
 */
class ControllerStack {
    constructor(modules) {
        this.ctx = null;
        this.entity = null;

        this._started = false;

        this.modules = Array.isArray(modules) ? modules : [];
        this._modsStarted = new Array(this.modules.length);
        for (let i = 0; i < this._modsStarted.length; i++) this._modsStarted[i] = false;
    }

    bind(ctx, entity) {
        if (ctx == null) throw new Error("[ControllerStack] ctx is required");
        if (entity == null) throw new Error("[ControllerStack] entity is required");

        this.ctx = ctx;
        this.entity = entity;

        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            if (!m || typeof m.bind !== "function") {
                throw new Error("[ControllerStack] module.bind(ctx,entity) required at index " + i);
            }
            m.bind(ctx, entity);
        }

        return this;
    }

    _start() {
        if (this._started) return;
        this._started = true;

        for (let i = 0; i < this.modules.length; i++) {
            const m = this.modules[i];
            if (!this._modsStarted[i]) {
                this._modsStarted[i] = true;
                if (typeof m.onStart === "function") m.onStart();
            }
        }
    }

    _tick(dt) {
        if (!Number.isFinite(dt)) throw new Error("[ControllerStack] dt must be finite");
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
            if (this._modsStarted[i]) {
                this._modsStarted[i] = false;
                if (typeof m.onStop === "function") m.onStop();
            }
        }

        this._started = false;
    }
}

module.exports = {ControllerStack};