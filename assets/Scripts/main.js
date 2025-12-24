// Scripts/main.js
// Author: Calista Verner
module.exports.world = {
    name: "main",
    mode: "game", // "editor" to enable editor-mode
    systems: [
        { id: "transform", order: 10 },
        { id: "camera", order: 15, config: { mode: "fly", speed: 90, accel: 18, drag: 6.5, smoothing: 0.15 }},
        { id: "jsSystem", order: 20, config: { module: "Scripts/systems/scene.js" } },
        { id: "jsSystem", order: 50, config: { module: "Scripts/systems/spawn.js" } },
        { id: "jsSystem", order: 60, config: { module: "Scripts/systems/ai.js" } }
    ],
    entities: [
        //{ name: "player", prefab: "Scripts/entities/player.js" }
    ]
};

module.exports.bootstrap = function () {
    const logger = engine.log();
    logger.info("Bootstrap engine version: " + engine.engineVersion());

    engine.events().emit("world.ready", {
        world: module.exports.world.name,
        mode: module.exports.world.mode
    });
};