"use strict";

// FILE: kalitech/engine/modules/Physics/Physics.js
// Author: KΛYLΛ

const {PhysicsOrchestrator} = require("./PhysicsOrchestrator.js");
const {createPhysicsEvents} = require("./helpers/PhysicsEvents.js");

/**
 * ENGINE-only Physics module.
 *
 * IMPORTANT:
 *  - This module does NOT register itself into ENGINE.
 *  - Bootstrap owns module registration: ENGINE.setModule("physics", api, moduleId).
 *  - Access it via ENGINE.modules.physics (canonical).
 */
class EnginePhysics {
    constructor(ENGINE) {
        if (!ENGINE) throw new Error("[ENGINE.physics] ENGINE is required");

        // Orchestrator resolves raw physics API from ENGINE (host/proxy safe)
        this._orch = new PhysicsOrchestrator(ENGINE);

        // Events use ENGINE.physics.on(...) passthrough (or raw backend on)
        this.events = createPhysicsEvents(ENGINE, this);
        Object.freeze(this.events);

        Object.freeze(this);
    }

    raw() {
        return this._orch.raw();
    }

    body(cfg) {
        return this._orch.body(cfg);
    }

    remove(h) {
        return this._orch.remove(h);
    }

    position(h, v) {
        return this._orch.position(h, v);
    }

    velocity(h, v) {
        return this._orch.velocity(h, v);
    }

    teleport(h, v) {
        return this._orch.teleport(h, v);
    }

    warp(h, v) {
        return this._orch.warp(h, v);
    }

    yaw(h, y) {
        return this._orch.yaw(h, y);
    }

    applyImpulse(h, i) {
        return this._orch.applyImpulse(h, i);
    }

    applyCentralForce(h, f) {
        return this._orch.applyCentralForce(h, f);
    }

    lockRotation(h, l) {
        return this._orch.lockRotation(h, l);
    }

    setKinematic(h, k) {
        return this._orch.setKinematic(h, k);
    }

    raycast(cfg) {
        return this._orch.raycast(cfg);
    }

    raycastEx(cfg) {
        return this._orch.raycastEx(cfg);
    }

    raycastAll(cfg) {
        return this._orch.raycastAll(cfg);
    }

    sweepSphere(cfg) {
        return this._orch.sweepSphere(cfg);
    }

    sweepCapsule(cfg) {
        return this._orch.sweepCapsule(cfg);
    }

    debug(e) {
        return this._orch.debug(e);
    }

    gravity(g) {
        return this._orch.gravity(g);
    }

    on(topic, fn) {
        return this._orch.on(topic, fn);
    }

    ref(h) {
        return this._orch.ref(h);
    }
}

function create(ENGINE) {
    if (!ENGINE) throw new Error("[ENGINE.physics] ENGINE is required");
    return new EnginePhysics(ENGINE);
}

module.exports = create;
module.exports.EnginePhysics = EnginePhysics;
module.exports.META = Object.freeze({
    moduleId: "physics",
    version: "2.0.6",
    description: "ENGINE-only physics module (bootstrap-owned registration)",
    engineMin: "0.2.0"
});