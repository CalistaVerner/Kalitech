"use strict";

function _isObj(x) { return x && typeof x === "object"; }

function _readJsonSafe(text) {
    try { return JSON.parse(String(text == null ? "" : text)); } catch { return null; }
}

function _dirOf(p) {
    p = String(p || "");
    const a = p.lastIndexOf("/");
    const b = p.lastIndexOf("\\");
    const i = Math.max(a, b);
    return i >= 0 ? p.slice(0, i) : "";
}

function _join(a, b) {
    a = String(a || "");
    b = String(b || "");
    if (!a) return b;
    if (!b) return a;
    const sep = a.includes("\\") ? "\\" : "/";
    const aa = a.replace(/[\/\\]+$/g, "");
    const bb = b.replace(/^[\/\\]+/g, "");
    return aa + sep + bb;
}

function _freeze(o) {
    if (!_isObj(o)) return o;
    try { return Object.freeze(o); } catch { return o; }
}

function _clonePlain(o) {
    if (!_isObj(o)) return {};
    const out = {};
    for (const k in o) out[k] = o[k];
    return out;
}

function _readViaAssets(engine, path) {
    const assets = engine && engine.assets && engine.assets();
    if (!assets || typeof assets.readText !== "function") return null;
    const txt = assets.readText(path);
    const obj = _readJsonSafe(txt);
    return _isObj(obj) ? obj : null;
}

function _readViaFs(path) {
    try {
        const fs = require("fs");
        if (!fs || typeof fs.readFileSync !== "function") return null;
        const txt = fs.readFileSync(path, "utf8");
        const obj = _readJsonSafe(txt);
        return _isObj(obj) ? obj : null;
    } catch {
        return null;
    }
}

function _resolveBaseDir(K) {
    if (K && (K.__moduleDir || K.moduleDir)) return String(K.__moduleDir || K.moduleDir);
    if (K && (K.__modulePath || K.modulePath)) return _dirOf(String(K.__modulePath || K.modulePath));
    return "";
}

function load(engine, K, fallbackMeta, fileName) {
    const fn = String(fileName || "kaliMod.json");
    const baseDir = _resolveBaseDir(K);
    const fb = _clonePlain(fallbackMeta);

    if (baseDir) {
        const path1 = _join(baseDir, fn);
        const viaAssets = _readViaAssets(engine, path1);
        if (viaAssets) return _freeze(viaAssets);

        if (typeof __dirname === "string") {
            const p2 = _join(__dirname, fn);
            const viaFs2 = _readViaFs(p2);
            if (viaFs2) return _freeze(viaFs2);
        }

        const viaFs1 = _readViaFs(path1);
        if (viaFs1) return _freeze(viaFs1);
    } else {
        if (typeof __dirname === "string") {
            const p = _join(__dirname, fn);
            const viaFs = _readViaFs(p);
            if (viaFs) return _freeze(viaFs);
        }
    }

    return _freeze(fb);
}

function loadOrThrow(engine, K, fileName) {
    const fn = String(fileName || "kaliMod.json");
    const baseDir = _resolveBaseDir(K);
    if (!baseDir && typeof __dirname !== "string") throw new Error("KaliMod: module path/dir is required (K.__moduleDir or K.__modulePath)");
    const meta = load(engine, K, null, fn);
    if (!_isObj(meta) || !meta.name) throw new Error("KaliMod: invalid or missing " + fn);
    return meta;
}

module.exports = {
    load,
    loadOrThrow
};