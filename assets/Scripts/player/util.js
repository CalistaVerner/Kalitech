// FILE: Scripts/player/util.js
"use strict";

// Минимальные утилиты без "тихих" ошибок.
// Если что-то не так в interop — пусть падает сразу.

function num(v, fb = 0) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function clamp(v, a, b) {
    return v < a ? a : (v > b ? b : v);
}

function isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

// Без глубоких магий — только plain objects.
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

// Жёсткий контракт: vec3 — либо поля x/y/z, либо методы x()/y()/z().
// Никаких try/catch — ошибка должна быть видна сразу.
function vget(v, key, fb = 0) {
    if (!v) return fb;
    const m = v[key];
    if (typeof m === "function") return num(m.call(v), fb);
    return num(m, fb);
}

function vx(v, fb = 0) { return vget(v, "x", fb); }
function vy(v, fb = 0) { return vget(v, "y", fb); }
function vz(v, fb = 0) { return vget(v, "z", fb); }

module.exports = { num, clamp, isPlainObj, deepMerge, vx, vy, vz };