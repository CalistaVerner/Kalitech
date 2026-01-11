// FILE: resources/kalitech/builtin/terrain/TerrainCreateHelper.js
"use strict";

const {isObj, num, i32} = require("./TerrainTypes.js");
const {TerrainHeights} = require("./TerrainHeights.js");
const {TerrainInstance} = require("./TerrainInstance.js");

class TerrainCreateHelper {
    constructor(engine, terrNative, heightsApi, physicsApi, proxies) {
        this.engine = engine;
        this.terr = terrNative;
        this.heights = heightsApi;      // TerrainHeights instance
        this.phys = physicsApi;         // TerrainPhysics instance
        this.p = proxies;               // proxy methods { terrain, terrainHeights, quad, plane, ... }
    }

    create(cfg) {
        const c = isObj(cfg) ? cfg : {};
        const kind = String(c.kind || "terrain");
        const attachFlag = (c.attach === undefined) ? true : !!c.attach;

        const materialH = c.material;
        const uvCfg = c.uv;
        const lodCfg = c.lod;
        const physCfg = c.physics;

        const scaleCfg = isObj(c.scale) ? c.scale : null;
        const xz = scaleCfg ? num(scaleCfg.xz, num(c.xzScale, 1.0)) : num(c.xzScale, 1.0);
        const y = scaleCfg ? num(scaleCfg.y, num(c.yScale, num(c.heightScale, 1.0))) : num(c.yScale, num(c.heightScale, 1.0));

        // ------------------------------------------------------------
        // post: apply material/uv/lod/scale and RETURN TerrainInstance
        // ------------------------------------------------------------
        const post = (surfaceOrBundle) => {
            const surface = (surfaceOrBundle && surfaceOrBundle.surface) ? surfaceOrBundle.surface : surfaceOrBundle;
            if (!surface) throw new Error("[TERR] create(): native returned null surface");

            if (materialH != null) this.p.material(surface, materialH);
            if (uvCfg != null) this.p.uv(surface, uvCfg);
            if (lodCfg != null) this.p.lod(surface, lodCfg);

            // behavior: only non-plane/quad get post-scale here (as in original)
            if (kind !== "plane" && kind !== "quad") {
                if (Number.isFinite(xz) && xz !== 1.0) this.p.scale(surface, xz, {yScale: y});
                else if (Number.isFinite(y) && y !== 1.0) this.p.scale(surface, 1.0, {yScale: y});
            }

            // IMPORTANT: return object-mode instance (mirrors Java API via proxies)
            return new TerrainInstance(this.p, surface);
        };

        // ------------------------------------------------------------
        // plane
        // ------------------------------------------------------------
        if (kind === "plane") {
            const planeCfg = Object.assign({}, isObj(c.plane) ? c.plane : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            return post(this.p.plane(planeCfg));
        }

        // ------------------------------------------------------------
        // quad
        // ------------------------------------------------------------
        if (kind === "quad") {
            const quadCfg = Object.assign({}, isObj(c.quad) ? c.quad : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            return post(this.p.quad(quadCfg));
        }

        // ------------------------------------------------------------
        // heightmap
        // ------------------------------------------------------------
        if (kind === "heightmap") {
            const tcfg = Object.assign({}, isObj(c.terrain) ? c.terrain : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            if (c.heightmap && !tcfg.heightmap) tcfg.heightmap = c.heightmap;
            if (tcfg.heightScale == null && Number.isFinite(y)) tcfg.heightScale = y;
            if (tcfg.xzScale == null && Number.isFinite(xz)) tcfg.xzScale = xz;

            // native creation already handles physics via p.terrain wrapper
            return post(this.p.terrain(tcfg));
        }

        // ------------------------------------------------------------
        // noise: generate heights -> create terrainHeights -> apply post -> build physics AFTER post-scale
        // ------------------------------------------------------------
        if (kind === "noise") {
            const noise = isObj(c.noise) ? c.noise : {};
            const type = String(noise.type || "perlin");

            const size = i32((isObj(c.terrain) ? c.terrain.size : c.size), i32(noise.size, 513)) || 513;
            const patchSize = i32((isObj(c.terrain) ? c.terrain.patchSize : c.patchSize), 65) || 65;

            TerrainHeights.validateTerrainDims(size, patchSize);

            const raw = (type === "ridged")
                ? this.heights.ridged(Object.assign({}, noise, {size}))
                : this.heights.perlin(Object.assign({}, noise, {size}));

            const normalize = (noise.normalize === undefined) ? true : !!noise.normalize;

            const out = new Float32Array(raw.length);
            if (normalize) {
                for (let i = 0; i < raw.length; i++) out[i] = (raw[i] * 2.0 - 1.0) * y;
            } else {
                for (let i = 0; i < raw.length; i++) out[i] = raw[i] * y;
            }

            const tcfg0 = isObj(c.terrain) ? c.terrain : {};
            const tcfgNoPhys = Object.assign({}, tcfg0, {
                name: c.name,
                size,
                patchSize,
                heights: out,
                attach: attachFlag,
            });

            // IMPORTANT: create WITHOUT physics first (dynamicMesh later)
            let surface = this.terr.terrainHeights(tcfgNoPhys);

            // apply post (material/uv/lod/scale)
            const inst = post(surface);

            // build physics AFTER post-scale, but do not change returned type (instance)
            // (physics attachment is side-effect; the actual body handle lives in engine registries)
            if (physCfg != null) this.phys.withBody(this.terr, inst.surface, physCfg, "dynamicMesh");

            return inst;
        }

        // ------------------------------------------------------------
        // heights: accept array-like -> f32, infer size if possible, validate dims, create terrainHeights
        // ------------------------------------------------------------
        if (kind === "heights") {
            const heightsIn = c.heights;
            if (!heightsIn) throw new Error("[TERR] create(kind='heights'): cfg.heights is required");

            const heights = TerrainHeights.toFloat32Array(heightsIn);

            // allow either cfg.terrain.{size,patchSize} or top-level size/patchSize
            const tcfg0 = isObj(c.terrain) ? c.terrain : {};
            const size = i32(tcfg0.size, i32(c.size, 0)) || TerrainHeights.inferSizeFromHeights(heights);
            const patchSize = i32(tcfg0.patchSize, i32(c.patchSize, 0));

            if (size > 0) {
                TerrainHeights.validateTerrainDims(size, patchSize || 0);
                TerrainHeights.assertHeightsMatchSize(heights, size, "create(kind='heights')");
            }

            const tcfgNoPhys = Object.assign({}, tcfg0, {
                name: c.name,
                heights,
                // keep explicit size/patchSize if known
                size: size || undefined,
                patchSize: patchSize || undefined,
                attach: attachFlag,
            });

            let surface = this.terr.terrainHeights(tcfgNoPhys);

            const inst = post(surface);

            // IMPORTANT: build physics AFTER post-scale (dynamicMesh)
            if (physCfg != null) this.phys.withBody(this.terr, inst.surface, physCfg, "dynamicMesh");

            return inst;
        }

        // ------------------------------------------------------------
        // default: treat as heightmap/terrain cfg
        // ------------------------------------------------------------
        return post(this.p.terrain(Object.assign({}, isObj(c.terrain) ? c.terrain : c)));
    }
}

module.exports = {TerrainCreateHelper};