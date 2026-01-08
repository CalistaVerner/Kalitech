// FILE: resources/kalitech/engine/bootstrap/Assets.js
"use strict";

const {dirOf, readJsonSafe, isObj} = require("./Util.js");

function readTextAsset(engine, path) {
    try {
        const a = engine && engine.assets && engine.assets();
        if (a) {
            if (typeof a.readText === "function") return a.readText(path);
            if (typeof a.text === "function") return a.text(path);
            if (typeof a.getText === "function") return a.getText(path);
        }
    } catch (_) {
    }
    try {
        const fs = engine && engine.fs && engine.fs();
        if (fs && typeof fs.readText === "function") return fs.readText(path);
    } catch (_) {
    }
    return null;
}

function tryReadKaliModFromFsNearModule(moduleId) {
    try {
        if (typeof require !== "function" || typeof require.resolve !== "function") return null;
        const resolved = require.resolve(moduleId);
        if (!resolved) return null;
        const fs = require("fs");
        const path = require("path");
        const dir = dirOf(resolved);
        const p = path.join(dir, "kaliMod.json");
        if (!fs.existsSync(p)) return null;
        const txt = fs.readFileSync(p, "utf8");
        const obj = readJsonSafe(txt);
        return isObj(obj) ? obj : null;
    } catch (_) {
        return null;
    }
}

function tryReadKaliModFromAssets(engine, moduleId) {
    try {
        if (!engine || !moduleId) return null;

        let rel = String(moduleId || "");
        if (rel.startsWith("@builtin/")) rel = rel.slice("@builtin/".length);

        const i = Math.max(rel.lastIndexOf("/"), rel.lastIndexOf("\\"));
        const dirRel = i >= 0 ? rel.slice(0, i) : rel;

        const base = "resources/kalitech/builtin/";
        const assetPath = base + dirRel.replace(/\\/g, "/") + "/kaliMod.json";

        const txt = readTextAsset(engine, assetPath);
        const obj = readJsonSafe(txt);
        return isObj(obj) ? obj : null;
    } catch (_) {
        return null;
    }
}

module.exports = {
    readTextAsset,
    tryReadKaliModFromFsNearModule,
    tryReadKaliModFromAssets
};