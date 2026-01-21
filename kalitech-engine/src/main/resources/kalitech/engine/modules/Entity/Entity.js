"use strict";

const {req} = require("./helpers/EntUtil.js");
const {EntApi} = require("./helpers/EntApi.js");

function create(engine, K) {
    req(engine, "[ENT] engine is required");

    const api = new EntApi(engine, K);

    return Object.freeze({
        create: api.create.bind(api),

        $: api.$.bind(api),
        player$: api.player$.bind(api),
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
    version: "2.0.2",
    description: "Declarative entity builder (UUID-only). Returns {core, handle} (compat). Core mirrors Java snapshot/components.",
    engineMin: "0.2.0"
};

module.exports = create;
module.exports.META = create.META;