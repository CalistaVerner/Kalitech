// FILE: resources/kalitech/engine/modules/Light/Light.js
// Author: Kalitech
"use strict";

const {requireEngineApi, normalizeCfgObject} = require("../helpers/ModuleCommon.js");

/**
 * Light API wrapper.
 * Provides safe helpers for creating and managing light handles.
 */
function create(engine /*, K */) {
    const api = requireEngineApi(engine, "light", "LIGHT");

    return Object.freeze({
        /** Create a new light and return its handle. */
        create(cfg) {
            return api.create(normalizeCfgObject(cfg));
        },

        /** Update an existing light. */
        set(handle, cfg) {
            api.set(handle, normalizeCfgObject(cfg));
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
