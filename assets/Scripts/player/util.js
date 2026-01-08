"use strict";

function num(v, fb = 0) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function clamp(v, a, b) {
    return v < a ? a : (v > b ? b : v);
}

function vget(v, key, fb = 0) {
    const m = v && v[key];
    const n = (typeof m === "function") ? num(m.call(v), fb) : num(m, fb);
    return Number.isFinite(n) ? n : fb;
}

function vx(v, fb = 0) {
    return vget(v, "x", fb);
}

function vy(v, fb = 0) {
    return vget(v, "y", fb);
}

function vz(v, fb = 0) {
    return vget(v, "z", fb);
}

function isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function deepMerge(dst, src) {
    if (!isPlainObj(src)) return isPlainObj(dst) ? dst : Object.create(null);

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

module.exports = {num, clamp, vx, vy, vz, isPlainObj, deepMerge};