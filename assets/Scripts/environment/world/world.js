// FILE: Scripts/systems/index.js
// Author: KΛYLΛ
"use strict";

class Index {
    constructor() {
        this.KEY_GROUND = "scene:ground";
        this.KEY_GROUND_PHYS = "scene:ground:phys";
    }

    init(ctx) {
        ENGINE.log.info("[scene] init");

        const size = 513;
        const xz = 2.0;
        const half = (size - 1) * xz * 0.5; // 512

        globalThis.TERRAIN = TERR.create({
            name: "proc",
            kind: "heights",

            // ✅ ВСЁ: высоты передаём как есть (host/polyglot/typed) — Java сама разберёт
            heights: TERR.heights.perlin({
                size,
                seed: 1337,
                scale: 480,
                octaves: 12,
                warp: { amp: 18, scale: 42, octaves: 3 }
            }),

            // size можно оставить здесь (или убрать, если Java умеет инферить из длины)
            terrain: { size, patchSize: 65 },

            // ✅ масштаб и позиция
            scale: { xz, y: 60.0 },
            pos: { x: -half, y: 0, z: -half },

            // ✅ материал и UV
            material: ENGINE.material.getMaterial("unshaded.sand"),
            uv: { scale: [50, 50] },

            attach: true,

            // ⚠️ для статического террейна лучше mesh (если dynamicMesh даёт джиттер)
            physics: { mass: 0, friction: 1.0 }

            // (опционально) если Java поддерживает:
            // autoCenter: true, // 0..1 -> -0.5..+0.5 автоматически
        });

        ENGINE.events.on("engine.physics.body.added", (i) => {
            let x = i.pos.x;
            let y = i.pos.y;
            let z = i.pos.z;
            ENGINE.log.debug("Spawned at (x=" + x + ",y=" + y + "z=" + z + ")");
            ENGINE.sound.playSound({event: "world.spawn"});
        });
    }


    destroy(ctx) {
        const st = ctx.state();

        const phys = st.get(this.KEY_GROUND_PHYS);
        if (phys) {
            try {
                ENGINE.physics.remove(phys);
            } catch (e) {
                ENGINE.log.error("[scene] failed to remove ground body", e);
            }
        }
        st.remove(this.KEY_GROUND_PHYS);

        const ground = st.get(this.KEY_GROUND);
        if (ground) {
            try {
                engine.surface().destroy(ground);
            } catch (e) {
                ENGINE.log.error("[scene] failed to destroy ground surface", e);
            }
        }
        st.remove(this.KEY_GROUND);
    }
}

module.exports = new Index();