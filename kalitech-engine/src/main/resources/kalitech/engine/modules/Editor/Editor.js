// FILE: resources/kalitech/engine/modules/Editor/Editor.js
// Author: Kalitech
"use strict";

const {requireEngineApi} = require("../helpers/ModuleCommon.js");

/**
 * Editor API wrapper.
 * Provides explicit toggles for runtime editor helpers.
 */
function create(engine /*, K */) {
    const api = requireEngineApi(engine, "editor", "EDITOR");

    return Object.freeze({
        enabled: () => !!api.enabled(),
        setEnabled: (enabled) => { api.setEnabled(!!enabled); return true; },
        toggle: () => { api.toggle(); return true; },
        setFlyCam: (enabled) => { api.setFlyCam(!!enabled); return true; },
        setStatsView: (enabled) => { api.setStatsView(!!enabled); return true; },
        api
    });
}

create.META = {
    moduleId: "editor",
    globalName: "EDITOR",
    version: "1.0.0",
    description: "Editor wrapper for toggling fly cam and stats overlays",
    engineMin: "0.1.0"
};

module.exports = create;
