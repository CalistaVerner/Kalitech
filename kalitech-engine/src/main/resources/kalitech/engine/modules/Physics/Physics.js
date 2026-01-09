"use strict";

const PhysicsOrchestrator = require("./PhysicsOrchestrator.js");
const PhysicsCollider = require("./helpers/PhysicsCollider.js");
const PhysicsEvents = require("./helpers/PhysicsEvents.js");
const {bodyIdOf, surfaceIdOf} = require("./helpers/PhysicsIds.js");
const {vec3Obj} = require("./helpers/PhysicsMath.js");

class Physics {
    constructor(engine) {
        this._orch = new PhysicsOrchestrator(engine);
        this.collider = PhysicsCollider;
        this.events = PhysicsEvents;
        Object.freeze(this.collider);
        Object.freeze(this.events);
        Object.freeze(this);
    }

    body(c) {
        return this._orch.body(c);
    }

    remove(h) {
        return this._orch.remove(h);
    }

    raycast(c) {
        return this._orch.raycast(c);
    }

    raycastEx(c) {
        return this._orch.raycastEx(c);
    }

    raycastAll(c) {
        return this._orch.raycastAll(c);
    }

    position(h, v) {
        return this._orch.position(h, v);
    }

    warp(h, v) {
        return this._orch.warp(h, v);
    }

    velocity(h, v) {
        return this._orch.velocity(h, v);
    }

    yaw(h, y) {
        return this._orch.yaw(h, y);
    }

    applyImpulse(h, i) {
        return this._orch.applyImpulse(h, i);
    }

    lockRotation(h, l) {
        return this._orch.lockRotation(h, l);
    }

    setKinematic(h, k) {
        return this._orch.setKinematic(h, k);
    }

    debug(e) {
        return this._orch.debug(e);
    }

    gravity(g) {
        return this._orch.gravity(g);
    }

    idOf(h) {
        return bodyIdOf(h);
    }

    surfaceIdOf(h) {
        return surfaceIdOf(h);
    }

    vec3(v, x, y, z) {
        return vec3Obj(v, x, y, z);
    }

    ensureBodyForSurface(s, c) {
        return this._orch.ensureBodyForSurface(s, c);
    }

    ref(h) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[PHYS] ref(): invalid body");
        return Object.freeze({
            id: () => id,
            position: v => this.position(id, v),
            warp: v => this.warp(id, v),
            velocity: v => this.velocity(id, v),
            yaw: y => this.yaw(id, y),
            applyImpulse: i => this.applyImpulse(id, i),
            lockRotation: l => this.lockRotation(id, l),
            setKinematic: k => this.setKinematic(id, k),
            remove: () => this.remove(id),
        });
    }
}

module.exports = engine => new Physics(engine);
module.exports.Physics = Physics;
module.exports.META = Object.freeze({
    moduleId: "physics",
    globalName: "PHYS",
    version: "1.0.3",
    engineMin: "0.1.0",
});