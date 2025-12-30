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

        const ground = engine.terrain().plane({ w: 1000, h: 1000 });
        ground.setMaterial(MAT.getMaterial("unshaded.grass"));
        ground.setTransform({ pos: [0, 0, 0] });

        // ✅ IMPORTANT:
        // Do NOT force collider:{type:"box"} without halfExtents — it may create tiny/invalid shape.
        // Let PhysicsApiImpl defaultShapeForSpatial() pick correct shape from the actual mesh/bounds.
        const groundBody = PHYS.body({
            surface: ground,
            mass: 0,
            kinematic: true,
            friction: 1.0,
            restitution: 0.0
        });

        st.set(this.KEY_GROUND, ground);
        st.set(this.KEY_GROUND_PHYS, groundBody);

        try {
            const id = (groundBody && typeof groundBody.id === "function") ? groundBody.id() : (groundBody && groundBody.id);
            LOG.info("[scene] ground ok surfaceId=" + (ground && ground.id ? ground.id : "?") + " bodyId=" + (id != null ? id : "?"));
        } catch (e) {
            LOG.warn("[scene] ground created but cannot log ids: " + (e && e.stack ? e.stack : e));
        }
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