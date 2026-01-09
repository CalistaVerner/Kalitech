// FILE: resources/kalitech/builtin/helpers/entity/EntUtil.js
"use strict";

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}

function bool(v, fb) {
    return (v == null) ? !!fb : !!v;
}

function vec3(v, fbX, fbY, fbZ) {
    if (Array.isArray(v)) return [num(v[0], fbX), num(v[1], fbY), num(v[2], fbZ)];
    if (isObj(v)) {
        const x = (v.x != null) ? v.x : v[0];
        const y = (v.y != null) ? v.y : v[1];
        const z = (v.z != null) ? v.z : v[2];
        return [num(x, fbX), num(y, fbY), num(z, fbZ)];
    }
    return [fbX, fbY, fbZ];
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

function req(cond, msg) {
    if (!cond) throw new Error(msg);
}

function errCtx(msg, e) {
    const m = (e && e.stack) ? e.stack : String(e);
    return msg + " :: " + m;
}

function subsystem(engine, name) {
    const v = engine[name];
    if (typeof v === "function") return v.call(engine);
    if (v && typeof v === "object") return v;
    throw new Error(`[ENT] engine.${name} missing`);
}

module.exports = {
    isObj,
    num,
    bool,
    vec3,
    deepMerge,
    req,
    errCtx,
    subsystem
};