// FILE: resources/kalitech/engine/bootstrap/Util.js
"use strict";

function safeJson(v) {
    try {
        return JSON.stringify(v);
    } catch (_) {
        return String(v);
    }
}

function deepMergePlain(dst, src) {
    if (!src || typeof src !== "object") return dst;
    if (!dst || typeof dst !== "object") dst = {};
    for (const k of Object.keys(src)) {
        const sv = src[k];
        const dv = dst[k];
        if (sv && typeof sv === "object" && !Array.isArray(sv)) dst[k] = deepMergePlain(dv, sv);
        else dst[k] = sv;
    }
    return dst;
}

function parseSemver(v) {
    if (!v || typeof v !== "string") return null;
    const m = v.trim().match(/^(\d+)\.(\d+)\.(\d+)/);
    if (!m) return null;
    return [m[1] | 0, m[2] | 0, m[3] | 0];
}

function semverGte(a, b) {
    const A = parseSemver(String(a || ""));
    const B = parseSemver(String(b || ""));
    if (!A || !B) return true;
    if (A[0] !== B[0]) return A[0] > B[0];
    if (A[1] !== B[1]) return A[1] > B[1];
    return A[2] >= B[2];
}

function readEngineVersion(engine) {
    try {
        if (!engine) return null;
        if (typeof engine.version === "function") return String(engine.version());
        if (typeof engine.version === "string") return engine.version;
        if (engine.info && typeof engine.info === "function") {
            const info = engine.info();
            if (info && info.version) return String(info.version);
        }
    } catch (_) {
    }
    return null;
}

function isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function isObj(x) {
    return x && typeof x === "object";
}

function readJsonSafe(text) {
    try {
        return JSON.parse(String(text == null ? "" : text));
    } catch (_) {
        return null;
    }
}

function dirOf(p) {
    p = String(p || "");
    const a = p.lastIndexOf("/");
    const b = p.lastIndexOf("\\");
    const i = Math.max(a, b);
    return i >= 0 ? p.slice(0, i) : "";
}

module.exports = {
    safeJson,
    deepMergePlain,
    parseSemver,
    semverGte,
    readEngineVersion,
    isPlainObj,
    isObj,
    readJsonSafe,
    dirOf
};