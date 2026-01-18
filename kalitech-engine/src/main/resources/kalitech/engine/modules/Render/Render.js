// FILE: resources/kalitech/engine/modules/Render/Render.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.render !== "function") {
        throw new Error("[RENDER] engine.render() is required");
    }
    const api = engine.render();
    if (!api) throw new Error("[RENDER] engine.render() returned null");
    return api;
}

function normalizeCfg(cfg) {
    return (cfg && typeof cfg === "object") ? cfg : {};
}

/**
 * Render API wrapper.
 * Provides simple methods that map directly to RenderApi contract.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    return Object.freeze({
        /** Ensure render scene is initialized. */
        ensureScene() {
            api.ensureScene();
            return true;
        },

        /** Configure ambient light. */
        ambient(cfg) {
            api.ambientCfg(normalizeCfg(cfg));
            return true;
        },

        /** Configure sun (directional) light. */
        sun(cfg) {
            api.sunCfg(normalizeCfg(cfg));
            return true;
        },

        /** Configure sun shadows. */
        sunShadows(cfg) {
            api.sunShadowsCfg(normalizeCfg(cfg));
            return true;
        },

        /** Configure fog. */
        fog(cfg) {
            api.fogCfg(normalizeCfg(cfg));
            return true;
        },

        /** Configure post-processing. */
        post(cfg) {
            api.postCfg(normalizeCfg(cfg));
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
