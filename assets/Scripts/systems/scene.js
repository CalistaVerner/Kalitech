// Scripts/systems/scene.js
// Author: Calista Verner

module.exports.init = function () {
    engine.log().info("[scene] init");
    render.ensureScene();

    render.ambientCfg({ color: { r: 0.25, g: 0.28, b: 0.35 }, intensity: 1.0 });
    render.sunCfg({ dir: [-1, -1, -0.3], color: [1.0, 0.98, 0.9], intensity: 1.2 });
    render.sunShadowsCfg({ mapSize: 2048, splits: 3, lambda: 0.65 });

    render.terrainCfg({
        heightmap: "Textures/terrain/height513.png",
        patchSize: 65,
        size: 513,
        heightScale: 2.0,
        xzScale: 2.0,

        alpha: "Textures/terrain/alpha.png",
        layers: [
            { tex: "Textures/terrain/grass.jpg", scale: 64 },
            { tex: "Textures/terrain/rock.jpg",  scale: 32 },
            { tex: "Textures/terrain/dirt.jpg",  scale: 16 }
        ],

        shadows: true
    });

    ctx.api.render().skyboxCube("Textures/Sky/skyBox.dds");
    ctx.api.render().fogCfg({ color: { r: 0.70, g: 0.78, b: 0.90 }, density: 1.2, distance: 250 });
};

module.exports.update = function (ctx, tpf) {};
module.exports.destroy = function () {};