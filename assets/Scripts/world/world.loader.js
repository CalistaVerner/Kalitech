// FILE: Scripts/world/world.loader.js
// Author: Calista Verner
"use strict";

exports.meta = {
    id: "kalitech.world.loader",
    version: "1.1.0",
    apiMin: "0.1.0"
};

exports.create = function () {
    var state = {
        worldName: null,
        mode: null,
        readyCount: 0,
        lastReadyPayload: null
    };

    return {
        init: function () {
            // events builtin может давать глобальный bus, но даже если нет —
            // engine.events() у вас уже есть как основной.
            var bus = engine.events();
            bus.on("world:boot", function (p) {
                engine.log().info("[world.loader] world:boot " + safeJson(p));
            });
            bus.on("world:ready", function (p) {
                state.readyCount++;
                state.lastReadyPayload = p || null;
                if (p) {
                    if (p.world != null) state.worldName = p.world;
                    if (p.mode != null) state.mode = p.mode;
                }
                engine.log().info("[world.loader] world:ready " + safeJson(p));
            });
            bus.on("world:shutdown", function (p) {
                engine.log().info("[world.loader] world:shutdown " + safeJson(p));
            });
        },

        serialize: function () { return state; },

        deserialize: function (s) {
            if (s && typeof s === "object") state = deepMerge(state, s);
        }
    };
};