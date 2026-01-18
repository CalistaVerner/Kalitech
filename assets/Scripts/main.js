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

    const base = world.env({mode: "game"}); // 1 day = 30 min real by default
    const desc = world.$(base)
        .systems(WorldSystems)
        .time({
            dayLength: 60 * 60,    // 1 day per 60 real minutes (override)
            day: 0,
            timeOfDay: 8 * 3600    // start at 08:00
        })
        .build();

    world.create(desc);
};