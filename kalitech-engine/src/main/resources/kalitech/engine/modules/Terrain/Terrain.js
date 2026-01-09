// FILE: resources/kalitech/builtin/Terrain.js
// Author: Calista Verner (v2 API overhaul)
"use strict";

const META = Object.freeze({
    moduleId: "terrain",
    globalName: "TERR",
    version: "2.0.0",
    engineMin: "0.1.0",
    description: "Declarative terrain builder (plane/quad/heightmap/heights/noise) + physics + edit/query",
});

const {isObj, num, i32} = require("./helpers/TerrainTypes.js");
const {TerrainHeights} = require("./helpers/TerrainHeights.js");
const {TerrainPhysics} = require("./helpers/TerrainPhysics.js");
const {TerrainCreateHelper} = require("./helpers/TerrainCreateHelper.js");

function makeApi(engine) {
    if (!engine) throw new Error("[TERR] engine is required");
    const terr = (engine.terrain && typeof engine.terrain === "function") ? engine.terrain() : null;
    if (!terr) throw new Error("[TERR] engine.terrain() is not available");

    const heightsApi = new TerrainHeights(terr);
    const physicsApi = new TerrainPhysics(engine);

    function terrain(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        if (c.size || c.patchSize) TerrainHeights.validateTerrainDims(i32(c.size, 0), i32(c.patchSize, 0));

        const surface = terr.terrain(c);
        return physicsApi.withBody(terr, surface, physCfg, "mesh");
    }

    function terrainHeights(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const heights = c.heights;
        if (heights != null) c.heights = TerrainHeights.toFloat32Array(heights);

        const size = i32(c.size, 0) || TerrainHeights.inferSizeFromHeights(c.heights);
        if (size > 0) c.size = size;

        if (c.size || c.patchSize) TerrainHeights.validateTerrainDims(i32(c.size, 0), i32(c.patchSize, 0));
        TerrainHeights.assertHeightsMatchSize(c.heights, c.size, "terrainHeights");

        const surface = terr.terrainHeights(c);
        return physicsApi.withBody(terr, surface, physCfg, "mesh");
    }

    function quad(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.quad(c);
        return physicsApi.withBody(terr, surface, physCfg, "mesh");
    }

    function plane(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.plane(c);
        return physicsApi.withBody(terr, surface, physCfg, "mesh");
    }

    function physics(surfaceHandleOrId, cfg) {
        if (!surfaceHandleOrId) throw new Error("TERR.physics(surface,cfg): surface handle/id required");
        return physicsApi.withBody(terr, surfaceHandleOrId, cfg || {}, "mesh");
    }

    function material(surfaceHandle, materialHandleOrCfg) {
        return terr.material(surfaceHandle, materialHandleOrCfg);
    }

    function uv(surfaceHandle, cfg) {
        return terr.uv(surfaceHandle, cfg);
    }

    function lod(surfaceHandle, cfg) {
        return terr.lod(surfaceHandle, cfg || {});
    }

    function scale(surfaceHandle, xzScale, cfg) {
        return terr.scale(surfaceHandle, num(xzScale, 1.0), cfg || null);
    }

    function heightAt(surfaceHandle, x, z, world) {
        if (world === undefined) return terr.heightAt(surfaceHandle, num(x, 0), num(z, 0));
        return terr.heightAt(surfaceHandle, num(x, 0), num(z, 0), !!world);
    }

    function normalAt(surfaceHandle, x, z, world) {
        if (world === undefined) return terr.normalAt(surfaceHandle, num(x, 0), num(z, 0));
        return terr.normalAt(surfaceHandle, num(x, 0), num(z, 0), !!world);
    }

    function setHeightmap(surfaceHandle, heights, size, rebuild) {
        if (isObj(heights)) return terr.setHeightmap(surfaceHandle, heights);

        const h = (heights instanceof Float32Array) ? heights : TerrainHeights.toFloat32Array(heights);
        const s = (size | 0) || TerrainHeights.inferSizeFromHeights(h);

        if (s > 0) TerrainHeights.assertHeightsMatchSize(h, s, "setHeightmap");

        return terr.setHeightmap(surfaceHandle, {
            heights: h,
            size: s || undefined,
            rebuild: (rebuild === undefined) ? true : !!rebuild,
        });
    }

    function heightmap(surfaceHandle) {
        return TerrainHeights.toFloat32Array(terr.heightmap(surfaceHandle));
    }

    function setHeight(surfaceHandle, x, z, height, world) {
        if (world === undefined) return terr.setHeight(surfaceHandle, num(x, 0), num(z, 0), num(height, 0));
        return terr.setHeight(surfaceHandle, num(x, 0), num(z, 0), num(height, 0), !!world);
    }

    function adjustHeight(surfaceHandle, x, z, delta, world) {
        if (world === undefined) return terr.adjustHeight(surfaceHandle, num(x, 0), num(z, 0), num(delta, 0));
        return terr.adjustHeight(surfaceHandle, num(x, 0), num(z, 0), num(delta, 0), !!world);
    }

    function rebuild(surfaceHandle) {
        return terr.rebuild(surfaceHandle);
    }

    function attach(surfaceHandle, entityId) {
        return terr.attach(surfaceHandle, entityId | 0);
    }

    function detach(surfaceHandle) {
        return terr.detach(surfaceHandle);
    }

    const heightsNS = Object.freeze({
        perlin: (cfg) => heightsApi.perlin(cfg || {}),
        ridged: (cfg) => heightsApi.ridged(cfg || {}),
        sizeOf: TerrainHeights.inferSizeFromHeights,
        toF32: TerrainHeights.toFloat32Array,
    });

    const createHelper = new TerrainCreateHelper(
        engine,
        terr,
        heightsApi,
        physicsApi,
        {
            terrain,
            terrainHeights,
            quad,
            plane,
            physics,
            material,
            uv,
            lod,
            scale,
            heightAt,
            normalAt,
            setHeightmap,
            heightmap,
            setHeight,
            adjustHeight,
            rebuild,
            attach,
            detach
        }
    );

    function create(cfg) {
        return createHelper.create(cfg);
    }

    return Object.freeze({
        META,
        create,
        heights: heightsNS,

        terrain,
        terrainHeights,
        quad,
        plane,

        physics,
        material,
        uv,
        lod,
        scale,
        heightAt,
        normalAt,

        setHeightmap,
        heightmap,
        setHeight,
        adjustHeight,
        rebuild,

        attach,
        detach,
    });
}

module.exports = function TerrainModule(engine, K) {
    return makeApi(engine, K);
};
module.exports.META = META;