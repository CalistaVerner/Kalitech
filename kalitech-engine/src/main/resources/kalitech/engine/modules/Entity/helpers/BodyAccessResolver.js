// FILE: resources/kalitech/builtin/helpers/entity/BodyAccessResolver.js
"use strict";

function isObj(v) {
    return !!v && typeof v === "object";
}

function hasXYZ(v) {
    return isObj(v) && ("x" in v || "y" in v || "z" in v);
}

function has012(v) {
    return isObj(v) && (0 in v || 1 in v || 2 in v);
}

/**
 * Normalizes vec3-like values into an object with {x,y,z}.
 *
 * Accepts:
 *  - {x,y,z}
 *  - [x,y,z]
 *  - {0,1,2}
 */
function vec3Obj(v, fbX = 0, fbY = 0, fbZ = 0) {
    if (!v) return {x: fbX, y: fbY, z: fbZ};

    if (Array.isArray(v)) {
        const x = +v[0];
        const y = +v[1];
        const z = +v[2];
        return {
            x: Number.isFinite(x) ? x : fbX,
            y: Number.isFinite(y) ? y : fbY,
            z: Number.isFinite(z) ? z : fbZ
        };
    }

    if (hasXYZ(v)) return v;

    if (has012(v)) {
        const x = +v[0];
        const y = +v[1];
        const z = +v[2];
        return {
            x: Number.isFinite(x) ? x : fbX,
            y: Number.isFinite(y) ? y : fbY,
            z: Number.isFinite(z) ? z : fbZ
        };
    }

    return {x: fbX, y: fbY, z: fbZ};
}

function vec3In(v, fbX = 0, fbY = 0, fbZ = 0) {
    // Always send {x,y,z} into the physics API.
    const o = vec3Obj(v, fbX, fbY, fbZ);
    return {x: +o.x || 0, y: +o.y || 0, z: +o.z || 0};
}

/**
 * Unified strict body access for EntityCore/EntityHandle.
 *
 * Required ENGINE.physics contract:
 *   - position(bodyId) -> {x,y,z} | [x,y,z]
 *   - velocity(bodyId) -> {x,y,z} | [x,y,z]
 *   - velocity(bodyId, vec3) -> void
 *
 * Optional:
 *   - teleport(bodyId, vec3) or warp(bodyId, vec3)
 *   - applyImpulse(bodyId, vec3)
 *   - applyCentralForce(bodyId, vec3)
 *   - yaw(bodyId, yawRad)
 *   - lockRotation(bodyId, bool)
 *   - remove(bodyId)
 */
function resolveBodyAccess(physics, _bodyHandle, bodyId) {
    if (!physics) throw new Error("[ENT] physics missing");
    const id = bodyId | 0;
    if (id <= 0) throw new Error("[ENT] invalid bodyId=" + id);

    if (typeof physics.position !== "function") throw new Error("[ENT] ENGINE.physics.position(bodyId) missing");
    if (typeof physics.velocity !== "function") throw new Error("[ENT] ENGINE.physics.velocity(bodyId[,vec3]) missing");

    const hasTeleport = typeof physics.teleport === "function";
    const hasWarp = typeof physics.warp === "function";

    const teleportImpl = hasTeleport
        ? (x, y, z) => physics.teleport(id, {x, y, z})
        : hasWarp
            ? (x, y, z) => physics.warp(id, {x, y, z})
            : null;

    const setVel = (v) => physics.velocity(id, vec3In(v, 0, 0, 0));

    const applyImpulse = (typeof physics.applyImpulse === "function")
        ? (ix, iy, iz) => physics.applyImpulse(id, {x: ix, y: iy, z: iz})
        : null;

    const applyCentralForce = (typeof physics.applyCentralForce === "function")
        ? (fx, fy, fz) => physics.applyCentralForce(id, {x: fx, y: fy, z: fz})
        : null;

    const setYaw = (typeof physics.yaw === "function")
        ? (yaw) => physics.yaw(id, +yaw || 0)
        : null;

    const lockRotation = (typeof physics.lockRotation === "function")
        ? (lock) => physics.lockRotation(id, !!lock)
        : null;

    const remove = (typeof physics.remove === "function")
        ? () => physics.remove(id)
        : null;

    return Object.freeze({
        bodyId: id,

        /**
         * Movement should use velocity-set mode for determinism and frame-rate stability.
         */
        mode: "SET_VEL",

        position: () => vec3Obj(physics.position(id), 0, 0, 0),

        getVel: () => vec3Obj(physics.velocity(id), 0, 0, 0),
        setVel,

        teleport: teleportImpl,

        applyImpulse,
        applyCentralForce,

        setYaw,
        lockRotation,

        remove
    });
}

module.exports = {resolveBodyAccess};