// FILE: Scripts/world/world.loader.js
// Author: Calista Verner
//
// World loader utilities for runtime tools & editor.
// Works even if Java does the initial build; we use events for lifecycle.

exports.meta = {
    id: "kalitech.world.loader",
    version: "1.0.0",
    apiMin: "0.1.0"
};

function safeJson(v) {
    try { return JSON.stringify(v); } catch (_) { return String(v); }
}

exports.create = function () {
    let state = {
        worldName: null,
        mode: null,
        readyCount: 0,
        lastReadyPayload: null
    };

    return {
        init() {
            const log = engine.log();
            const events = engine.events();

            // Listen world lifecycle
            if (events?.on) {
                events.on("world:boot", (p) => log.info("[world.loader] world:boot " + safeJson(p)));
                events.on("world:ready", (p) => {
                    state.readyCount++;
                    state.lastReadyPayload = p || null;
                    state.worldName = p?.world ?? state.worldName;
                    state.mode = p?.mode ?? state.mode;
                    log.info("[world.loader] world:ready " + safeJson(p));
                });
                events.on("world:shutdown", (p) => log.info("[world.loader] world:shutdown " + safeJson(p)));
            }
        },

        // optional: future runtime apply() where Java exposes system toggles:
        // applyWorldDescriptor(world) { engine.world().apply(world) ... }

        serialize() { return state; },
        deserialize(s) { if (s && typeof s === "object") state = { ...state, ...s }; }
    };
};