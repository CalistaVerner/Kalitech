// FILE: Scripts/systems/index.js
// Author: Calista Verner
"use strict";

class Index {

    constructor() {
        this.KEY_GROUND = "scene:ground";
        this.KEY_GROUND_PHYS = "scene:ground:phys";
    }

    init(ctx) {
        LOG.info("[scene] init");

        const st = ctx.state();

        render.ensureScene();

        // --- ground plane ---
        const ground = engine.terrain().plane({w: 1000, h: 1000});
        ground.setMaterial(MAT.getMaterial("unshaded.grass"));
        //ground.setShadowMode("Cast");
        ground.setTransform({ pos:[0,0,0] });

        const groundBody = PHYS.body({
            surface: ground,
            mass: 0,
            kinematic: true,
            collider: { type: "box" },
            friction: 1.0,
            restitution: 0.0
        });

        // сохраняем в state
        st.set(this.KEY_GROUND, ground);
        st.set(this.KEY_GROUND_PHYS, groundBody);
    }

    destroy(ctx) {
        const st = ctx.state();

        // physics first
        const phys = st.get(this.KEY_GROUND_PHYS);
        if (phys) {
            try {
                PHYS.remove(phys);
            } catch (_) {}
        }
        st.remove(this.KEY_GROUND_PHYS);

        // surface
        const ground = st.get(this.KEY_GROUND);
        if (ground) {
            try {
                engine.surface().destroy(ground);
            } catch (_) {}
        }
        st.remove(this.KEY_GROUND);
    }
}

module.exports = new Index();