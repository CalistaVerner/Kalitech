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


        const ground = TERR.plane({
            w: 1000, h: 1000,
            uv: { scale: [50, 50] },
            material: MAT.getMaterial("unshaded.grass"),
            attach: true,
            physics: { mass: 0, collider: { type: "box" }, friction: 1.0 }
        });
    }

    destroy(ctx) {
        const st = ctx.state();

        const phys = st.get(this.KEY_GROUND_PHYS);
        if (phys) {
            try {
                PHYS.remove(phys);
            } catch (e) {
                LOG.error("[scene] failed to remove ground body", e);
            }
        }
        st.remove(this.KEY_GROUND_PHYS);

        const ground = st.get(this.KEY_GROUND);
        if (ground) {
            try {
                engine.surface().destroy(ground);
            } catch (e) {
                LOG.error("[scene] failed to destroy ground surface", e);
            }
        }
        st.remove(this.KEY_GROUND);
    }
}

module.exports = new Index();