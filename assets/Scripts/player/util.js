// FILE: Scripts/player/util.js
// Author: Calista Verner
"use strict";

/**
 * Tiny runtime helpers for Player stack.
 * Philosophy:
 *  - no silent failures in gameplay code
 *  - tolerate host-interop weirdness, but log it (once) when it happens
 */

const _warned = Object.create(null);

function errStr(e) {
    if (!e) return "unknown";
    if (typeof e === "string") return e;
    return (e && (e.stack || e.message)) ? (e.stack || e.message) : String(e);
}

function warnOnce(key, msg) {
    if (_warned[key]) return;
    _warned[key] = true;
    if (typeof LOG !== "undefined" && LOG && typeof LOG.warn === "function") LOG.warn(msg);
}

function num(v, fb = 0) {
    const n = +v;
    return Number.isFinite(n) ? n : fb;
}

function clamp(v, a, b) {
    return v < a ? a : (v > b ? b : v);
}

function isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function deepMerge(dst, src) {
    if (!isPlainObj(src)) return dst;
    const out = isPlainObj(dst) ? dst : Object.create(null);
    const keys = Object.keys(src);
    for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        const sv = src[k];
        const dv = out[k];
        if (isPlainObj(sv) && isPlainObj(dv)) out[k] = deepMerge(dv, sv);
        else if (isPlainObj(sv)) out[k] = deepMerge(Object.create(null), sv);
        else out[k] = sv;
    }
    return out;
}

function readNum(obj, path, fb = 0) {
    if (!obj) return fb;
    let v = obj;
    for (let i = 0; i < path.length; i++) {
        if (v == null) return fb;
        v = v[path[i]];
    }
    return num(v, fb);
}

function readBool(obj, path, fb = false) {
    if (!obj) return fb;
    let v = obj;
    for (let i = 0; i < path.length; i++) {
        if (v == null) return fb;
        v = v[path[i]];
    }
    return (v === undefined) ? fb : !!v;
}

// JS/Java vec3 accessor (x/y/z may be field or method)
function vget(v, key, fb = 0) {
    if (!v) return fb;
    const m = v[key];
    if (typeof m === "function") {
        try { return num(m.call(v), fb); }
        catch (e) {
            warnOnce("vget_fn_" + key, "[util] vec." + key + "() threw: " + errStr(e));
            return fb;
        }
    }
    return num(m, fb);
}

function vx(v, fb = 0) { return vget(v, "x", fb); }
function vy(v, fb = 0) { return vget(v, "y", fb); }
function vz(v, fb = 0) { return vget(v, "z", fb); }

function setVec3(out, x, y, z) {
    out.x = x;
    out.y = y;
    out.z = z;
    return out;
}

module.exports = {
    num,
    clamp,
    isPlainObj,
    deepMerge,
    readNum,
    readBool,

    vx,
    vy,
    vz,
    setVec3,

    warnOnce,
    errStr
};
