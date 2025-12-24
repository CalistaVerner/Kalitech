// Author: Calista Verner
module.exports.spawn = function ({ api }) {
    const ent = api.entity();
    const evt = api.events();

    const id = ent.create("player");

    ent.setComponent(id, "Transform", { x: 0, y: 1.8, z: 0, rotY: 0 });
    ent.setComponent(id, "Script", { assetPath: "Scripts/behaviors/player.behavior.js", enabled: true });
    ent.setComponent(id, "Tag", { value: "PLAYER" });

    if (api.camera && api.camera().followEntity) {
        api.camera().followEntity(id, { offset: { x: 0, y: 0, z: 0 }, mode: "FPS" });
    }

    evt.emit("entity:spawned", { id, type: "player", tag: "PLAYER" });
    return id;
};