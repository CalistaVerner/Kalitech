// FILE: resources/kalitech/builtin/helpers/entity/BodyAccessResolver.js
"use strict";

/**
 * Strict body access for EntityCore.
 * No heuristics, no legacy names.
 *
 * Required ENGINE.physics contract:
 *   - position(bodyId) -> {x,y,z} | [x,y,z]
 *   - velocity(bodyId) -> {x,y,z} | [x,y,z]
 *   - velocity(bodyId, vec3) -> void
 * Optional:
 *   - applyImpulse(bodyId, vec3)
 *   - yaw(bodyId, yawRad)
 *   - teleport(bodyId, vec3) or warp(bodyId, vec3)
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

    const setVel = (v) => physics.velocity(id, v);

    const applyImpulse = (typeof physics.applyImpulse === "function")
        ? (ix, iy, iz) => physics.applyImpulse(id, {x: ix, y: iy, z: iz})
        : null;

    const setYaw = (typeof physics.yaw === "function")
        ? (yaw) => physics.yaw(id, +yaw || 0)
        : null;

    return Object.freeze({
        bodyId: id,
        mode: "SET_VEL",
        position: () => physics.position(id),
        getVel: () => physics.velocity(id),
        setVel,
        applyImpulse,
        setYaw,
        teleport: teleportImpl
    });
}

module.exports = {resolveBodyAccess};