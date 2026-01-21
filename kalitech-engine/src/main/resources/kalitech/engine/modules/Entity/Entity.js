// FILE: resources/kalitech/builtin/Entity.js
"use strict";

const {req} = require("./helpers/EntUtil.js");
const {EntApi} = require("./helpers/EntApi.js");

function create(engine) {
    req(engine, "[ENT] engine is required");
    const api = new EntApi(engine);

    return Object.freeze({
        create: api.create.bind(api),

        $: api.$.bind(api),
        capsule$: api.capsule$.bind(api),
        box$: api.box$.bind(api),
        sphere$: api.sphere$.bind(api),

        preset: api.preset.bind(api),
        bodyDefaults: api.bodyDefaults.bind(api),
        presets: api.presets.bind(api),

        idOf: api.idOf.bind(api),
        uuidOf: api.uuidOf.bind(api)
    });
}

create.META = {
    moduleId: "entity",
    globalName: "ENT",
    version: "2.3.0",
    description: "Deterministic Entity API (UUID-only). Returns EntityHandle. JS mirrors Java snapshot for UI.",
    engineMin: "0.2.0"
};

module.exports = create;
module.exports.META = create.META;