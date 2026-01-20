// FILE: resources/kalitech/engine/modules/Assets/Assets.js
// Author: Kalitech
"use strict";

const {requireEngineApi, normalizeNullableObject} = require("../helpers/ModuleCommon.js");

function normalizePath(path, label) {
    const p = String(path || "").trim();
    if (!p) throw new Error("[ASSETS] " + label + " is required");
    return p;
}

function normalizeCfg(cfg) {
    return normalizeNullableObject(cfg, "ASSETS", "cfg");
}

/**
 * Assets API wrapper.
 * Provides a strict JS surface for engine.assets().
 *
 * Notes:
 * - Java side may define loadModel(path, Value cfg) and expects 2 arguments.
 *   This wrapper ALWAYS calls loadModel with 2 args to avoid Graal arity errors.
 */
function create(engine /*, K */) {
    if (!engine) throw new Error("[ASSETS] engine is required");

    const api = requireEngineApi(engine, "assets", "ASSETS");
    if (typeof api.readText !== "function") {
        throw new Error("[ASSETS] engine.assets() must provide readText(path)");
    }
    if (typeof api.loadModel !== "function") {
        throw new Error("[ASSETS] engine.assets() must provide loadModel(path, cfg)");
    }
    // Optional but very useful for verified JS loader fallback
    // (AssetsApiImpl exports readJsVerified)
    // We don't hard-require it to keep backward compatibility.

    function readText(path) {
        return api.readText(normalizePath(path, "path"));
    }

    function readJsVerified(path) {
        const p = normalizePath(path, "path");
        if (typeof api.readJsVerified === "function") {
            return api.readJsVerified(p);
        }
        // Fallback: behave like readText if verified loader is not exposed
        return api.readText(p);
    }

    function loadModel(path, cfg) {
        const p = normalizePath(path, "path");
        const c = normalizeCfg(cfg);
        // Always 2 args to match Java signature loadModel(String, Value)
        return api.loadModel(p, c);
    }

    function enabled() {
        return !!api;
    }

    // Keep raw access but make it explicit (avoids accidental misuse)
    function host() {
        return api;
    }

    return Object.freeze({
        enabled,
        readText,
        readJsVerified,
        loadModel,
        host
    });
}

create.META = {
    moduleId: "assets",
    globalName: "ASSETS",
    version: "1.1.0",
    description: "Assets wrapper for readText/readJsVerified/loadModel with strict argument checks and safe arity",
    engineMin: "0.1.0"
};

module.exports = create;
