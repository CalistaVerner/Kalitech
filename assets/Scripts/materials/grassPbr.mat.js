// FILE: Scripts/materials/grassPbr.mat.js
// Author: Calista Verner
//
// PBR grass material factory.
// Responsibility:
//  - create a reusable PBR material instance
//  - validate and normalize parameters
//
// No scene logic here. Material only.

const C = require("../lib/cfg.js");

module.exports.create = function (engine, cfg = {}) {
    const textures = C.obj(cfg, "textures", {});

    const baseColorMap =
        C.str(textures, "baseColor", "Textures/Grass/grass_albedo.jpg");

    const roughnessMap =
        C.str(textures, "roughness", "Textures/Grass/grass_roughness.jpg");

    const metallicMap =
        C.str(textures, "metallic", "Textures/Grass/grass_metallic.jpg");

    const aoMap =
        C.str(textures, "ao", "Textures/Grass/grass_ao.jpg");

    const tiling =
        C.num(cfg, "tiling", 4.0, 0.1, 64.0);

    return engine.material().create({
        def: "Common/MatDefs/Light/PBRLighting.j3md",

        params: {
            BaseColorMap: { value: baseColorMap, wrap: "Repeat" },
            RoughnessMap: { value: roughnessMap, wrap: "Repeat" },
            MetallicMap: { value: metallicMap, wrap: "Repeat" },
            AmbientOcclusionMap: { value: aoMap, wrap: "Repeat" },

            UseSpecGloss: false,
            UseSpecularAA: false,

            Metallic: C.num(cfg, "metallic", 0.0, 0.0, 1.0),
            Roughness: C.num(cfg, "roughness", 0.8, 0.0, 1.0)
        },

        scales: {
            BaseColorMap: tiling,
            RoughnessMap: tiling,
            MetallicMap: tiling,
            AmbientOcclusionMap: tiling
        }
    });
};
