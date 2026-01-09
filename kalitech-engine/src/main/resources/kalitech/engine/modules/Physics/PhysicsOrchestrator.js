"use strict";

const {vec3Obj, vec3Arr, num, warn} = require("./helpers/PhysicsMath.js");
const {bodyIdOf, surfaceIdOf} = require("./helpers/PhysicsIds.js");

class PhysicsOrchestrator {
    constructor(engine) {
        if (!engine) throw new Error("[PHYS] engine is required");
        const phys = engine.physics ? engine.physics() : null;
        if (!phys) throw new Error("[PHYS] engine.physics() is not available");
        this.phys = phys;
    }

    body(cfg) {
        return this.phys.body(cfg);
    }

    remove(h) {
        const id = bodyIdOf(h);
        if (id > 0) return this.phys.remove(id);
    }

    raycast(cfg) {
        cfg = Object.assign({}, cfg);
        cfg.from = vec3Arr(cfg.from, 0, 0, 0);
        cfg.to = vec3Arr(cfg.to, 0, -1, 0);
        return this.phys.raycast(cfg);
    }

    raycastEx(cfg) {
        return this.phys.raycastEx(this._ray(cfg));
    }

    raycastAll(cfg) {
        return this.phys.raycastAll(this._ray(cfg));
    }

    _ray(cfg) {
        cfg = Object.assign({}, cfg);
        cfg.from = vec3Arr(cfg.from, 0, 0, 0);
        cfg.to = vec3Arr(cfg.to, 0, -1, 0);
        return cfg;
    }

    position(h, v) {
        const id = bodyIdOf(h);
        return v !== undefined ? this.phys.warp(id, vec3Obj(v, 0, 0, 0)) : this.phys.position(id);
    }

    warp(h, v) {
        return this.phys.warp(bodyIdOf(h), vec3Obj(v, 0, 0, 0));
    }

    velocity(h, v) {
        return v !== undefined ? this.phys.velocity(bodyIdOf(h), vec3Obj(v, 0, 0, 0)) : this.phys.velocity(bodyIdOf(h));
    }

    yaw(h, y) {
        return this.phys.yaw(bodyIdOf(h), num(y, 0));
    }

    applyImpulse(h, i) {
        return this.phys.applyImpulse(bodyIdOf(h), vec3Obj(i, 0, 0, 0));
    }

    lockRotation(h, l) {
        return this.phys.lockRotation(bodyIdOf(h), !!l);
    }

    setKinematic(h, k) {
        if (!this.phys.setKinematic) throw new Error("[PHYS] setKinematic not supported");
        return this.phys.setKinematic(bodyIdOf(h), !!k);
    }

    debug(e) {
        try {
            this.phys.debug(!!e);
        } catch (x) {
            warn(x);
        }
    }

    gravity(g) {
        try {
            this.phys.gravity(vec3Obj(g, 0, -9.81, 0));
        } catch (x) {
            warn(x);
        }
    }

    ensureBodyForSurface(s, cfg) {
        const sid = surfaceIdOf(s);
        return this.body(Object.assign({}, cfg, {surface: sid}));
    }
}

module.exports = PhysicsOrchestrator;
