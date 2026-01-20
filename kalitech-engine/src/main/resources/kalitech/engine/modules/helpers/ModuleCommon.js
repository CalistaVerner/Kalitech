"use strict";

function requireEngineApi(engine, methodName, moduleTag) {
    if (!engine || typeof engine[methodName] !== "function") {
        throw new Error("[" + moduleTag + "] engine." + methodName + "() is required");
    }
    const api = engine[methodName]();
    if (!api) {
        throw new Error("[" + moduleTag + "] engine." + methodName + "() returned null");
    }
    return api;
}

function normalizeCfgObject(cfg) {
    return (cfg && typeof cfg === "object") ? cfg : {};
}

function normalizeNullableObject(cfg, moduleTag, label) {
    if (cfg === undefined || cfg === null) return null;
    if (typeof cfg === "object") return cfg;
    throw new Error("[" + moduleTag + "] " + label + " must be an object or null");
}

module.exports = Object.freeze({
    normalizeCfgObject,
    normalizeNullableObject,
    requireEngineApi
});
