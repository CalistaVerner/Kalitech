module.exports.bootstrap = function (ctx) {
    const log = ctx.api.log();
    log.info("[spawn] bootstrap");

    // Пример: можно читать json-конфиг из ассетов (без GSON в Java)
    // const cfgText = ctx.api.assets().readText("Data/spawn.json");
    // const cfg = JSON.parse(cfgText);

    // Можно дернуть спавн сущностей тут, но в нашем main.js это делает player.spawn()
    // Оставим как пример.
};

module.exports.init = function (ctx) {
    const log = ctx.api.log();
    log.info("[spawn] init");

    // Пример: событие "world:ready"
    ctx.api.events().emit("world:ready", { at: Date.now() });
};

module.exports.update = function (ctx, tpf) {
    // Пример: периодический tick event (можно отключить)
    // ctx.api.events().emit("world:tick", { tpf });
};

module.exports.destroy = function (ctx) {
    ctx.api.log().info("[spawn] destroy");
};
