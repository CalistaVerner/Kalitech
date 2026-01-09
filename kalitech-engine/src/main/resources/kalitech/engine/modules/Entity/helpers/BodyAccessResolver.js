// FILE: resources/kalitech/builtin/helpers/entity/BodyAccessResolver.js
"use strict";

function pickFirst(obj, names) {
    for (let i = 0; i < names.length; i++) {
        const n = names[i];
        if (obj && typeof obj[n] === "function") return n;
    }
    return "";
}

function resolveBodyAccess(physics, bodyHandle, bodyId) {
    if (!physics) throw new Error("[ENT] physics missing");
    bodyId = bodyId | 0;
    if (bodyId <= 0) throw new Error("[ENT] invalid bodyId=" + bodyId);

    const PHYS_POS = pickFirst(physics, ["position", "location", "getPosition", "getLocation", "bodyPosition"]);
    const PHYS_VGET = pickFirst(physics, ["velocity", "linearVelocity", "getVelocity", "getLinearVelocity", "vel", "getVel"]);
    const PHYS_VSET = pickFirst(physics, ["setVelocity", "setLinearVelocity", "velocitySet", "linearVelocitySet", "setVel"]);
    const PHYS_YAW = pickFirst(physics, ["yaw", "setYaw", "bodyYaw"]);
    const PHYS_TP = pickFirst(physics, ["teleport", "warp", "setPosition", "setLocation", "bodyTeleport"]);

    const BODY_POS = bodyHandle && typeof bodyHandle.position === "function" ? "position" : "";
    const BODY_VGET = bodyHandle ? pickFirst(bodyHandle, ["velocity", "linearVelocity", "getVelocity", "getLinearVelocity", "vel", "getVel"]) : "";
    const BODY_VSET = bodyHandle ? pickFirst(bodyHandle, ["setVelocity", "setLinearVelocity", "velocity", "linearVelocity", "setVel"]) : "";
    const BODY_YAW = bodyHandle ? pickFirst(bodyHandle, ["yaw", "setYaw"]) : "";
    const BODY_TP = bodyHandle ? pickFirst(bodyHandle, ["teleport", "warp", "setPosition", "position"]) : "";
    const BODY_IMP = bodyHandle ? pickFirst(bodyHandle, ["applyCentralImpulse", "applyImpulse", "impulse", "applyForce"]) : "";

    const position = BODY_POS
        ? () => bodyHandle.position()
        : PHYS_POS
            ? () => physics[PHYS_POS](bodyId)
            : null;

    if (!position) throw new Error("[ENT] cannot resolve position getter (body.position or physics.position/location)");

    const getVel = BODY_VGET
        ? () => bodyHandle[BODY_VGET]()
        : PHYS_VGET
            ? () => physics[PHYS_VGET](bodyId)
            : null;

    if (!getVel) throw new Error("[ENT] cannot resolve velocity getter (body or physics)");

    const setVel = BODY_VSET
        ? (v) => bodyHandle[BODY_VSET](v)
        : PHYS_VSET
            ? (v) => physics[PHYS_VSET](bodyId, v)
            : null;

    const applyImpulse = BODY_IMP
        ? (ix, iy, iz) => bodyHandle[BODY_IMP]({x: ix, y: iy, z: iz})
        : null;

    const mode = setVel ? "SET_VEL" : (applyImpulse ? "IMPULSE" : "");
    if (!mode) throw new Error("[ENT] cannot resolve velocity setter nor impulse");

    const setYaw = BODY_YAW
        ? (yaw) => bodyHandle[BODY_YAW](+yaw || 0)
        : PHYS_YAW
            ? (yaw) => physics[PHYS_YAW](bodyId, +yaw || 0)
            : null;

    const teleport = BODY_TP
        ? (x, y, z) => bodyHandle[BODY_TP]({x, y, z})
        : PHYS_TP
            ? (x, y, z) => physics[PHYS_TP](bodyId, {x, y, z})
            : null;

    return Object.freeze({
        bodyId,
        mode,
        position,
        getVel,
        setVel: setVel || null,
        applyImpulse: applyImpulse || null,
        setYaw: setYaw || null,
        teleport: teleport || null
    });
}

module.exports = {resolveBodyAccess};