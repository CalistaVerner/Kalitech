// FILE: assets/Scripts/entities/player.behavior.js
//
// Задача: per-entity логика игрока (если используешь ScriptSystem для ScriptComponent).
// Контракт: init(api), update(api, tpf), destroy(api)
// Здесь ctx обычно будет EntityScriptAPI или похожий объект.
// Если ты переходишь на единый ctx.api — можешь адаптировать ScriptSystem, чтобы передавал ctx.

let speed = 1.0;

module.exports.init = function (api) {
    api.log().info("[player.behavior] init");
};

module.exports.update = function (api, tpf) {
    // пример: двигать entity вперёд
    // const tr = api.entity().getComponent(api.entityId(), "Transform");
    // tr.z += speed * tpf;
    // api.entity().setComponent(api.entityId(), "Transform", tr);
};

module.exports.destroy = function (api) {
    api.log().info("[player.behavior] destroy");
};