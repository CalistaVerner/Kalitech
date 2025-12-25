// FILE: Scripts/main.js
// Author: Calista Verner
"use strict";

const worldMod = require("./world/main.world.js");
const bootMod  = require("./world/main.bootstrap.js");

exports.meta = {
    id: "kalitech.world.main",
    version: "1.1.0",
    apiMin: "0.1.0",
    name: "Main World Entrypoint"
};

// legacy
exports.world = worldMod.world;
exports.bootstrap = bootMod.bootstrap;

exports.create = function () {
    // no typeof checks — contract: modules provide either .create() or plain object
    var world = worldMod.create ? worldMod.create() : (worldMod.world || {});
    var boot  = bootMod.create  ? bootMod.create()  : (bootMod.bootstrap || {});

    var state = { started: false };

    return {
        init: function (apiOrCtx) {
            // rely on assert if you want hard guarantees
            // assert(apiOrCtx, "[main] init requires ctx/api");

            try { if (boot.init) boot.init(apiOrCtx); } catch (e) {
                engine.log().error("[main] boot.init failed: " + e);
                throw e;
            }
            state.started = true;
        },

        update: function (tpfOrCtx) {
            try { if (boot.update) boot.update(tpfOrCtx); } catch (e) {
                engine.log().error("[main] boot.update failed: " + e);
                throw e;
            }
        },

        destroy: function (reason) {
            try { if (boot.destroy) boot.destroy(reason); } catch (e) {
                engine.log().error("[main] boot.destroy failed: " + e);
            }
        },

        serialize: function () {
            var bootState = null;
            var worldState = null;

            try { if (boot.serialize) bootState = boot.serialize(); } catch (_) {}
            try { if (world.serialize) worldState = world.serialize(); } catch (_) {}

            return { started: state.started, boot: bootState, world: worldState };
        },

        deserialize: function (restored) {
            // deepMerge is builtin
            if (restored && typeof restored === "object") {
                state = deepMerge(state, restored);
                try { if (boot.deserialize) boot.deserialize(restored.boot); } catch (_) {}
                try { if (world.deserialize) world.deserialize(restored.world); } catch (_) {}
            }
        }
    };
};