// FILE: Scripts/main.js
// Author: KΛYLΛ
//
// Pure boot:
// - create world descriptor
// - ask engine to create+start world
//
// No routing. No conditions. No “maybe”.

"use strict";

const WorldDesc = require("@env"); // points to Scripts/world/main.world.js :contentReference[oaicite:1]{index=1}

exports.meta = {
    id: "kalitech.app",
    version: "2.0.0",
    apiMin: "0.1.0",
    name: "Kalitech App Entrypoint (pure world boot)"
};

exports.start = function start(ctx) {
    const engine = ctx.engine.api(); // SystemContext.EngineDomain

    // build descriptor (data-first)
    const worldDesc = WorldDesc.create({mode: "game"});

    // just run
    engine.world().create({
        name: worldDesc.name || "main",
        start: true,
        systems: worldDesc.systems || [],
        entities: worldDesc.entities || []
    });

    // optional signal (nice for UI/tools, can remove if not needed)
    try {
        engine.bus().emit("world:started", {name: worldDesc.name, mode: worldDesc.mode});
    } catch (_) {
    }
};
