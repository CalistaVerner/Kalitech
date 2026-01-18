// FILE: resources/kalitech/engine/modules/Light/Light.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.light !== "function") {
        throw new Error("[LIGHT] engine.light() is required");
    }
    const api = engine.light();
    if (!api) throw new Error("[LIGHT] engine.light() returned null");
    return api;
}

function normalizeCfg(cfg) {
    return (cfg && typeof cfg === "object") ? cfg : {};
}

/**
 * Light API wrapper.
 * Provides safe helpers for creating and managing light handles.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    return Object.freeze({
        /** Create a new light and return its handle. */
        create(cfg) {
            return api.create(normalizeCfg(cfg));
        },

        /** Update an existing light. */
        set(handle, cfg) {
            api.set(handle, normalizeCfg(cfg));
            return handle;
        },

        /** Enable or disable a light. */
        enable(handle, enabled) {
            api.enable(handle, !!enabled);
            return handle;
        },

        /** Check if a light handle exists. */
        exists(handle) {
            return !!api.exists(handle);
        },

        /** Destroy a light handle. */
        destroy(handle) {
            api.destroy(handle);
            return true;
        },

        /** Get light configuration for a handle. */
        get(handle) {
            return api.get(handle);
        },

        /** List all lights. */
        list() {
            return api.list();
        },

        /** Access to the raw host API. */
        api
    });
}

create.META = {
    moduleId: "light",
    globalName: "LIGHT",
    version: "1.0.0",
    description: "Light wrapper for create/set/enable/destroy operations",
    engineMin: "0.1.0"
};

module.exports = create;
