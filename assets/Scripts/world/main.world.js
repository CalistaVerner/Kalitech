// FILE: Scripts/world/main.world.js
// Author: Calista Verner
//
// Main world descriptor (data-first).
// Editor systems are injected by engine/builtins.

"use strict";

const WORLD_SCHEMA_VERSION = 1;

// FIX: world.systems.js now exports `worldSystems`
const baseSystems = require("./world.systems.js").worldSystems;

// --- Official module contract meta ---
exports.meta = {
    id: "kalitech.world.main",
    version: "1.2.0",
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
    return obj ? JSON.parse(JSON.stringify(obj)) : obj;
}

function validateWorld(world) {
    const errors = [];

    if (!world || typeof world !== "object") {
        errors.push("world must be an object");
        return errors;
    }

    if (!world.name || typeof world.name !== "string")
        errors.push("world.name must be string");

    if (!world.mode || (world.mode !== "game" && world.mode !== "editor"))
        errors.push('world.mode must be "game" or "editor"');

    if (!Array.isArray(world.systems))
        errors.push("world.systems must be array");

    if (!Array.isArray(world.entities))
        errors.push("world.entities must be array");

    const ids = new Set();
    for (const s of world.systems || []) {
        if (!s || typeof s !== "object") {
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

        if (s.config != null && typeof s.config !== "object")
            errors.push(`system.config must be object (${stableId || "?"})`);

        if (s.id === "jsSystem") {
            // Keep compatibility with older JS engines that don't support optional chaining
            const cfg = s.config;
            const mod = cfg && cfg.module;
            if (typeof mod !== "string")
                errors.push(`jsSystem requires config.module (${stableId || "?"})`);
        }
    }

    return errors;
}

// --- Presets ---
const presets = {
    game() {
        return {
            name: "main",
            mode: "game",
            schemaVersion: WORLD_SCHEMA_VERSION,

            // IMPORTANT: clone() below will deep-copy, so it’s safe to reference shared baseSystems here
            systems: baseSystems,

            entities: []
        };
    },

    editor() {
        const w = presets.game();
        w.mode = "editor";
        w.name = "main_editor";
        return w;
    }
};

// --- Build ---
function buildWorld(mode) {
    const m = (mode === "editor") ? "editor" : "game";
    const w = presets[m]();

    const frozen = deepFreeze(clone(w));
    const errs = validateWorld(frozen);
    if (errs.length) {
        throw new Error("[world] Invalid world descriptor:\n- " + errs.join("\n- "));
    }
    return frozen;
}

exports.world = buildWorld("editor");

exports.create = function (opts) {
    const mode = (opts && opts.mode) ? opts.mode : "game";
    return buildWorld(mode);
};

// Optional: expose validator for tooling
exports.validate = validateWorld;