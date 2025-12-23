let state = {
    frame: 0,
    enabled: true,
};

module.exports.bootstrap = function (ctx) {
    ctx.api.log().info("[ai] bootstrap");
};

module.exports.init = function (ctx) {
    const log = ctx.api.log();
    log.info("[ai] init");

    // Пример: подписки лучше делать через events api, если он поддерживает
    // ctx.api.events().on("something", handler) — если реализуешь.
    // Пока просто шлем "ai:init".
    ctx.api.events().emit("ai:init", { ok: true });
};

module.exports.update = function (ctx, tpf) {
    if (!state.enabled) return;

    state.frame++;

    // Пример: раз в 60 кадров что-то логируем
    if (state.frame % 60 === 0) {
        ctx.api.log().debug(`[ai] tick frame=${state.frame} tpf=${tpf}`);
    }

    // Пример: можно читать компонент и менять его
    // (Зависит от того, как ты реализуешь entity api)
    // const playerId = ctx.api.entity().findByName("player");
    // const tr = ctx.api.entity().getComponent(playerId, "Transform");
    // if (tr) { tr.x += 0.01; ctx.api.entity().setComponent(playerId, "Transform", tr); }
};

module.exports.destroy = function (ctx) {
    ctx.api.log().info("[ai] destroy");
    state = { frame: 0, enabled: true };
};