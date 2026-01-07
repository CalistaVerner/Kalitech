// FILE: Scripts/world/main.world.js
// Author: KΛYLΛ
//
// Main world descriptor (data-first).
// No more hardcoded "exports.world = editor" magic.
// Runtime should call: require(...).create({mode})  ✅
// Legacy fallback: require(...).world              (default preset)

"use strict";

const WORLD_SCHEMA_VERSION = 1;

// Base systems list
// (World.systems.js exports `worldSystems`)
const baseSystems = require("./World.systems.js").worldSystems;

// --- Module meta ---
exports.meta = {
    id: "kalitech.world.main",
    version: "1.3.0",
    apiMin: "0.1.0",
    name: "Main World Descriptor"
};

// --- Helpers ---
function deepFreeze(obj) {
    if (!obj || typeof obj !== "object") return obj;
    Object.freeze(obj);
    for (const k of Object.keys(obj)) deepFreeze(obj[k]);
    return obj;
}

function clone(obj) {
    // JSON clone is enough for descriptors (pure data)
    return obj ? JSON.parse(JSON.stringify(obj)) : obj;
}

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function validateWorld(world) {
    const errors = [];

    if (!isObj(world)) {
        errors.push("world must be an object");
        return errors;
    }

    if (!world.name || typeof world.name !== "string")
        errors.push("world.name must be string");

    // ✅ mode is now data-driven: allow any string (game/editor/menu/benchmark/…)
    if (!world.mode || typeof world.mode !== "string")
        errors.push("world.mode must be string");

    if (!Number.isFinite(+world.schemaVersion))
        errors.push("world.schemaVersion must be number");

    if (!Array.isArray(world.systems))
        errors.push("world.systems must be array");

    if (!Array.isArray(world.entities))
        errors.push("world.entities must be array");

    // systems validation (stableId uniqueness etc.)
    const ids = new Set();
    for (const s of world.systems || []) {
        if (!isObj(s)) {
            errors.push("system entry must be object");
            continue;
        }

        const stableId = String(s.stableId ?? "").trim();
        if (!stableId)
            errors.push("system.stableId is required");

        if (stableId) {
            if (ids.has(stableId))
                errors.push(`duplicate system.stableId: ${stableId}`);
            ids.add(stableId);
        }

        if (typeof s.order !== "number")
            errors.push(`system.order must be number (${stableId || "?"})`);

        if (!s.id || typeof s.id !== "string")
            errors.push(`system.id must be string (${stableId || "?"})`);

        if (s.config != null && !isObj(s.config))
            errors.push(`system.config must be object (${stableId || "?"})`);

        // jsSystem contract: must have config.module
        if (s.id === "jsSystem") {
            const cfg = s.config;
            const mod = cfg && cfg.module;
            if (typeof mod !== "string" || !mod.trim())
                errors.push(`jsSystem requires config.module (${stableId || "?"})`);
        }
    }

    return errors;
}

// --- Presets ---
// Preset returns PURE DATA descriptor.
// buildWorld() will clone+freeze+validate (safe boundary).
const presets = {
    game() {
        return {
            name: "main",
            mode: "game",
            schemaVersion: WORLD_SCHEMA_VERSION,
            systems: baseSystems,
            entities: []
        };
    },

    editor() {
        const w = presets.game();
        w.mode = "editor";
        w.name = "main_editor";
        return w;
    },

    // Minimal worlds (optional, but useful for routing)
    menu() {
        // keep it safe: minimal systems, no player/terrain by default
        // You can later add a dedicated UI jsSystem here.
        return {
            name: "main_menu",
            mode: "menu",
            schemaVersion: WORLD_SCHEMA_VERSION,
            systems: [
                // Example:
                // { id:"jsSystem", order:10, stableId:"ui.mainMenu", config:{ module:"Scripts/ui/MainMenu" } }
            ],
            entities: []
        };
    },

    benchmark() {
        // Keep base systems but remove player if you want;
        // For now: same as game (simple + predictable).
        const w = presets.game();
        w.mode = "benchmark";
        w.name = "main_benchmark";
        return w;
    }
};

// --- Build ---
function normalizeMode(mode) {
    const m = String(mode || "").trim();
    return m || "game";
}

function buildWorld(mode) {
    const m = normalizeMode(mode);
    const factory = presets[m] || presets.game;

    const raw = factory();
    // ensure preset respected requested mode even if preset fallback happened
    raw.mode = m;

    const frozen = deepFreeze(clone(raw));
    const errs = validateWorld(frozen);
    if (errs.length) {
        throw new Error("[world] Invalid world descriptor:\n- " + errs.join("\n- "));
    }
    return frozen;
}

/**
 * ✅ Main entry:
 *   require("./world/main.world.js").create({mode:"game"})
 */
exports.create = function create(opts) {
    const mode = (opts && opts.mode) ? opts.mode : "game";
    return buildWorld(mode);
};

/**
 * ✅ Legacy fallback:
 * Old engine paths may still read `exports.world`.
 * Keep it stable & predictable: default is "game".
 */
exports.world = buildWorld("game");

// Optional: tooling hooks
exports.validate = validateWorld;
exports.presets = Object.freeze(Object.keys(presets));
