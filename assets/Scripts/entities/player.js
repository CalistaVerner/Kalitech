// FILE: assets/Scripts/entities/player.spawn.js
//
// Контракт:
//   module.exports.spawn = function (ctx) -> entityId
//
// ctx:
//   ctx.api -> EngineApi (единая точка входа)
//   ctx.world? / ctx.scene? (если появятся позже)

module.exports.spawn = function (ctx) {
    const api = ctx.api;
    const log = api.log();
    const ent = api.entity();
    const evt = api.events();

    log.info("[player.spawn] spawning player entity");

    // ------------------------------------------------------------
    // Create entity
    // ------------------------------------------------------------

    const id = ent.create("player");

    // ------------------------------------------------------------
    // Transform (каноническая форма)
    // ------------------------------------------------------------
    // Рекомендуемый минимальный контракт Transform:
    // { x, y, z, rotY }
    ent.setComponent(id, "Transform", {
        x: 0,
        y: 1.8,   // высота камеры / глаз
        z: 0,
        rotY: 0
    });

    // ------------------------------------------------------------
    // Script (per-entity behavior)
    // ------------------------------------------------------------

    ent.setComponent(id, "Script", {
        assetPath: "Scripts/entities/player.behavior.js",
        enabled: true
    });

    // ------------------------------------------------------------
    // Tags / identity
    // ------------------------------------------------------------

    ent.setComponent(id, "Tag", {
        value: "PLAYER"
    });

    // ------------------------------------------------------------
    // Camera ownership (если CameraApi это поддерживает)
    // ------------------------------------------------------------
    // Не обязательно, но очень полезно:
    // камера знает, за каким entity следить
    if (api.camera && api.camera().followEntity) {
        api.camera().followEntity(id, {
            offset: { x: 0, y: 0, z: 0 },
            mode: "FPS"
        });
    }

    // ------------------------------------------------------------
    // Notify world
    // ------------------------------------------------------------

    evt.emit("entity:spawned", {
        id,
        type: "player",
        tag: "PLAYER"
    });

    log.info("[player.spawn] player spawned id=" + id);
    return id;
};