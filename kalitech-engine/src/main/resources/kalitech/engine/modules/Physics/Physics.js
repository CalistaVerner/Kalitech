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

        this._orch = new PhysicsOrchestrator(ENGINE);

        this.events = createPhysicsEvents(ENGINE, this);
        Object.freeze(this.events);

        Object.freeze(this);
    }

    raw() {
        return this._orch.raw();
    }

    // lifecycle / handles
    body(cfg) {
        return this._orch.body(cfg);
    }

    remove(h) {
        return this._orch.remove(h);
    }

    removeById(id) {
        return this._orch.removeById(id);
    }

    bodyOfSurface(surface) {
        return this._orch.bodyOfSurface(surface);
    }

    handle(h) {
        return this._orch.handle(h);
    }

    exists(h) {
        return this._orch.exists(h);
    }

    ensureBodyForSurface(surface, cfg) {
        return this._orch.ensureBodyForSurface(surface, cfg);
    }

    // transforms
    position(h) {
        return this._orch.position(h);
    }

    teleport(h, v) {
        return this._orch.teleport(h, v);
    }

    warp(h, v) {
        return this._orch.warp(h, v);
    }

    velocity(h, v) {
        return this._orch.velocity(h, v);
    }

    angularVelocity(h, v) {
        return this._orch.angularVelocity(h, v);
    }

    yaw(h, y) {
        return this._orch.yaw(h, y);
    }

    // forces / flags
    applyImpulse(h, i) {
        return this._orch.applyImpulse(h, i);
    }

    applyCentralForce(h, f) {
        return this._orch.applyCentralForce(h, f);
    }

    applyTorque(h, t) {
        return this._orch.applyTorque(h, t);
    }

    clearForces(h) {
        return this._orch.clearForces(h);
    }

    lockRotation(h, l) {
        return this._orch.lockRotation(h, l);
    }

    setKinematic(h, k) {
        return this._orch.setKinematic(h, k);
    }

    collisionGroups(h, group, mask) {
        return this._orch.collisionGroups(h, group, mask);
    }

    // queries
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

    // engine knobs
    debug(e) {
        return this._orch.debug(e);
    }

    gravity(g) {
        return this._orch.gravity(g);
    }

    // events passthrough
    on(topic, fn) {
        return this._orch.on(topic, fn);
    }

    // ergonomic
    idOf(h) {
        return this._orch.idOf(h);
    }

    surfaceIdOf(h) {
        return this._orch.surfaceIdOf(h);
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
    version: "2.0.7",
    description: "ENGINE-only physics module (PhysicsApiImpl-aligned)",
    engineMin: "0.2.0"
});