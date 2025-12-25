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
    const M = require("@core/materials/index");
    const p = engine.terrain().plane({ w: 100, h: 100 });
    engine.surface().setMaterial(p, M.getMaterial("unshaded.grass"));
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