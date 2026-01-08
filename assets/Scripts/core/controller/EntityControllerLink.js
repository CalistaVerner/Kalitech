"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqFn(fn, msg) {
    if (typeof fn !== "function") throw new Error(msg);
    return fn;
}

class EntityControllerLink {
    constructor(ctx, entity, orchestrator) {
        this.ctx = req(ctx, "[Link] ctx is required");
        this.entity = req(entity, "[Link] entity is required");
        this.orch = req(orchestrator, "[Link] orchestrator is required");

        reqFn(this.orch.bind, "[Link] orchestrator.bind(ctx,entity) required");
        reqFn(this.orch._tick, "[Link] orchestrator._tick(dt) required");
        reqFn(this.orch._shutdown, "[Link] orchestrator._shutdown() required");

        this._alive = true;
        this._started = false;

        this.orch.bind(this.ctx, this.entity);
    }

    update(dt) {
        if (!this._alive) throw new Error("[Link] update() on disposed link");
        if (!Number.isFinite(dt)) throw new Error("[Link] dt must be finite");

        if (!this._started) {
            this._started = true;
            if (typeof this.orch._start === "function") this.orch._start();
        }

        this.orch._tick(dt);
    }

    dispose() {
        if (!this._alive) return;
        this._alive = false;

        this.orch._shutdown();

        this.orch = null;
        this.entity = null;
        this.ctx = null;
    }
}

module.exports = {EntityControllerLink};