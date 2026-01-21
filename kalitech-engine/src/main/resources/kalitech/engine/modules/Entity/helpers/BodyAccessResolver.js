// FILE: @builtin/modules/Entity/helpers/BodyAccessResolver.js
"use strict";

function req(cond, msg) {
    if (!cond) throw new Error(msg);
}

function isFn(v) {
    return typeof v === "function";
}

function v3(a, b, c) {
    if (a && typeof a === "object") return a;
    return {x: +a || 0, y: +b || 0, z: +c || 0};
}

function quat(a, b, c, d) {
    if (a && typeof a === "object") return a;
    return {x: +a || 0, y: +b || 0, z: +c || 0, w: (d !== undefined) ? (+d || 0) : 1};
}

/**
 * Canonical body access adapter for PhysicsApiImpl (ops by handleOrId).
 * Provides a stable API plus compatibility aliases for legacy gameplay scripts.
 */
function resolveBodyAccess(physicsApi, _unused, bodyId) {
    req(physicsApi, "[BodyAccess] physics api is required");

    const id = bodyId | 0;
    req(id > 0, "[BodyAccess] bodyId must be > 0");

    req(isFn(physicsApi.exists), "[BodyAccess] physics.exists(id) required");
    req(physicsApi.exists(id), "[BodyAccess] body does not exist (id=" + id + ")");

    req(isFn(physicsApi.position), "[BodyAccess] physics.position(handleOrId) required");
    req(isFn(physicsApi.velocity), "[BodyAccess] physics.velocity(handleOrId[,vec3]) required");
    req(isFn(physicsApi.angularVelocity), "[BodyAccess] physics.angularVelocity(handleOrId[,vec3]) required");
    req(isFn(physicsApi.applyImpulse), "[BodyAccess] physics.applyImpulse(handleOrId,vec3) required");
    req(isFn(physicsApi.warp), "[BodyAccess] physics.warp(handleOrId,vec3) required");

    const hasRotation = isFn(physicsApi.rotation);
    const hasYaw = isFn(physicsApi.yaw);

    const api = Object.freeze({
        bodyId: id,

        // Canonical names
        position: function () {
            return physicsApi.position(id);
        },
        velocity: function () {
            return physicsApi.velocity(id);
        },
        setVelocity: function (a, b, c) {
            physicsApi.velocity(id, v3(a, b, c));
        },

        angularVelocity: function () {
            return physicsApi.angularVelocity(id);
        },
        setAngularVelocity: function (a, b, c) {
            physicsApi.angularVelocity(id, v3(a, b, c));
        },

        applyImpulse: function (a, b, c) {
            physicsApi.applyImpulse(id, v3(a, b, c));
        },
        warp: function (a, b, c) {
            physicsApi.warp(id, v3(a, b, c));
        },

        rotation: function () {
            req(hasRotation, "[BodyAccess] physics.rotation(handleOrId) missing in PhysicsApiImpl");
            return physicsApi.rotation(id);
        },

        setRotation: function (a, b, c, d) {
            req(hasRotation, "[BodyAccess] physics.rotation(handleOrId,quat) missing in PhysicsApiImpl");
            physicsApi.rotation(id, quat(a, b, c, d));
        },

        yaw: function (y) {
            req(hasYaw, "[BodyAccess] physics.yaw(handleOrId,yaw) missing in PhysicsApiImpl");
            physicsApi.yaw(id, +y || 0);
        },

        // Compatibility aliases (legacy gameplay)
        getPos: function () {
            return physicsApi.position(id);
        },
        setPos: function (a, b, c) {
            physicsApi.warp(id, v3(a, b, c));
        },

        getVel: function () {
            return physicsApi.velocity(id);
        },
        setVel: function (a, b, c) {
            physicsApi.velocity(id, v3(a, b, c));
        },

        getAngVel: function () {
            return physicsApi.angularVelocity(id);
        },
        setAngVel: function (a, b, c) {
            physicsApi.angularVelocity(id, v3(a, b, c));
        },

        getRot: function () {
            req(hasRotation, "[BodyAccess] physics.rotation(handleOrId) missing in PhysicsApiImpl");
            return physicsApi.rotation(id);
        },
        setRot: function (a, b, c, d) {
            req(hasRotation, "[BodyAccess] physics.rotation(handleOrId,quat) missing in PhysicsApiImpl");
            physicsApi.rotation(id, quat(a, b, c, d));
        },

        impulse: function (a, b, c) {
            physicsApi.applyImpulse(id, v3(a, b, c));
        },
        teleport: function (a, b, c) {
            physicsApi.warp(id, v3(a, b, c));
        }
    });

    return api;
}

module.exports = {resolveBodyAccess};