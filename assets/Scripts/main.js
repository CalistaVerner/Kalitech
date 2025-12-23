const spawn = require("./systems/spawn.js");
const ai = require("./systems/ai.js");
const player = require("./entities/player.js");

module.exports.world = {
    name: "main",
    systems: [
        // чисто Java системы
        { id: "transform", order: 10 },

        // один универсальный JS-раннер, модуль задаётся данными
        { id: "jsSystem", order: 50, config: { module: "Scripts/systems/spawn.js" } },
        { id: "jsSystem", order: 60, config: { module: "Scripts/systems/ai.js" } }
    ],

    entities: [
        { name: "player", prefab: "Scripts/entities/player.js" }
    ]
};

module.exports.bootstrap = function(ctx) {
    ctx.api.log().info("Bootstrap engine version: " + ctx.api.engineVersion());

    // Можно делегировать “обязанности”:
    spawn.bootstrap?.(ctx);
    ai.bootstrap?.(ctx);

    // Можно создавать сущности программно:
    player.spawn?.(ctx);
};