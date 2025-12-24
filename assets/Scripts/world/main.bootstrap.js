// FILE: Scripts/world/main.bootstrap.js
// Author: Calista Verner

const WORLD = require("./main.world.js").world;

module.exports.bootstrap = function (ctx) {
    const log = engine.log();
    const events = engine.events();
    const render = engine.render();

    log.info("[main] bootstrap world=" + WORLD.name + " mode=" + WORLD.mode + " engine=" + engine.engineVersion());

    // Safe: scene system may also call it
    try {
        render.ensureScene();
    } catch (e) {
        log.debug("[main] render.ensureScene skipped: " + e);
    }

    // Optional: one-shot listener (if once() exists)
    try {
        events.once("world.ready", function (payload) {
            log.info("[main] world.ready payload=" + JSON.stringify(payload));
        });
    } catch (e) {
        log.debug("[main] events.once not available: " + e);
    }

    events.emit("world.ready", { world: WORLD.name, mode: WORLD.mode });
    events.emit("bootstrap.done", { world: WORLD.name, ts: Date.now() });
};
