"use strict";

const WorldSystems = require("./World.systems").worldSystems;

exports.meta = {
    id: "kalitech.app",
    version: "2.4.0",
    apiMin: "0.2.0",
    name: "Kalitech App Entrypoint (WORLD boot, time-aware)"
};

exports.start = function start(ctx) {
    const engine = ctx.engine.api();
    const world = WORLD;

    const base = world.env({mode: "game"});
    const desc = world.$(base)
        .systems(WorldSystems)
        .time({worldTime: 0.0, timeRate: 1.0, paused: false, maxDelta: 0.25})
        .build();

    world.create(desc);
};