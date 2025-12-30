// FILE: resources/kalitech/builtin/Physics/Physics.js
// Author: Calista Verner
"use strict";

const META = {
    name: "Physics",
    globalName: "PHYS",
    version: "1.0.2",
    engineMin: "0.1.0",
};

function num(x, def = 0) { x = Number(x); return Number.isFinite(x) ? x : def; }
function isObj(x) { return x && typeof x === "object"; }

function warn(s) {
    try { if (typeof LOG !== "undefined" && LOG && LOG.warn) LOG.warn(String(s)); } catch (_) {}
}

function vec3Obj(v, dx, dy, dz) {
    if (Array.isArray(v)) return { x: num(v[0], dx), y: num(v[1], dy), z: num(v[2], dz) };
    if (isObj(v)) return { x: num(v.x, dx), y: num(v.y, dy), z: num(v.z, dz) };
    return { x: dx, y: dy, z: dz };
}
function vec3Arr(v, dx, dy, dz) {
    if (Array.isArray(v)) return [num(v[0], dx), num(v[1], dy), num(v[2], dz)];
    if (isObj(v)) return [num(v.x, dx), num(v.y, dy), num(v.z, dz)];
    return [dx, dy, dz];
}

function makeApi(engine) {
    if (!engine) throw new Error("[PHYS] engine is required");
    const phys = engine.physics ? engine.physics() : null;
    if (!phys) throw new Error("[PHYS] engine.physics() is not available");

    function surfaceIdOf(surfaceHandleOrId) {
        if (typeof surfaceHandleOrId === "number") return surfaceHandleOrId | 0;
        if (!surfaceHandleOrId) return 0;
        if (typeof surfaceHandleOrId.id === "function") return surfaceHandleOrId.id() | 0;
        if (typeof surfaceHandleOrId.id === "number") return surfaceHandleOrId.id | 0;
        if (typeof surfaceHandleOrId.surfaceId === "number") return surfaceHandleOrId.surfaceId | 0;
        return 0;
    }

    function bodyIdOf(handleOrId) {
        if (typeof handleOrId === "number") return handleOrId | 0;
        if (!handleOrId) return 0;
        if (typeof handleOrId.id === "function") return handleOrId.id() | 0;
        if (typeof handleOrId.id === "number") return handleOrId.id | 0;
        if (typeof handleOrId.bodyId === "number") return handleOrId.bodyId | 0;
        return 0;
    }

    function body(cfg) {
        if (!cfg || typeof cfg !== "object") throw new Error("PHYS.body(cfg): cfg object required");
        return phys.body(cfg);
    }

    function remove(handleOrId) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) return;
        return phys.remove(id);
    }

    function raycast(cfg) {
        if (!cfg || typeof cfg !== "object") throw new Error("PHYS.raycast(cfg): cfg object required");
        const c = Object.assign({}, cfg);
        c.from = vec3Arr(cfg.from, 0, 0, 0);
        c.to = vec3Arr(cfg.to, 0, -1, 0);
        return phys.raycast(c);
    }

    function raycastEx(cfg) {
        if (!cfg || typeof cfg !== "object") throw new Error("PHYS.raycastEx(cfg): cfg object required");
        if (!phys.raycastEx) throw new Error("PHYS.raycastEx: engine physics API missing raycastEx()");
        const c = Object.assign({}, cfg);
        c.from = vec3Arr(cfg.from, 0, 0, 0);
        c.to = vec3Arr(cfg.to, 0, -1, 0);
        return phys.raycastEx(c);
    }

    function raycastAll(cfg) {
        if (!cfg || typeof cfg !== "object") throw new Error("PHYS.raycastAll(cfg): cfg object required");
        if (!phys.raycastAll) throw new Error("PHYS.raycastAll: engine physics API missing raycastAll()");
        const c = Object.assign({}, cfg);
        c.from = vec3Arr(cfg.from, 0, 0, 0);
        c.to = vec3Arr(cfg.to, 0, -1, 0);
        return phys.raycastAll(c);
    }

    function position(handleOrId, maybeVec3) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.position(handleOrId): body id/handle required");
        if (maybeVec3 !== undefined) {
            return phys.warp(id, vec3Obj(maybeVec3, 0, 0, 0));
        }
        return phys.position(id);
    }

    function warp(handleOrId, pos) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.warp(handleOrId,pos): body id/handle required");
        return phys.warp(id, vec3Obj(pos, 0, 0, 0));
    }

    function velocity(handleOrId, maybeVec3) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.velocity(handleOrId): body id/handle required");
        if (maybeVec3 !== undefined) {
            return phys.velocity(id, vec3Obj(maybeVec3, 0, 0, 0));
        }
        return phys.velocity(id);
    }

    function yaw(handleOrId, yawRad) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.yaw(handleOrId,yaw): body id/handle required");
        return phys.yaw(id, num(yawRad, 0));
    }

    function applyImpulse(handleOrId, impulse) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.applyImpulse(handleOrId,impulse): body id/handle required");
        return phys.applyImpulse(id, vec3Obj(impulse, 0, 0, 0));
    }

    function lockRotation(handleOrId, lock) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.lockRotation(handleOrId,lock): body id/handle required");
        return phys.lockRotation(id, !!lock);
    }

    function setKinematic(handleOrId, kinematic) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.setKinematic(handleOrId,kinematic): body id/handle required");
        if (!phys.setKinematic) throw new Error("PHYS.setKinematic: engine physics API missing setKinematic()");
        return phys.setKinematic(id, !!kinematic);
    }

    function debug(enabled) {
        try { phys.debug(!!enabled); } catch (e) { warn("[PHYS] debug() failed: " + (e && e.message ? e.message : e)); }
    }

    function gravity(g) {
        try { phys.gravity(vec3Obj(g, 0, -9.81, 0)); } catch (e) { warn("[PHYS] gravity() failed: " + (e && e.message ? e.message : e)); }
    }

    const collider = {
        box: (halfExtents) => ({ type: "box", halfExtents: vec3Obj(halfExtents, 0.5, 0.5, 0.5) }),
        sphere: (radius) => ({ type: "sphere", radius: num(radius, 0.5) }),
        capsule: (radius, height) => ({ type: "capsule", radius: num(radius, 0.35), height: num(height, 1.8) }),
        cylinder: (radius, height) => ({ type: "cylinder", radius: num(radius, 0.5), height: num(height, 1.0) }),
        mesh: () => ({ type: "mesh" }),
        dynamicMesh: () => ({ type: "dynamicMesh" })
    };

    function ensureBodyForSurface(surfaceHandleOrId, cfg) {
        const sid = surfaceIdOf(surfaceHandleOrId);
        if (sid <= 0) throw new Error("PHYS.ensureBodyForSurface(surface,cfg): surface id/handle required");
        const c = cfg ? Object.assign({}, cfg) : {};
        c.surface = sid;
        return body(c);
    }

    function ref(handleOrId) {
        const id = bodyIdOf(handleOrId);
        if (id <= 0) throw new Error("PHYS.ref(handleOrId): body id/handle required");

        const self = {
            id: () => id,
            position: (maybeVec3) => position(id, maybeVec3),
            warp: (pos) => warp(id, pos),
            velocity: (maybeVec3) => velocity(id, maybeVec3),
            yaw: (yawRad) => yaw(id, yawRad),
            applyImpulse: (imp) => applyImpulse(id, imp),
            lockRotation: (lock) => lockRotation(id, lock),
            setKinematic: (k) => setKinematic(id, k),
            raycast: (cfg) => raycast(cfg),
            raycastEx: (cfg) => raycastEx(cfg),
            raycastAll: (cfg) => raycastAll(cfg),
            remove: () => remove(id)
        };

        return Object.freeze(self);
    }

    return Object.freeze({
        body,
        remove,

        raycast,
        raycastEx,
        raycastAll,

        position,
        warp,
        velocity,
        yaw,

        applyImpulse,
        lockRotation,
        setKinematic,

        debug,
        gravity,

        collider,
        idOf: bodyIdOf,
        surfaceIdOf,
        vec3: vec3Obj,
        ensureBodyForSurface,
        ref
    });
}

module.exports = makeApi;
module.exports.META = Object.freeze(META);