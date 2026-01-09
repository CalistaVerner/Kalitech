"use strict";

const {isObj, num, normalizePos} = require("./MeshMath.js");

function normalizeCfg(cfg) {
    cfg = isObj(cfg) ? cfg : {};
    const out = Object.assign({}, cfg);

    if (out.type != null) out.type = String(out.type);
    if (out.name != null) out.name = String(out.name);

    if (out.path == null) {
        if (out.model != null) out.path = out.model;
        else if (out.asset != null) out.path = out.asset;
        else if (out.url != null) out.path = out.url;
    }
    if (out.path != null) out.path = String(out.path);

    const p =
        (out.pos != null) ? out.pos :
            (out.position != null) ? out.position :
                (out.loc != null) ? out.loc :
                    (out.location != null) ? out.location :
                        undefined;

    const posN = normalizePos(p);
    if (posN !== undefined) out.pos = posN;

    if (out.radius == null && out.r != null) out.radius = out.r;
    if (out.height == null && out.h != null) out.height = out.h;

    if (out.radius != null) out.radius = num(out.radius, out.radius);
    if (out.height != null) out.height = num(out.height, out.height);

    // physics конфиг — оставляем как есть (без “legacy top-level” магии)
    if (out.physics != null && typeof out.physics === "number") out.physics = {mass: out.physics};

    return out;
}

function withType(type, cfg) {
    const c = normalizeCfg(cfg);
    c.type = String(type);
    return c;
}

function unshadedColor(rgba) {
    const c = Array.isArray(rgba) ? rgba : [1, 1, 1, 1];
    return {def: "Common/MatDefs/Misc/Unshaded.j3md", params: {Color: c}};
}

function physics(mass, opts) {
    const o = opts || {};
    const p = {mass: (mass != null ? mass : 0)};
    if (o.enabled != null) p.enabled = !!o.enabled;
    if (o.lockRotation != null) p.lockRotation = !!o.lockRotation;
    if (o.kinematic != null) p.kinematic = !!o.kinematic;
    if (o.friction != null) p.friction = o.friction;
    if (o.restitution != null) p.restitution = o.restitution;
    if (o.damping != null) p.damping = o.damping;
    if (o.collider != null) p.collider = o.collider;
    return p;
}

module.exports = Object.freeze({
    normalizeCfg,
    withType,
    unshadedColor,
    physics,
});