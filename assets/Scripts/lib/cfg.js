// FILE: Scripts/lib/cfg.js
// Author: Calista Verner
//
// Safe config reader utilities.
// No engine access. No side effects.

const M = require("@core/math");

function isNum(v) {
    return typeof v === "number" && !Number.isNaN(v) && Number.isFinite(v);
}

function isObj(v) {
    return v !== null && typeof v === "object" && !Array.isArray(v);
}

function num(src, key, def, min, max) {
    const v = src?.[key];
    if (!isNum(v)) return def;
    if (min !== undefined && max !== undefined) return M.clamp(v, min, max);
    return v;
}

function int(src, key, def, min, max) {
    const v = src?.[key];
    if (!isNum(v)) return def;
    const iv = v | 0;
    if (min !== undefined && max !== undefined) return M.clamp(iv, min, max);
    return iv;
}

function bool(src, key, def) {
    const v = src?.[key];
    if (typeof v !== "boolean") return def;
    return v;
}

function str(src, key, def) {
    const v = src?.[key];
    if (typeof v !== "string" || v.length === 0) return def;
    return v;
}

function obj(src, key, def) {
    const v = src?.[key];
    if (!isObj(v)) return def;
    return v;
}

function color(src, key, def) {
    const c = src?.[key];
    if (!isObj(c)) return def;
    return {
        r: M.clamp(isNum(c.r) ? c.r : def.r, 0, 1),
        g: M.clamp(isNum(c.g) ? c.g : def.g, 0, 1),
        b: M.clamp(isNum(c.b) ? c.b : def.b, 0, 1),
        a: def.a !== undefined
            ? M.clamp(isNum(c.a) ? c.a : def.a, 0, 1)
            : undefined
    };
}

function vec3(src, key, def) {
    const v = src?.[key];
    if (!isObj(v)) return def;
    return {
        x: isNum(v.x) ? v.x : def.x,
        y: isNum(v.y) ? v.y : def.y,
        z: isNum(v.z) ? v.z : def.z
    };
}

module.exports = {
    num,
    int,
    bool,
    str,
    obj,
    color,
    vec3
};