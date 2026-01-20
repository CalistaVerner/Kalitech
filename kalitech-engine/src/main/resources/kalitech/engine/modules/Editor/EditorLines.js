// FILE: resources/kalitech/engine/modules/Editor/EditorLines.js
// Author: Kalitech
"use strict";

const {requireEngineApi, normalizeCfgObject} = require("../helpers/ModuleCommon.js");

/**
 * Editor lines API wrapper.
 * Manages editor-only grid plane helpers.
 */
function create(engine /*, K */) {
    const api = requireEngineApi(engine, "editorLines", "EDITOR_LINES");

    return Object.freeze({
        createGridPlane(cfg) {
            return api.createGridPlane(normalizeCfgObject(cfg));
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
