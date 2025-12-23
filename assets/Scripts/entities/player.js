module.exports.spawn = function (ctx) {
    const log = ctx.api.log();
    const ent = ctx.api.entity();

    log.info("[player] spawn");

    const id = ent.create("player");

    // Transform — пример, структура зависит от твоего TransformSystem/Component
    ent.setComponent(id, "Transform", { x: 0, y: 0, z: 0 });

    // ScriptComponent — если хочешь per-entity скрипт:
    // (тогда ScriptSystem подхватит и будет вызывать update/init в этом модуле)
    ent.setComponent(id, "Script", { assetPath: "Scripts/entities/player.behavior.js" });

    // Любые другие компоненты:
    ent.setComponent(id, "Tag", { value: "PLAYER" });

    ctx.api.events().emit("entity:spawned", { id, name: "player" });
    return id;
};