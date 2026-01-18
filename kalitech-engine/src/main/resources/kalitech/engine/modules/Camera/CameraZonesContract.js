"use strict";

function fail(msg) {
    throw new Error(msg);
}

function isObj(x) {
    return x && typeof x === "object";
}

function isNum(x) {
    return typeof x === "number" && Number.isFinite(x);
}

function isInt(x) {
    return isNum(x) && (x | 0) === x;
}

function isBool(x) {
    return typeof x === "boolean";
}

function isStr(x) {
    return typeof x === "string" && !!x.trim();
}

function req(v, msg) {
    if (v == null) fail(msg);
    return v;
}

function asNum(v, name) {
    if (!isNum(v)) fail("[camera][zones] " + name + " must be number");
    return v;
}

function asInt(v, name) {
    if (!isInt(v)) fail("[camera][zones] " + name + " must be int");
    return v | 0;
}

function asBool(v, name) {
    if (!isBool(v)) fail("[camera][zones] " + name + " must be boolean");
    return !!v;
}

function asStr(v, name) {
    if (!isStr(v)) fail("[camera][zones] " + name + " must be non-empty string");
    return v.trim();
}

function asVec3(v, name) {
    if (Array.isArray(v)) {
        if (v.length !== 3) fail("[camera][zones] " + name + " must have 3 elements");
        const x = asNum(v[0], name + "[0]");
        const y = asNum(v[1], name + "[1]");
        const z = asNum(v[2], name + "[2]");
        return {x, y, z};
    }
    if (isObj(v)) {
        return {x: asNum(v.x, name + ".x"), y: asNum(v.y, name + ".y"), z: asNum(v.z, name + ".z")};
    }
    fail("[camera][zones] " + name + " must be vec3 (array[3] or {x,y,z})");
}

function validateAabb(aabb, zoneId) {
    req(aabb, "[camera][zones] zone '" + zoneId + "' shape.aabb is required");
    const min = asVec3(req(aabb.min, "[camera][zones] zone '" + zoneId + "' aabb.min required"), "aabb.min");
    const max = asVec3(req(aabb.max, "[camera][zones] zone '" + zoneId + "' aabb.max required"), "aabb.max");

    if (!(max.x > min.x && max.y > min.y && max.z > min.z)) {
        fail("[camera][zones] zone '" + zoneId + "' aabb must satisfy max>min on all axes");
    }

    return {min, max};
}

function validateOverrides(over, zoneId) {
    if (!isObj(over)) fail("[camera][zones] zone '" + zoneId + "' overrides must be an object");

    // Allowed keys (strict)
    const allowed = {
        // third-person camera behavior
        pivotOffset: 1,        // vec3
        verticalLift: 1,       // number
        minPitch: 1,           // number (rad)
        maxPitch: 1,           // number (rad)

        // zoom clamping
        zoomMin: 1,            // number
        zoomMax: 1,            // number

        // collision tuning
        collisionEnabled: 1,   // boolean
        camRadius: 1,          // number
        surfacePadding: 1,     // number
        floorPadding: 1,       // number

        // shoulder swap / bias (optional)
        shoulderX: 1           // number
    };

    for (const k in over) if (!allowed[k]) fail("[camera][zones] zone '" + zoneId + "' overrides has unknown key: " + k);

    const out = Object.create(null);

    if (over.pivotOffset != null) out.pivotOffset = asVec3(over.pivotOffset, "overrides.pivotOffset");
    if (over.verticalLift != null) out.verticalLift = asNum(over.verticalLift, "overrides.verticalLift");

    if (over.minPitch != null) out.minPitch = asNum(over.minPitch, "overrides.minPitch");
    if (over.maxPitch != null) out.maxPitch = asNum(over.maxPitch, "overrides.maxPitch");

    if (over.zoomMin != null) out.zoomMin = asNum(over.zoomMin, "overrides.zoomMin");
    if (over.zoomMax != null) out.zoomMax = asNum(over.zoomMax, "overrides.zoomMax");

    if (over.collisionEnabled != null) out.collisionEnabled = asBool(over.collisionEnabled, "overrides.collisionEnabled");
    if (over.camRadius != null) out.camRadius = asNum(over.camRadius, "overrides.camRadius");
    if (over.surfacePadding != null) out.surfacePadding = asNum(over.surfacePadding, "overrides.surfacePadding");
    if (over.floorPadding != null) out.floorPadding = asNum(over.floorPadding, "overrides.floorPadding");

    if (over.shoulderX != null) out.shoulderX = asNum(over.shoulderX, "overrides.shoulderX");

    // Sanity checks (when both bounds are provided)
    if (out.zoomMin != null && out.zoomMax != null && !(out.zoomMax >= out.zoomMin)) {
        fail("[camera][zones] zone '" + zoneId + "' overrides zoomMax must be >= zoomMin");
    }
    if (out.minPitch != null && out.maxPitch != null && !(out.maxPitch >= out.minPitch)) {
        fail("[camera][zones] zone '" + zoneId + "' overrides maxPitch must be >= minPitch");
    }

    return out;
}

function validateZone(z, idx) {
    if (!isObj(z)) fail("[camera][zones] zones[" + idx + "] must be an object");

    const id = asStr(z.id, "zones[" + idx + "].id");
    const priority = asInt(z.priority, "zones[" + idx + "].priority");
    const blend = (z.blend != null) ? asNum(z.blend, "zones[" + idx + "].blend") : 0;

    if (blend < 0) fail("[camera][zones] zone '" + id + "' blend must be >= 0");

    const shape = req(z.shape, "[camera][zones] zone '" + id + "' shape is required (object)");
    if (!isObj(shape)) fail("[camera][zones] zone '" + id + "' shape must be object");
    const allowedShape = {aabb: 1};
    for (const k in shape) if (!allowedShape[k]) fail("[camera][zones] zone '" + id + "' shape has unknown key: " + k);

    const aabb = validateAabb(shape.aabb, id);

    const overrides = req(z.overrides, "[camera][zones] zone '" + id + "' overrides is required");
    const o = validateOverrides(overrides, id);

    return {id, priority, blend, shape: {aabb}, overrides: o};
}

function validateZonesConfig(cfg) {
    if (!isObj(cfg)) fail("[camera][zones] cfg must be object");
    if (!isBool(cfg.enabled)) fail("[camera][zones] cfg.enabled must be boolean");
    if (!cfg.enabled) return {enabled: false, zones: []};

    const zones = req(cfg.zones, "[camera][zones] cfg.zones is required when enabled=true");
    if (!Array.isArray(zones) || zones.length === 0) fail("[camera][zones] cfg.zones must be a non-empty array");

    const out = [];
    const seen = Object.create(null);

    for (let i = 0; i < zones.length; i++) {
        const z = validateZone(zones[i], i);
        if (seen[z.id]) fail("[camera][zones] duplicate zone id: " + z.id);
        seen[z.id] = true;
        out.push(z);
    }

    return {enabled: true, zones: out};
}

module.exports = {validateZonesConfig};
