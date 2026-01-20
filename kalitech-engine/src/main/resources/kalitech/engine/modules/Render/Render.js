// FILE: resources/kalitech/engine/modules/Render/Render.js
// Author: Kalitech
"use strict";

const {requireEngineApi, normalizeCfgObject} = require("../helpers/ModuleCommon.js");

/**
 * Render API wrapper.
 * Provides simple methods that map directly to RenderApi contract.
 */
function create(engine /*, K */) {
    const api = requireEngineApi(engine, "render", "RENDER");

    return Object.freeze({
        /** Ensure render scene is initialized. */
        ensureScene() {
            api.ensureScene();
            return true;
        },

        /** Configure ambient light. */
        ambient(cfg) {
            api.ambientCfg(normalizeCfgObject(cfg));
            return true;
        },

        /** Configure sun (directional) light. */
        sun(cfg) {
            api.sunCfg(normalizeCfgObject(cfg));
            return true;
        },

        /** Configure sun shadows. */
        sunShadows(cfg) {
            api.sunShadowsCfg(normalizeCfgObject(cfg));
            return true;
        },

        /** Configure fog. */
        fog(cfg) {
            api.fogCfg(normalizeCfgObject(cfg));
            return true;
        },

        /** Configure post-processing. */
        post(cfg) {
            api.postCfg(normalizeCfgObject(cfg));
            return true;
        },

        /** Access to the raw host API. */
        api
    });
}

create.META = {
    moduleId: "render",
    globalName: "RENDER",
    version: "1.0.0",
    description: "Render wrapper for scene setup, lighting, fog and post-processing",
    engineMin: "0.1.0"
};

module.exports = create;
