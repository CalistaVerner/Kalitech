// Author: Calista Verner
function createGridMaterial(engine) {
    try {
        return engine.material().create({
            def: "Common/MatDefs/Misc/Unshaded.j3md",
            params: { Color: { r: 0.85, g: 0.85, b: 0.85, a: 0.35 } },
            states: { Wireframe: true, BlendMode: "Alpha" }
        });
    } catch (_) {
        return null;
    }
}

module.exports.init = function (ctx) {
    engine.log().info("[scene] init");
    render.ensureScene();

    const groundMat = require("../materials/terrainLit.mat.js").create(engine);

    const ground = engine.terrain().plane({
        name: "groundPlane",
        w: 512, h: 512,
        pos: { x: -256, y: 0, z: -256 },
        material: groundMat,
        shadows: true,
        attach: true
    });

    const gridMat = createGridMaterial(engine);
    let grid = null;

    if (gridMat) {
        grid = engine.terrain().plane({
            name: "gridOverlay",
            w: 512, h: 512,
            pos: { x: -256, y: 0.02, z: -256 },
            material: gridMat,
            shadows: false,
            attach: true
        });
        try { engine.surface().setShadowMode(grid, "Off"); } catch (_) {}
    }

    const st = ctx.state();
    st.set("scene:ground", ground);
    st.set("scene:grid", grid);
};

module.exports.destroy = function (ctx) {
    const st = ctx.state();

    const grid = st.get("scene:grid");
    if (grid) { try { engine.surface().destroy(grid); } catch (_) {} }
    st.remove("scene:grid");

    const ground = st.get("scene:ground");
    if (ground) { try { engine.surface().destroy(ground); } catch (_) {} }
    st.remove("scene:ground");
};