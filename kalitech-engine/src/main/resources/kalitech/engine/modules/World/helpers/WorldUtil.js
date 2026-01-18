// FILE: resources/kalitech/builtin/helpers/world/WorldUtil.js
// (ваш файл, без изменений)
"use strict";

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function req(cond, msg) {
    if (!cond) throw new Error(msg);
}

function deepMerge(dst, src) {
    dst = (dst && typeof dst === "object") ? dst : {};
    if (!src || typeof src !== "object") return dst;

    for (const k of Object.keys(src)) {
        const sv = src[k];
        const dv = dst[k];
        if (sv && typeof sv === "object" && !Array.isArray(sv)) dst[k] = deepMerge(dv, sv);
        else dst[k] = sv;
    }
    return dst;
}

function subsystem(engine, name) {
    const v = engine[name];
    if (typeof v === "function") return v.call(engine);
    if (v && typeof v === "object") return v;
    throw new Error(`[WORLD] engine.${name} missing`);
}

function str(v, fb) {
    const s = (v == null) ? "" : String(v);
    const t = s.trim();
    return t ? t : (fb != null ? String(fb) : "");
}

function bool(v, fb) {
    return (v == null) ? !!fb : !!v;
}

function numInt(v, fb) {
    const n = +v;
    if (!Number.isFinite(n)) return (fb | 0);
    return (n | 0);
}

module.exports = {isObj, req, deepMerge, subsystem, str, bool, numInt};