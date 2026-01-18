// FILE: resources/kalitech/engine/modules/Assets/Assets.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.assets !== "function") {
        throw new Error("[ASSETS] engine.assets() is required");
    }
    const api = engine.assets();
    if (!api || typeof api.readText !== "function" || typeof api.loadModel !== "function") {
        throw new Error("[ASSETS] engine.assets() must provide readText() and loadModel()");
    }
    return api;
}

function normalizePath(path, label) {
    const p = String(path || "").trim();
    if (!p) throw new Error("[ASSETS] " + label + " is required");
    return p;
}

/**
 * Assets API wrapper.
 * Provides a simple, strict JS surface for engine.assets().
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    return Object.freeze({
        /** Read a text asset as string. */
        readText(path) {
            return api.readText(normalizePath(path, "path"));
        },

        /** Load a model and return a SurfaceHandle. */
        loadModel(path, cfg) {
            const c = (cfg && typeof cfg === "object") ? cfg : {};
            return api.loadModel(normalizePath(path, "path"), c);
        },

        /** Access to the raw host API. */
        api
    });
}

create.META = {
    moduleId: "assets",
    globalName: "ASSETS",
    version: "1.0.0",
    description: "Assets wrapper for readText/loadModel with strict argument checks",
    engineMin: "0.1.0"
};

module.exports = create;
