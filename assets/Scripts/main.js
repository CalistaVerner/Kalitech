"use strict";

const WorldDesc = require("@env");

exports.meta = {
    id: "kalitech.app",
    version: "2.0.0",
    apiMin: "0.1.0",
    name: "Kalitech App Entrypoint (pure world boot)"
};

exports.start = function start(ctx) {
    const engine = ctx.engine.api();
    const worldDesc = WorldDesc.create({mode: "game"});

    engine.world().create({
        name: worldDesc.name || "main",
        start: true,
        systems: worldDesc.systems || [],
        entities: worldDesc.entities || []
    });

    const bus = engine.bus && engine.bus();
    if (bus && typeof bus.emit === "function") bus.emit("world:started", {name: worldDesc.name, mode: worldDesc.mode});
};