// Author: Calista Verner
"use strict";

/**
 * Physics RootKit (engine-extension).
 *
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 *
 * Goals:
 * - Thin, stable wrapper around engine.physics()
 * - Robust handle/id normalization (id / handle / {id} / {bodyId} / Value)
 * - Friendly helpers for vec3 configs and common operations
 *
 * Notes:
 * - Does NOT change Java API. Just wraps PhysicsApiImpl exports.
 * - Keeps all calls "safe": try/catch around host calls where appropriate.
 */

function isPlainJsObject(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function num(v, fallback) {
    const n = +v;
    return Number.isFinite(n) ? n : (fallback || 0);
}

// Accept:
// - [x,y,z]
// - {x,y,z}
// - {x:()=>,y:()=>,z:()=>}
// - Java Vec3 with .x/.y/.z OR .x()/.y()/.z()
function vec3(v, fbX, fbY, fbZ) {
    const out = { x: fbX || 0, y: fbY || 0, z: fbZ || 0 };
    if (v == null) return out;

    if (Array.isArray(v)) {
        out.x = num(v[0], out.x);
        out.y = num(v[1], out.y);
        out.z = num(v[2], out.z);
        return out;
    }

    if (typeof v === "object") {
        try {
            const x = v.x;
            const y = v.y;
            const z = v.z;

            out.x = (typeof x === "function") ? num(x.call(v), out.x) : num(x, out.x);
            out.y = (typeof y === "function") ? num(y.call(v), out.y) : num(y, out.y);
            out.z = (typeof z === "function") ? num(z.call(v), out.z) : num(z, out.z);
            return out;
        } catch (_) {
            return out;
        }
    }

    return out;
}

/**
 * Robust id resolver for body handles (number | handle | Value | {id|bodyId}).
 * Mirrors your engine-side resolveBodyId rules.
 */
function bodyIdOf(h) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    try {
        if (typeof h.valueOf === "function") {
            const v = h.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}

    try {
        if (typeof h.id === "number") return h.id | 0;
        if (typeof h.bodyId === "number") return h.bodyId | 0;
    } catch (_) {}

    const fnNames = ["id", "getId", "bodyId", "getBodyId", "handle"];
    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        try {
            const fn = h[n];
            if (typeof fn === "function") {
                const r = fn.call(h);
                if (typeof r === "number" && isFinite(r)) return r | 0;
            }
        } catch (_) {}
    }

    return 0;
}

/**
 * Robust id resolver for surface handles (surfaceId / id).
 * Useful for physics.body({ surface: <...> })
 */
function surfaceIdOf(s) {
    if (s == null) return 0;
    if (typeof s === "number") return s | 0;

    try {
        if (typeof s.valueOf === "function") {
            const v = s.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}

    try {
        if (typeof s.id === "number") return s.id | 0;
        if (typeof s.surfaceId === "number") return s.surfaceId | 0;
    } catch (_) {}

    const fnNames = ["id", "getId", "surfaceId", "getSurfaceId", "handle"];
    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        try {
            const fn = s[n];
            if (typeof fn === "function") {
                const r = fn.call(s);
                if (typeof r === "number" && isFinite(r)) return r | 0;
            }
        } catch (_) {}
    }

    return 0;
}

function makeApi(engine, K) {
    const phys = engine.physics();

    // optional internal logging helper
    const log = (engine.log && engine.log()) ? engine.log() : null;
    function warn(msg) { try { if (log && log.warn) log.warn(msg); } catch (_) {} }

    /**
     * body(cfg)
     *
     * Java requires:
     *   cfg.surface (surfaceId or SurfaceHandle)
     * Optional:
     *   mass, friction, restitution, damping{linear,angular}, kinematic, lockRotation, collider{...}
     *
     * Returns PhysicsBodyHandle (Java-side) which also has .id/.surfaceId.
     */
    function body(cfg) {
        if (!cfg) throw new Error("PHYS.body(cfg): cfg is required");

        // Normalize cfg.surface if user passed handle-ish
        if (cfg.surface != null) {
            const sid = surfaceIdOf(cfg.surface);
            if (sid > 0) cfg = Object.assign({}, cfg, { surface: sid });
        }

        return phys.body(cfg);
    }

    function remove(handleOrId) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) return;
        try { phys.remove(id); } catch (_) {}
    }

    /**
     * raycast({from,to})
     * returns PhysicsRayHit or null
     */
    function raycast(cfg) {
        return phys.raycast(cfg);
    }

    /**
     * position(handleOrId) -> Vec3
     * position(handleOrId, vec3) -> warp
     */
    function position(handleOrId, maybeVec3) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.position(handleOrId): body id/handle required");

        if (maybeVec3 === undefined) {
            return phys.position(id);
        }
        // setter: warp
        return phys.warp(id, vec3(maybeVec3, 0, 0, 0));
    }

    /**
     * warp(handleOrId, vec3)
     */
    function warp(handleOrId, pos) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.warp(handleOrId,pos): body id/handle required");
        return phys.warp(id, vec3(pos, 0, 0, 0));
    }

    /**
     * velocity(handleOrId) -> Vec3
     * velocity(handleOrId, vec3) -> set linear velocity
     */
    function velocity(handleOrId, maybeVec3) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.velocity(handleOrId): body id/handle required");

        if (maybeVec3 === undefined) {
            return phys.velocity(id);
        }
        return phys.velocity(id, vec3(maybeVec3, 0, 0, 0));
    }

    /**
     * yaw(handleOrId, yawRad)
     */
    function yaw(handleOrId, yawRad) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.yaw(handleOrId,yaw): body id/handle required");
        return phys.yaw(id, +yawRad || 0);
    }

    /**
     * applyImpulse(handleOrId, vec3)
     */
    function applyImpulse(handleOrId, impulse) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.applyImpulse(handleOrId,impulse): body id/handle required");
        return phys.applyImpulse(id, vec3(impulse, 0, 0, 0));
    }

    /**
     * lockRotation(handleOrId, bool)
     */
    function lockRotation(handleOrId, lock) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.lockRotation(handleOrId,lock): body id/handle required");
        return phys.lockRotation(id, !!lock);
    }

    function raycastEx(cfg) { return phys.raycastEx(cfg); }
    function raycastAll(cfg) { return phys.raycastAll(cfg); }
    function collisionGroups(handleOrId, group, mask) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.collisionGroups: body required");
        return phys.collisionGroups(id, group|0, mask|0);
    }
    function applyCentralForce(handleOrId, force) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.applyCentralForce: body required");
        return phys.applyCentralForce(id, vec3(force,0,0,0));
    }
    function applyTorque(handleOrId, torque) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.applyTorque: body required");
        return phys.applyTorque(id, vec3(torque,0,0,0));
    }
    function angularVelocity(handleOrId, maybe) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.angularVelocity: body required");
        if (maybe === undefined) return phys.angularVelocity(id);
        return phys.angularVelocity(id, vec3(maybe,0,0,0));
    }
    function clearForces(handleOrId) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.clearForces: body required");
        return phys.clearForces(id);
    }

    /**
     * ref(handleOrId) -> stable JS wrapper bound to bodyId
     * Usage:
     *   const b = PHYS.ref(bodyId);
     *   b.velocity(); b.velocity({x:0,y:0,z:0});
     *   b.position(); b.position([1,2,3]);
     */
    function ref(handleOrId) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.ref(handleOrId): body id/handle required");

        const self = {
            id: () => id,

            // transforms
            position: (maybeVec3) => position(id, maybeVec3),
            warp: (pos) => warp(id, pos),
            velocity: (maybeVec3) => velocity(id, maybeVec3),
            yaw: (yawRad) => yaw(id, yawRad),

            // forces
            applyImpulse: (impulse) => applyImpulse(id, impulse),
            applyCentralForce: (force) => applyCentralForce(id, force),
            applyTorque: (torque) => applyTorque(id, torque),
            angularVelocity: (maybeVec3) => angularVelocity(id, maybeVec3),
            clearForces: () => clearForces(id),

            // flags
            lockRotation: (lock) => lockRotation(id, lock),
            collisionGroups: (group, mask) => collisionGroups(id, group, mask),

            // world queries (not bound but convenient)
            raycast: (cfg) => raycast(cfg),
            raycastEx: (cfg) => raycastEx(cfg),
            raycastAll: (cfg) => raycastAll(cfg),

            // lifecycle
            remove: () => remove(id)
        };

        return Object.freeze(self);
    }


    /**
     * debug(true/false)
     */
    function debug(enabled) {
        try { phys.debug(!!enabled); } catch (e) { warn("[PHYS] debug() failed: " + (e && e.message ? e.message : e)); }
    }

    /**
     * gravity(vec3)
     */
    function gravity(g) {
        try { phys.gravity(vec3(g, 0, -9.81, 0)); } catch (e) { warn("[PHYS] gravity() failed: " + (e && e.message ? e.message : e)); }
    }

    /**
     * Helpers: build collider config quickly (pure JSON)
     */
    const collider = {
        box: (halfExtents /* vec3 */) => ({ type: "box", halfExtents: vec3(halfExtents, 0.5, 0.5, 0.5) }),
        sphere: (radius) => ({ type: "sphere", radius: num(radius, 0.5) }),
        capsule: (radius, height) => ({ type: "capsule", radius: num(radius, 0.35), height: num(height, 1.8) }),
        cylinder: (radius, height) => ({ type: "cylinder", radius: num(radius, 0.5), height: num(height, 1.0) }),
        mesh: () => ({ type: "mesh" }),
        dynamicMesh: () => ({ type: "dynamicMesh" })
    };

    /**
     * Convenience: ensure body exists for a surface.
     * If already created for surface, Java will return existing handle.
     */
    function ensureBodyForSurface(surfaceHandleOrId, cfg) {
        const sid = surfaceIdOf(surfaceHandleOrId);
        if (sid <= 0) throw new Error("PHYS.ensureBodyForSurface(surface,cfg): surface id/handle required");
        const c = cfg ? Object.assign({}, cfg) : {};
        c.surface = sid;
        return body(c);
    }

    return Object.freeze({
        // core
        body,
        remove,
        raycast,

        // transforms
        position,
        warp,
        velocity,
        yaw,

        // forces
        applyImpulse,
        lockRotation,

        // debug/world
        debug,
        gravity,

        // helpers
        collider,
        idOf: bodyIdOf,
        surfaceIdOf,
        vec3,
        ensureBodyForSurface,
        ref
    });
}

module.exports = makeApi;

module.exports.META = Object.freeze({
    name: "Physics",
    globalName: "PHYS",
    version: "1.0.0",
    description: "Rootkit wrapper for engine.physics()",
    engineMin: "0.1.0"
});