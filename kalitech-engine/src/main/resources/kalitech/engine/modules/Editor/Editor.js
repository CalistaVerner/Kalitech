// FILE: resources/kalitech/engine/modules/Editor/Editor.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.editor !== "function") {
        throw new Error("[EDITOR] engine.editor() is required");
    }
    const api = engine.editor();
    if (!api) throw new Error("[EDITOR] engine.editor() returned null");
    return api;
}

/**
 * Editor API wrapper.
 * Provides explicit toggles for runtime editor helpers.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

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
