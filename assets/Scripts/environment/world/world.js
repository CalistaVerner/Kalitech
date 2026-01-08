// FILE: Scripts/systems/index.js
// Author: KΛYLΛ
"use strict";

class Index {
    constructor() {
        this.KEY_GROUND = "scene:ground";
        this.KEY_GROUND_PHYS = "scene:ground:phys";
    }

    init(ctx) {
        LOG.info("[scene] init");

        const size = 513;
        const xz = 2.0;
        const half = (size - 1) * xz * 0.5; // 512

        const t = TERR.create({
            name: "proc",
            kind: "heights",

            // ✅ ВСЁ: высоты передаём как есть (host/polyglot/typed) — Java сама разберёт
            heights: TERR.heights.perlin({
                size,
                seed: 1337,
                scale: 360,
                octaves: 12,
                warp: { amp: 18, scale: 42, octaves: 3 }
            }),

            // size можно оставить здесь (или убрать, если Java умеет инферить из длины)
            terrain: { size, patchSize: 65 },

            // ✅ масштаб и позиция
            scale: { xz, y: 60.0 },
            pos: { x: -half, y: 0, z: -half },

            // ✅ материал и UV
            material: MAT.getMaterial("unshaded.grass"),
            uv: { scale: [50, 50] },

            attach: true,

            // ⚠️ для статического террейна лучше mesh (если dynamicMesh даёт джиттер)
            physics: { mass: 0, friction: 1.0 }

            // (опционально) если Java поддерживает:
            // autoCenter: true, // 0..1 -> -0.5..+0.5 автоматически
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