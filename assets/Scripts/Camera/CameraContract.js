"use strict";

function fail(msg) { throw new Error(msg); }
function isFn(x) { return typeof x === "function"; }
function isObj(x) { return x && typeof x === "object"; }

function asBool(v, name) {
    if (typeof v !== "boolean") fail("[camera] meta." + name + " must be boolean");
    return v;
}
function asInt(v, name) {
    if (!Number.isFinite(v) || (v | 0) !== v) fail("[camera] meta." + name + " must be int");
    return v | 0;
}
function asStr(v, name) {
    if (typeof v !== "string" || !v.trim()) fail("[camera] " + name + " must be non-empty string");
    return v.trim();
}

function validatePlayer(player) {
    if (!player) fail("[camera] player is required");
    if (!isFn(player.getBodyId)) fail("[camera] player.getBodyId() required");
    if (!isFn(player.getModel)) fail("[camera] player.getModel() required");
    return true;
}

function validateEngine(engine) {
    if (!engine) fail("[camera] global engine is missing");

    const cam = engine.camera && engine.camera();
    if (!cam) fail("[camera] engine.camera() is null");

    if (!isFn(cam.setYawPitch)) fail("[camera] camera.setYawPitch(yaw,pitch) required");
    if (!isFn(cam.setLocation)) fail("[camera] camera.setLocation(x,y,z) required");
    if (!isFn(cam.location)) fail("[camera] camera.location() required");

    const ph = engine.physics && engine.physics();
    if (!ph) fail("[camera] engine.physics() is null");
    if (!isFn(ph.position)) fail("[camera] physics.position(bodyId) required");

    return true;
}

function validateMeta(meta) {
    if (!isObj(meta)) fail("[camera] mode.meta required (object)");

    // STRICT allowed keys
    const allowed = { supportsZoom: 1, hasCollision: 1, numRays: 1, playerModelVisible: 1 };
    for (const k in meta) if (!allowed[k]) fail("[camera] mode.meta has unknown key: " + k);

    return {
        supportsZoom: asBool(meta.supportsZoom, "supportsZoom"),
        hasCollision: asBool(meta.hasCollision, "hasCollision"),
        numRays: asInt(meta.numRays, "numRays"),
        playerModelVisible: asBool(meta.playerModelVisible, "playerModelVisible")
    };
}

function validateMode(mode) {
    if (!isObj(mode)) fail("[camera] mode is null");
    mode.id = asStr(mode.id, "mode.id");
    mode.meta = validateMeta(mode.meta);
    if (!isFn(mode.update)) fail("[camera] mode.update(ctx) required");
    return mode;
}

module.exports = { validatePlayer, validateEngine, validateMode };