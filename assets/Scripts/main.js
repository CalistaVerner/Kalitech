// FILE: Scripts/main.js
// Author: Calista Verner
//
// World entrypoint (compatibility shim + contract).
// Keeps engine configuration stable while internal structure evolves.
//
// Actual content lives in:
//   - Scripts/world/main.world.js
//   - Scripts/world/main.bootstrap.js

const worldMod = require("./world/main.world.js");
const bootMod  = require("./world/main.bootstrap.js");

// --- Official contract meta (platform-ready) ---
exports.meta = {
    id: "kalitech.world.main",
    version: "1.0.0",
    apiMin: "0.1.0",
    name: "Main World Entrypoint"
};

// Keep legacy exports for compatibility with existing Java glue:
exports.world = worldMod.world;
exports.bootstrap = bootMod.bootstrap;

// New preferred entrypoint for platform-style loading:
exports.create = function () {
    // If world/bootstrap modules support their own create(), prefer it.
    const world = (typeof worldMod.create === "function") ? worldMod.create() : (worldMod.world || {});
    const boot  = (typeof bootMod.create === "function")  ? bootMod.create()  : (bootMod.bootstrap || {});

    // Shim state: store only JSON-serializable data here
    let state = {
        // could include selected world preset, seed, etc.
        started: false
    };

    return {
        init(api) {
            // If bootstrap exposes init(api), call it. Otherwise keep compatibility.
            if (boot && typeof boot.init === "function") boot.init(api);
            state.started = true;
        },

        update(tpf) {
            if (boot && typeof boot.update === "function") boot.update(tpf);
        },

        destroy(reason) {
            if (boot && typeof boot.destroy === "function") boot.destroy(reason);
        },

        serialize() {
            // If bootstrap/world can serialize, merge. Keep it JSON-only.
            const bootState = (boot && typeof boot.serialize === "function") ? boot.serialize() : null;
            const worldState = (world && typeof world.serialize === "function") ? world.serialize() : null;

            return {
                ...state,
                boot: bootState,
                world: worldState
            };
        },

        deserialize(restored) {
            if (restored && typeof restored === "object") {
                state = { ...state, ...restored, started: restored.started ?? state.started };

                if (boot && typeof boot.deserialize === "function") boot.deserialize(restored.boot);
                if (world && typeof world.deserialize === "function") world.deserialize(restored.world);
            }
        }
    };
};
