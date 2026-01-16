// FILE: resources/kalitech/builtin/helpers/entity/BodyAccessResolver.js
"use strict";

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

    const setVel = (v) => physics.velocity(id, v);

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

        position: () => physics.position(id),

        getVel: () => physics.velocity(id),
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