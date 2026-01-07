"use strict";

const WORLD_SCHEMA_VERSION = 1;
const baseSystems = require("./World.systems.js").worldSystems;

exports.meta = {
    id: "kalitech.world.main",
    version: "1.3.1",
    apiMin: "0.1.0",
    name: "Main World Descriptor (pure)"
};

exports.create = function create(opts) {
    const mode = (opts && opts.mode) ? String(opts.mode) : "game";
    return {
        name: "main",
        mode,
        schemaVersion: WORLD_SCHEMA_VERSION,
        systems: baseSystems,
        entities: []
    };
};

exports.world = exports.create({mode: "game"});