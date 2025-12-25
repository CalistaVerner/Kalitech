// FILE: Scripts/world/main.bootstrap.js
// Author: Calista Verner

const WORLD = require("./main.world.js").world;
const loader = require("./world.loader.js");

exports.meta = {
    id: "kalitech.world.bootstrap.main",
    version: "1.0.0",
    apiMin: "0.1.0"
};

module.exports.bootstrap = function (ctx) {
    const log = engine.log();
    const events = engine.events();
    const render = engine.render();

    log.info("[main] bootstrap world=" + WORLD.name + " mode=" + WORLD.mode + " engine=" + engine.engineVersion());

    // Ensure scene
    try { render.ensureScene(); } catch (e) { log.debug("[main] render.ensureScene skipped: " + e); }

    // Start world services (JS side)
    try {
        const svc = (typeof loader.create === "function") ? loader.create() : null;
        if (svc && typeof svc.init === "function") svc.init(ctx);
        // Optionally keep svc in ctx.state() later when your ctx supports it
    } catch (e) {
        log.debug("[main] world loader init failed: " + e);
    }

    // Lifecycle events (platform-grade)
    try {
        if (events.once) {
            events.once("world:ready", function (payload) {
                log.info("[main] world:ready payload=" + JSON.stringify(payload));
            });
        }
    } catch (e) {
        log.debug("[main] events.once not available: " + e);
    }

    events.emit("world:boot", { world: WORLD.name, mode: WORLD.mode });
    events.emit("world:ready", { world: WORLD.name, mode: WORLD.mode });
    events.emit("bootstrap:done", { world: WORLD.name, ts: Date.now() });
};