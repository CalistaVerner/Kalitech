// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

const config = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials"
    },
    packageStyle: {
        enabled: false,
        roots: {}
    }
};

if (!globalThis.__kalitech) globalThis.__kalitech = Object.create(null);
globalThis.__kalitech.config = config;

// local helpers
function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }
function clamp(x, a, b) { x = +x; return x < a ? a : (x > b ? b : x); }

module.exports = { config, safeJson, clamp };

// ✅ Expose builtins into global namespace (loaded before user scripts)
if (!globalThis.safeJson) globalThis.safeJson = safeJson;
if (!globalThis.clamp) globalThis.clamp = clamp;

// Optional: expose a single "builtins" bag to avoid polluting top-level too much
if (!globalThis.__kalitech.builtins) globalThis.__kalitech.builtins = Object.create(null);
globalThis.__kalitech.builtins.safeJson = safeJson;
globalThis.__kalitech.builtins.clamp = clamp;

// (опционально) если хотите прям «всё глобально», можно ещё сюда подцепить остальные builtins:
 globalThis.assert = require("@builtin/assert");
 globalThis.deepMerge = require("@builtin/deepMerge");
 globalThis.schema = require("@builtin/schema");
 globalThis.paths = require("@builtin/paths");
 globalThis.eventsUtil = require("@builtin/events");
 globalThis.math = require("@builtin/math");
