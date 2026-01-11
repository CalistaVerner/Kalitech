"use strict";

// FILE: @builtin/modules/Physics/PhysicsOrchestrator.js
// Author: KΛYLΛ

const {vec3Obj, vec3Arr, num, warn} = require("./helpers/PhysicsMath.js");
const {bodyIdOf, surfaceIdOf} = require("./helpers/PhysicsIds.js");

/**
 * Deterministic ENGINE.physics wrapper over Java physics api.
 * No globals. No heuristics for random method names.
 *
 * Resolves backend by:
 *  1) backend.physics()            (ENGINE style)
 *  2) backend.api().physics()      (api accessor style)
 *  3) backend                      (raw physics api)
 */
class PhysicsOrchestrator {
    constructor(backend) {
        if (!backend) throw new Error("[ENGINE.physics] backend is required");

        const phys = this._resolveBackend(backend);
        this._phys = phys;

        // ---- required core ----
        this._reqFn(phys, "body", "[ENGINE.physics] backend.body(cfg) missing");
        this._reqFn(phys, "remove", "[ENGINE.physics] backend.remove(id) missing");
        this._reqFn(phys, "position", "[ENGINE.physics] backend.position(id) missing");
        this._reqFn(phys, "applyImpulse", "[ENGINE.physics] backend.applyImpulse(id,vec3) missing");
        this._reqFn(phys, "lockRotation", "[ENGINE.physics] backend.lockRotation(id,bool) missing");

        // ---- teleport/warp ----
        const hasWarp = typeof phys.warp === "function";
        const hasTeleport = typeof phys.teleport === "function";
        if (!hasWarp && !hasTeleport) {
            throw new Error("[ENGINE.physics] backend.warp(id,vec3) or backend.teleport(id,vec3) missing");
        }

        // ---- velocity (either overload or get/set pair) ----
        const hasVelocityOverload = typeof phys.velocity === "function";
        const hasGetSetVelocity = (typeof phys.getVelocity === "function" && typeof phys.setVelocity === "function");
        if (!hasVelocityOverload && !hasGetSetVelocity) {
            throw new Error("[ENGINE.physics] backend.velocity(id[,vec3]) or getVelocity/setVelocity missing");
        }

        this._teleportImpl = hasTeleport
            ? (id, v) => phys.teleport(id, v)
            : (id, v) => phys.warp(id, v);

        this._velGetImpl = hasVelocityOverload
            ? (id) => phys.velocity(id)
            : (id) => phys.getVelocity(id);

        this._velSetImpl = hasVelocityOverload
            ? (id, v) => phys.velocity(id, v)
            : (id, v) => phys.setVelocity(id, v);
    }

    _resolveBackend(backend) {
        // 1) ENGINE style: engine.physics() returns raw physics api
        if (typeof backend.physics === "function") {
            const p = backend.physics();
            if (!p || typeof p !== "object") throw new Error("[ENGINE.physics] backend.physics() returned invalid object");
            return p;
        }

        // 2) api accessor style: engine.api().physics()
        if (typeof backend.api === "function") {
            const api = backend.api();
            if (api && typeof api.physics === "function") {
                const p = api.physics();
                if (!p || typeof p !== "object") throw new Error("[ENGINE.physics] backend.api().physics() returned invalid object");
                return p;
            }
        }

        // 3) raw physics api
        if (backend && typeof backend === "object") return backend;

        throw new Error("[ENGINE.physics] invalid backend");
    }

    _reqFn(obj, name, msg) {
        if (typeof obj[name] !== "function") throw new Error(msg);
    }

    raw() {
        return this._phys;
    }

    // ----- creation / removal -----
    body(cfg) {
        return this._phys.body(cfg);
    }

    remove(h) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] remove(): invalid body id");
        return this._phys.remove(id);
    }

    // ----- queries -----
    position(h, v) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] position(): invalid body id");
        if (v === undefined) return this._phys.position(id);
        return this._teleportImpl(id, vec3Obj(v, 0, 0, 0));
    }

    teleport(h, v) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] teleport(): invalid body id");
        return this._teleportImpl(id, vec3Obj(v, 0, 0, 0));
    }

    warp(h, v) {
        return this.teleport(h, v);
    }

    velocity(h, v) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] velocity(): invalid body id");
        if (v === undefined) return this._velGetImpl(id);
        return this._velSetImpl(id, vec3Obj(v, 0, 0, 0));
    }

    yaw(h, yawRad) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] yaw(): invalid body id");
        if (typeof this._phys.yaw !== "function") throw new Error("[ENGINE.physics] backend.yaw(id,yawRad) missing");
        return this._phys.yaw(id, num(yawRad, 0));
    }

    // ----- forces -----
    applyImpulse(h, impulse) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] applyImpulse(): invalid body id");
        return this._phys.applyImpulse(id, vec3Obj(impulse, 0, 0, 0));
    }

    applyCentralForce(h, force) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] applyCentralForce(): invalid body id");
        if (typeof this._phys.applyCentralForce !== "function") throw new Error("[ENGINE.physics] backend.applyCentralForce(id,vec3) missing");
        return this._phys.applyCentralForce(id, vec3Obj(force, 0, 0, 0));
    }

    lockRotation(h, lock) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] lockRotation(): invalid body id");
        return this._phys.lockRotation(id, !!lock);
    }

    setKinematic(h, kinematic) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] setKinematic(): invalid body id");
        if (typeof this._phys.setKinematic !== "function") throw new Error("[ENGINE.physics] backend.setKinematic(id,bool) missing");
        return this._phys.setKinematic(id, !!kinematic);
    }

    // ----- raycasts -----
    _ray(cfg) {
        const c = Object.assign({}, cfg);
        c.from = vec3Arr(c.from, 0, 0, 0);
        c.to = vec3Arr(c.to, 0, -1, 0);
        return c;
    }

    _sweep(cfg) {
        const c = Object.assign({}, cfg);
        c.from = vec3Arr(c.from, 0, 0, 0);
        c.to = vec3Arr(c.to, 0, -1, 0);

        // common optional filters
        if (c.mask != null) c.mask = (c.mask | 0);
        if (c.group != null) c.group = (c.group | 0);
        if (c.ignoreBody != null) c.ignoreBody = (c.ignoreBody | 0);
        if (c.ignoreSurface != null) c.ignoreSurface = (c.ignoreSurface | 0);

        return c;
    }

    raycast(cfg) {
        if (typeof this._phys.raycast !== "function") throw new Error("[ENGINE.physics] backend.raycast(cfg) missing");
        return this._phys.raycast(this._ray(cfg));
    }

    // ----- convex sweeps (sphere/capsule) -----
    sweepSphere(cfg) {
        if (typeof this._phys.sweepSphere !== "function") {
            throw new Error("[ENGINE.physics] backend.sweepSphere(cfg) missing");
        }
        const c = this._sweep(cfg);
        c.radius = num(c.radius, 0);
        if (!(c.radius > 0)) throw new Error("[ENGINE.physics] sweepSphere: cfg.radius must be > 0");
        return this._phys.sweepSphere(c);
    }

    sweepCapsule(cfg) {
        if (typeof this._phys.sweepCapsule !== "function") {
            throw new Error("[ENGINE.physics] backend.sweepCapsule(cfg) missing");
        }
        const c = this._sweep(cfg);
        c.radius = num(c.radius, 0);
        c.height = num(c.height, 0);

        if (!(c.radius > 0)) throw new Error("[ENGINE.physics] sweepCapsule: cfg.radius must be > 0");
        if (!(c.height >= 0)) throw new Error("[ENGINE.physics] sweepCapsule: cfg.height must be >= 0");

        // orientation (optional)
        c.up = vec3Arr(c.up, 0, 1, 0);

        return this._phys.sweepCapsule(c);
    }


    raycastEx(cfg) {
        if (typeof this._phys.raycastEx !== "function") throw new Error("[ENGINE.physics] backend.raycastEx(cfg) missing");
        return this._phys.raycastEx(this._ray(cfg));
    }

    raycastAll(cfg) {
        if (typeof this._phys.raycastAll !== "function") throw new Error("[ENGINE.physics] backend.raycastAll(cfg) missing");
        return this._phys.raycastAll(this._ray(cfg));
    }

    // ----- engine knobs -----
    debug(enable) {
        if (typeof this._phys.debug !== "function") return;
        try {
            this._phys.debug(!!enable);
        } catch (e) {
            warn(e);
        }
    }

    gravity(g) {
        if (typeof this._phys.gravity !== "function") return;
        try {
            this._phys.gravity(vec3Obj(g, 0, -9.81, 0));
        } catch (e) {
            warn(e);
        }
    }

    // ----- ids -----
    idOf(h) {
        return bodyIdOf(h);
    }

    surfaceIdOf(h) {
        return surfaceIdOf(h);
    }

    ensureBodyForSurface(surfaceHandleOrId, cfg) {
        const sid = surfaceIdOf(surfaceHandleOrId);
        if (sid <= 0) throw new Error("[ENGINE.physics] ensureBodyForSurface(): invalid surface id");
        return this.body(Object.assign({}, cfg, {surface: sid}));
    }

    // ----- events passthrough -----
    on(topic, fn) {
        if (typeof this._phys.on !== "function") throw new Error("[ENGINE.physics] backend.on(topic,fn) missing");
        return this._phys.on(topic, fn);
    }

    // ----- ergonomic ref -----
    ref(h) {
        const id = bodyIdOf(h);
        if (id <= 0) throw new Error("[ENGINE.physics] ref(): invalid body id");
        const self = this;
        return Object.freeze({
            id: () => id,
            position: (v) => self.position(id, v),
            teleport: (v) => self.teleport(id, v),
            warp: (v) => self.teleport(id, v),
            velocity: (v) => self.velocity(id, v),
            yaw: (y) => self.yaw(id, y),
            applyImpulse: (i) => self.applyImpulse(id, i),
            applyCentralForce: (f) => self.applyCentralForce(id, f),
            lockRotation: (l) => self.lockRotation(id, l),
            setKinematic: (k) => self.setKinematic(id, k),
            remove: () => self.remove(id),
        });
    }
}

module.exports = {PhysicsOrchestrator};