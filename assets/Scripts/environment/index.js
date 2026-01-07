// FILE: Scripts/world/main.world.js
// Author: KΛYLΛ
//
// Pure data descriptor.
// No validation, no clone/freeze, no presets.
// If script says so — engine builds.

"use strict";

const WORLD_SCHEMA_VERSION = 1;
const baseSystems = require("./World.systems.js").worldSystems; // :contentReference[oaicite:2]{index=2}

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

// Legacy fallback (если где-то старый код читает exports.world)
exports.world = exports.create({mode: "game"});