// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

/**
 * Builtin bootstrap for Kalitech.
 * Goals:
 *  - idempotent (safe to require multiple times)
 *  - engine-safe (never touches engine before attachEngine(engine))
 *  - predictable lifecycle (deferred hooks, optional one-time hooks)
 *  - minimal global pollution (everything goes into __kalitech.*; optional globals are guarded)
 */

const config = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials"
    },
    packageStyle: { enabled: false, roots: {} }
};

// --- global kalitech root (idempotent) ---
if (!globalThis.__kalitech) globalThis.__kalitech = Object.create(null);
const K = globalThis.__kalitech;

// stable storage (idempotent)
K.config = config;
if (!K.builtins) K.builtins = Object.create(null);

// lifecycle state
if (!K._deferred) K._deferred = [];
if (!K._engineAttached) K._engineAttached = false;
if (!K._engine) K._engine = null;

// internal: unique “once-key” registry for deferred hooks
if (!K._onceKeys) K._onceKeys = Object.create(null);

// --- engine-independent helpers ---
function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }
function clamp(x, a, b) { x = +x; return x < a ? a : (x > b ? b : x); }

// expose helpers early (safe)
if (!globalThis.safeJson) globalThis.safeJson = safeJson;
if (!globalThis.clamp) globalThis.clamp = clamp;

K.builtins.safeJson = safeJson;
K.builtins.clamp = clamp;

/**
 * Registers a callback that runs when engine is attached.
 * If engine is already attached, runs immediately.
 */
function whenEngine(fn) {
    if (K._engineAttached && K._engine) {
        try { fn(K._engine); } catch (_) {}
        return true;
    }
    K._deferred.push(fn);
    return false;
}

/**
 * Same as whenEngine, but guarantees the hook runs only once per process,
 * even if bootstrap is required multiple times.
 */
function whenEngineOnce(key, fn) {
    const k = String(key || "");
    if (!k) return whenEngine(fn);
    if (K._onceKeys[k]) return false;
    K._onceKeys[k] = true;
    return whenEngine(fn);
}

/**
 * Called from Java right after publishing engine into JS runtime.
 * Example (Java): boot.invokeMember("attachEngine", api)
 */
function attachEngine(engine) {
    if (!engine) return false;

    // idempotent: if same engine is already attached, do nothing
    if (K._engineAttached && K._engine === engine) return true;

    K._engine = engine;
    K._engineAttached = true;

    // drain deferred queue
    const q = K._deferred;
    K._deferred = [];
    for (let i = 0; i < q.length; i++) {
        try { q[i](engine); } catch (_) {}
    }
    return true;
}

/**
 * Optional: detach engine (useful for hot-reload / world swap).
 * Doesn’t clear builtins; it just marks engine as absent.
 */
function detachEngine() {
    K._engine = null;
    K._engineAttached = false;
    return true;
}

// ✅ Export exactly what Java (and scripts) can call
module.exports = { config, safeJson, clamp, whenEngine, whenEngineOnce, attachEngine, detachEngine };

// --- engine-independent builtins (safe to load now) ---
// Guard against double-require side effects: only assign if not already assigned.
if (!globalThis.assert) globalThis.assert = require("@builtin/assert");
if (!globalThis.deepMerge) globalThis.deepMerge = require("@builtin/deepMerge");
if (!globalThis.schema) globalThis.schema = require("@builtin/schema");
if (!globalThis.paths) globalThis.paths = require("@builtin/paths");
if (!globalThis.math) globalThis.math = require("@builtin/math");
if (!K.editorPreset) K.editorPreset = require("@builtin/editorPreset");

// --- engine-dependent wiring (runs only after attachEngine) ---
whenEngineOnce("builtin:engine-wiring", (engine) => {
    const log = engine && engine.log ? engine.log() : null;

    // Prefer keeping references in __kalitech, avoid globals (but allow optional global exposure).
    const events = engine && engine.events ? engine.events() : null;

    K.builtins.engine = engine;
    K.builtins.events = events;

    // Optional global exposure (guarded)
    if (!globalThis.eventsUtil) globalThis.eventsUtil = events;

    if (log && log.info) log.info("[builtin/bootstrap] engine attached");

    // Light diagnostics (helps catch HostAccess issues early)
    if (log && log.debug) {
        try {
            log.debug("[builtin/bootstrap] events.emit=" + String(events && events.emit));
            log.debug("[builtin/bootstrap] events.once=" + String(events && events.once));
        } catch (_) {}
    }

    // Example hook (safe, one-time)
    if (events && typeof events.once === "function") {
        events.once("world:ready", (payload) => {
            if (log && log.info) log.info("[builtin] world:ready payload=" + safeJson(payload));
        });
    } else {
        if (log && log.debug) log.debug("[builtin] events.once not available");
    }
});
