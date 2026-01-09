// FILE: resources/kalitech/builtin/Entity.js
// Author: Calista Verner
"use strict";

/**
 * Builtin entity factory (engine-extension).
 *
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { moduleId, globalName, version, description, engineMin }
 */

const {req} = require("./helpers/EntUtil.js");
const {EntApi} = require("./helpers/EntApi.js");

function create(engine, K) {
    req(engine, "[ENT] engine is required");

    const api = new EntApi(engine, K);

    return Object.freeze({
        // creation
        create: api.create.bind(api),

        // builder
        $: api.$.bind(api),
        player$: api.player$.bind(api),
        capsule$: api.capsule$.bind(api),
        box$: api.box$.bind(api),
        sphere$: api.sphere$.bind(api),

        // config
        preset: api.preset.bind(api),
        bodyDefaults: api.bodyDefaults.bind(api),
        presets: api.presets.bind(api),

        // utility
        idOf: api.idOf.bind(api)
    });
}

create.META = {
    moduleId: "entity",
    globalName: "ENT",
    version: "1.2.0",
    description: "Declarative entity builder (entity + surface + body + components) + EntityCore on handle.core",
    engineMin: "0.1.0"
};

module.exports = create;
module.exports.META = create.META;