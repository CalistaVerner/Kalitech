// Author: Calista Verner
module.exports.create = function (engine) {
    return engine.material().create({
        def: "Common/MatDefs/Terrain/TerrainLighting.j3md",
        params: {
            // оставь свои параметры/слои здесь
        }
    });
};