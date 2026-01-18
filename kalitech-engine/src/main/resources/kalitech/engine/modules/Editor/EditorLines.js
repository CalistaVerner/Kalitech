// FILE: resources/kalitech/engine/modules/Editor/EditorLines.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.editorLines !== "function") {
        throw new Error("[EDITOR_LINES] engine.editorLines() is required");
    }
    const api = engine.editorLines();
    if (!api) throw new Error("[EDITOR_LINES] engine.editorLines() returned null");
    return api;
}

function normalizeCfg(cfg) {
    return (cfg && typeof cfg === "object") ? cfg : {};
}

/**
 * Editor lines API wrapper.
 * Manages editor-only grid plane helpers.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    return Object.freeze({
        createGridPlane(cfg) {
            return api.createGridPlane(normalizeCfg(cfg));
        },

        destroy(handle) {
            api.destroy(handle);
            return true;
        },

        api
    });
}

create.META = {
    moduleId: "editorLines",
    globalName: "EDITOR_LINES",
    version: "1.0.0",
    description: "Editor lines wrapper for grid plane helpers",
    engineMin: "0.1.0"
};

module.exports = create;
