// FILE: resources/kalitech/builtin/World.js
// Author: Calista Verner
"use strict";

const {req} = require("./helpers/WorldUtil.js");
const {WorldApi} = require("./helpers/WorldApi.js");

function create(engine, K) {
    req(engine, "[WORLD] engine is required");

    const api = new WorldApi(engine, K);

    const publicApi = Object.freeze({
        // Low-level (kept): normalize + engine.world().create(desc)
        create: api.create.bind(api),

        // Simple boot (kept)
        boot: api.boot.bind(api),

        // Object mode
        $: api.$.bind(api),

        // Pure env seed (no require/io)
        env: api.env.bind(api),

        // World time (NEW, read-only, JSON-safe)
        getWorldTime: api.getWorldTime.bind(api),

        // Utilities
        normalize: api.normalize.bind(api),

        // Optional builder (compat)
        builder: api.builder.bind(api)
    });

    globalThis.WORLD = publicApi;
    return publicApi;
}

create.META = {
    moduleId: "world",
    id: "world",
    globalName: "WORLD",
    version: "2.5.0",
    description:
        "World bootstrap DSL. Object-mode via WORLD.$(). " +
        "Pure env seed via WORLD.env() (no IO). " +
        "Explicit systems only. Read-only access to world time via WORLD.getWorldTime().",
    engineMin: "0.2.0",
    changelog: [
        "2.5.0: world time snapshots now include frame/tick indices and dt/interpolation fields."
    ],
    deprecation: {
        status: "active",
        policy: "Breaking changes require major bump."
    }
};

module.exports = create;
module.exports.META = create.META;
