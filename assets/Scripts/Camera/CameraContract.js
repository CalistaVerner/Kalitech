"use strict";

function fail(msg) { throw new Error(msg); }
function isFn(x) { return typeof x === "function"; }
function isObj(x) { return x && typeof x === "object"; }

function req(v, msg) {
    if (v == null) fail(msg);
    return v;
}

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
    req(player, "[camera] player is required");

    if (!isFn(player.getBodyId)) fail("[camera] player.getBodyId() required");
    if (!isFn(player.getModel)) fail("[camera] player.getModel() required");

    const d = req(player.d, "[camera] player.d is required (camera deps)");

    // camera API used by orchestrator
    const cam = req(d.camera, "[camera] player.d.camera is required");
    if (!isFn(cam.setYawPitch)) fail("[camera] d.camera.setYawPitch(yaw,pitch) required");
    if (!isFn(cam.setLocation)) fail("[camera] d.camera.setLocation(x,y,z) required");
    if (!isFn(cam.location)) fail("[camera] d.camera.location() required");

    // physics API used by camera (and collision solver)
    const ph = req(d.physics, "[camera] player.d.physics is required");
    if (!isFn(ph.position)) fail("[camera] d.physics.position(bodyId) required");
    // raycast is required only if third-person collision is enabled by mode/meta,
    // collision solver itself will enforce it when used.
    // Do NOT silently accept missing raycast: solver will throw when called.

    // input API used for V switch key + mouse deltas are in snap
    const inp = req(d.input, "[camera] player.d.input is required");
    if (!isFn(inp.keyCode)) fail("[camera] d.input.keyCode(key) required");

    return true;
}

function validateMeta(meta) {
    if (!isObj(meta)) fail("[camera] mode.meta required (object)");

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

module.exports = {validatePlayer, validateMode};