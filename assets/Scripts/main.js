// Scripts/main.js
// Author: Calista Verner

module.exports.world = {
    name: "main",
    mode: "game", // "editor" to enable editor-mode
    systems: [
        { id: "transform", order: 10 },
        { id: "jsSystem", order: 20, config: { module: "Scripts/systems/scene.js" } },
        { id: "jsSystem", order: 50, config: { module: "Scripts/systems/spawn.js" } },
        { id: "jsSystem", order: 60, config: { module: "Scripts/systems/ai.js" } }
    ],
    entities: [
        { name: "player", prefab: "Scripts/entities/player.js" }
    ]
};

module.exports.bootstrap = function (ctx) {
    const eng = (typeof engine !== "undefined" && engine) ? engine : (ctx?.api ?? null);
    const log = eng?.log ? eng.log() : console;

    log.info?.("Bootstrap engine version: " + (eng?.engineVersion ? eng.engineVersion() : "unknown"));

    try {
        const mode = module.exports.world?.mode || "game";
        if (mode === "editor") eng?.editor?.().setEnabled(true);
    } catch (e) {
        log.warn?.("Editor-mode toggle skipped: " + e);
    }

    try {
        const isEditor = (module.exports.world.mode === "editor");
        const cam = eng.camera();
        cam.setFlySpeed(90.0);
    } catch (e) {
        log.warn?.("FlyCam setup skipped: " + e);
    }

    try {
        const events = (typeof engine !== "undefined" && engine?.events)
            ? engine.events()
            : (ctx?.api?.events?.() ?? null);

        events?.emit?.("world.ready", { world: module.exports.world.name, mode: module.exports.world.mode });
    } catch (e) {}
};