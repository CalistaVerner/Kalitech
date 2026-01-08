// FILE: resources/kalitech/builtin/Terrain.js
// Author: Calista Verner (v2 API overhaul)
//
// Terrain Builtin — v2.0.1 (Kalitech)
// Changes / Fixes in this patch:
//  - FIX: TerrainQuad physics collider is now created AFTER post-processing scale is applied
//         for declarative builders: kind="heights" and kind="noise".
//         This prevents "no collisions / falling through terrain" caused by collider being built
//         from the unscaled TerrainQuad and then visually scaling the terrain later.
//  - FIX: Default collider hint for terrain builders uses "dynamicMesh" (TerrainQuad is a Node of patches),
//         which is required for reliable collision shape creation.
//  - Behavior: JS API remains simple — TERR.create({ kind:"heights", heights, ... , physics:{...} })
//         works without requiring the script to pre-bake/convert height arrays specifically for physics timing.
//
// Compatibility notes:
//  - Low-level functions terrain()/terrainHeights()/plane()/quad() are kept compatible.
//  - Declarative create() path for heights/noise builds physics post-scale; others remain unchanged.
//
// "physics" in cfg is optional; when provided, a static body is attached to the created surface.
// For TerrainQuad, prefer collider.type="dynamicMesh" (or omit type and let defaults apply).
"use strict";

const META = Object.freeze({
    moduleId: "terrain",
    globalName: "TERR",
    version: "2.0.0",
    engineMin: "0.1.0",
    description: "Declarative terrain builder (plane/quad/heightmap/heights/noise) + physics + edit/query",
});

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function num(v, def) {
    const n = +v;
    return Number.isFinite(n) ? n : def;
}

function i32(v, def) {
    const n = (v | 0);
    return n !== 0 ? n : (def | 0);
}

function warn(msg) {
    try {
        if (typeof LOG !== "undefined" && LOG && LOG.warn) LOG.warn(String(msg));
    } catch (_) {
    }
}

function errStr(e) {
    try {
        return (e && (e.stack || e.message)) ? String(e.stack || e.message) : String(e);
    } catch (_) {
        return "" + e;
    }
}

function surfaceIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    return 0;
}

function bodyIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    return 0;
}

function toFloat32Array(raw) {
    if (!raw) return null;
    if (raw instanceof Float32Array) return raw;

    try {
        if (typeof Java !== "undefined" && Java && typeof Java.from === "function") {
            const a = Java.from(raw);
            if (Array.isArray(a)) {
                const out = new Float32Array(a.length);
                for (let i = 0; i < a.length; i++) out[i] = +a[i] || 0;
                return out;
            }
        }
    } catch (_) {
    }

    if (Array.isArray(raw)) {
        const out = new Float32Array(raw.length);
        for (let i = 0; i < out.length; i++) out[i] = +raw[i] || 0;
        return out;
    }

    try {
        if (typeof raw.length === "number") {
            const out = new Float32Array(raw.length | 0);
            for (let i = 0; i < out.length; i++) out[i] = +raw[i] || 0;
            return out;
        }
    } catch (_) {
    }

    return raw;
}

function inferSizeFromHeights(heights) {
    if (!heights) return 0;
    const len = (typeof heights.length === "number") ? (heights.length | 0) : 0;
    if (len <= 0) return 0;
    const s = Math.round(Math.sqrt(len));
    return (s > 0 && s * s === len) ? s : 0;
}

function isPow2(n) {
    return n > 0 && (n & (n - 1)) === 0;
}

function isJmeTerrainSize(n) {
    const x = (n | 0) - 1;
    return x > 0 && isPow2(x);
}

function validateTerrainDims(size, patchSize) {
    const s = size | 0;
    const p = patchSize | 0;
    if (s > 0 && !isJmeTerrainSize(s)) throw new Error(`[TERR] size must be (2^k + 1). Got size=${s}`);
    if (p > 0 && !isJmeTerrainSize(p)) throw new Error(`[TERR] patchSize must be (2^k + 1). Got patchSize=${p}`);
    if (s > 0 && p > 0 && p > s) throw new Error(`[TERR] patchSize must be <= size. Got patchSize=${p} size=${s}`);
}

function resolveBodyId(engine, surfaceHandleOrId, maybeBodyHandleOrId) {
    const sid = surfaceIdOf(surfaceHandleOrId);
    if (sid <= 0) return 0;

    try {
        const s = engine.surface && engine.surface();
        if (s && typeof s.attachedBody === "function") {
            const bid = bodyIdOf(s.attachedBody(sid));
            if (bid > 0) return bid;
        }
    } catch (_) {
    }

    try {
        const p = engine.physics && engine.physics();
        if (p && typeof p.bodyOfSurface === "function") {
            const bid = bodyIdOf(p.bodyOfSurface(sid));
            if (bid > 0) return bid;
        }
    } catch (_) {
    }

    const bid = bodyIdOf(maybeBodyHandleOrId);
    return (bid > 0) ? bid : 0;
}

function ensureStaticBody(engine, surfaceHandleOrId, physCfg, defaultColliderType) {
    const sid = surfaceIdOf(surfaceHandleOrId);
    if (sid <= 0) return {bodyId: 0, bodyHandle: null};

    const existing = resolveBodyId(engine, sid, null);
    if (existing > 0) return {bodyId: existing, bodyHandle: null};

    const base = {
        surface: sid,
        mass: 0,
        kinematic: true,
        collider: {type: defaultColliderType || "mesh"},
    };
    const cfg = isObj(physCfg) ? Object.assign(base, physCfg) : base;

    let bodyHandle = null;
    try {
        if (typeof PHYS !== "undefined" && PHYS && typeof PHYS.body === "function") {
            bodyHandle = PHYS.body(cfg);
        } else {
            const p = engine.physics && engine.physics();
            if (p && typeof p.body === "function") bodyHandle = p.body(cfg);
        }
    } catch (e) {
        warn("[TERR] ensureStaticBody failed: " + errStr(e));
    }

    const bodyId = resolveBodyId(engine, sid, bodyHandle);
    return {bodyId, bodyHandle};
}

function withBody(engine, terr, surface, physCfg, defaultColliderType) {
    if (physCfg == null) return surface;

    let bodyHandle = null;

    try {
        if (terr && typeof terr.physics === "function") {
            bodyHandle = terr.physics(surface, physCfg);
        }
    } catch (_) {
    }

    const sid = surfaceIdOf(surface);
    let bodyId = resolveBodyId(engine, sid, bodyHandle);
    if (bodyId <= 0) {
        const made = ensureStaticBody(engine, surface, physCfg, defaultColliderType || "mesh");
        bodyId = made.bodyId;
        bodyHandle = bodyHandle || made.bodyHandle;
    }

    const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function")
        ? PHYS.ref(bodyId)
        : undefined;

    return Object.freeze({surface, bodyId, body: bodyRef, handle: bodyHandle});
}

function makeApi(engine) {
    if (!engine) throw new Error("[TERR] engine is required");
    const terr = engine.terrain ? engine.terrain() : null;
    if (!terr) throw new Error("[TERR] engine.terrain() is not available");

    function terrain(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;
        if (c.size || c.patchSize) validateTerrainDims(i32(c.size, 0), i32(c.patchSize, 0));
        const surface = terr.terrain(c);
        return withBody(engine, terr, surface, physCfg, "mesh");
    }

    function terrainHeights(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const heights = c.heights;
        if (heights != null) {
            const h = (heights instanceof Float32Array) ? heights : toFloat32Array(heights);
            c.heights = h;
        }

        const size = i32(c.size, 0) || inferSizeFromHeights(c.heights);
        if (size > 0) c.size = size;

        if (c.size || c.patchSize) validateTerrainDims(i32(c.size, 0), i32(c.patchSize, 0));

        if (c.heights != null && i32(c.size, 0) > 0) {
            const need = (c.size | 0) * (c.size | 0);
            const got = (typeof c.heights.length === "number") ? (c.heights.length | 0) : 0;
            if (got && got !== need) {
                throw new Error(`[TERR] terrainHeights: heights length must be size*size (${need}), got ${got} (size=${c.size})`);
            }
        }

        const surface = terr.terrainHeights(c);
        return withBody(engine, terr, surface, physCfg, "mesh");
    }

    function quad(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;
        const surface = terr.quad(c);
        return withBody(engine, terr, surface, physCfg, "mesh");
    }

    function plane(cfg) {
        const c = isObj(cfg) ? Object.assign({}, cfg) : {};
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;
        const surface = terr.plane(c);
        return withBody(engine, terr, surface, physCfg, "mesh");
    }

    function physics(surfaceHandleOrId, cfg) {
        if (!surfaceHandleOrId) throw new Error("TERR.physics(surface,cfg): surface handle/id required");
        return withBody(engine, terr, surfaceHandleOrId, cfg || {}, "mesh");
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

        const h = (heights instanceof Float32Array) ? heights : toFloat32Array(heights);
        const s = (size | 0) || inferSizeFromHeights(h);

        if (s > 0) {
            const need = s * s;
            const got = (typeof h.length === "number") ? (h.length | 0) : 0;
            if (got && got !== need) {
                throw new Error(`[TERR] setHeightmap: heights length=${got} expected=${need} (size=${s})`);
            }
        }

        return terr.setHeightmap(surfaceHandle, {
            heights: h,
            size: s || undefined,
            rebuild: (rebuild === undefined) ? true : !!rebuild,
        });
    }

    function heightmap(surfaceHandle) {
        return toFloat32Array(terr.heightmap(surfaceHandle));
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

    function perlinHeights(cfg) {
        try {
            if (terr && typeof terr.perlinHeights === "function") {
                return toFloat32Array(terr.perlinHeights(cfg || {}));
            }
        } catch (_) {
        }
        throw new Error("[TERR] perlinHeights: native generator not available in this build");
    }

    function ridgedHeights(cfg) {
        try {
            if (terr && typeof terr.ridgedHeights === "function") {
                return toFloat32Array(terr.ridgedHeights(cfg || {}));
            }
        } catch (_) {
        }
        throw new Error("[TERR] ridgedHeights: native generator not available in this build");
    }

    const heightsNS = Object.freeze({
        perlin: perlinHeights,
        ridged: ridgedHeights,
        sizeOf: inferSizeFromHeights,
        toF32: toFloat32Array,
    });

    function create(cfg) {
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

        function post(surfaceOrBundle) {
            const surface = surfaceOrBundle && surfaceOrBundle.surface ? surfaceOrBundle.surface : surfaceOrBundle;

            try {
                if (materialH != null) material(surface, materialH);
            } catch (e) {
                warn("[TERR] material failed: " + errStr(e));
            }
            try {
                if (uvCfg != null) uv(surface, uvCfg);
            } catch (e) {
                warn("[TERR] uv failed: " + errStr(e));
            }
            try {
                if (lodCfg != null) lod(surface, lodCfg);
            } catch (e) {
                warn("[TERR] lod failed: " + errStr(e));
            }

            try {
                if (kind !== "plane" && kind !== "quad") {
                    if (Number.isFinite(xz) && xz !== 1.0) scale(surface, xz, {yScale: y});
                    else if (Number.isFinite(y) && y !== 1.0) scale(surface, 1.0, {yScale: y});
                }
            } catch (e) {
                warn("[TERR] scale failed: " + errStr(e));
            }

            return surfaceOrBundle;
        }

        if (kind === "plane") {
            const planeCfg = Object.assign({}, isObj(c.plane) ? c.plane : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            return post(plane(planeCfg));
        }

        if (kind === "quad") {
            const quadCfg = Object.assign({}, isObj(c.quad) ? c.quad : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            return post(quad(quadCfg));
        }

        if (kind === "heightmap") {
            const tcfg = Object.assign({}, isObj(c.terrain) ? c.terrain : {}, {
                name: c.name,
                attach: attachFlag,
                physics: physCfg,
            });
            if (c.heightmap && !tcfg.heightmap) tcfg.heightmap = c.heightmap;
            if (tcfg.heightScale == null && Number.isFinite(y)) tcfg.heightScale = y;
            if (tcfg.xzScale == null && Number.isFinite(xz)) tcfg.xzScale = xz;
            return post(terrain(tcfg));
        }

        if (kind === "noise") {
            const noise = isObj(c.noise) ? c.noise : {};
            const type = String(noise.type || "perlin");
            const size = i32((isObj(c.terrain) ? c.terrain.size : c.size), i32(noise.size, 513)) || 513;
            const patchSize = i32((isObj(c.terrain) ? c.terrain.patchSize : c.patchSize), 65) || 65;

            validateTerrainDims(size, patchSize);

            const h = (type === "ridged") ? ridgedHeights(Object.assign({}, noise, {size})) : perlinHeights(Object.assign({}, noise, {size}));
            const normalize = (noise.normalize === undefined) ? true : !!noise.normalize;

            const out = new Float32Array(h.length);
            if (normalize) {
                for (let i = 0; i < h.length; i++) out[i] = (h[i] * 2.0 - 1.0) * y;
            } else {
                for (let i = 0; i < h.length; i++) out[i] = h[i] * y;
            }

            const tcfg0 = isObj(c.terrain) ? c.terrain : {};
            const tcfgNoPhys = Object.assign({}, tcfg0, {
                name: c.name,
                size,
                patchSize,
                heights: out,
                attach: attachFlag,
            });

            let surface = terr.terrainHeights(tcfgNoPhys);
            surface = post(surface);

            if (physCfg != null) surface = withBody(engine, terr, surface, physCfg, "dynamicMesh");
            return surface;
        }

        if (kind === "heights") {
            const heightsIn = c.heights;
            if (!heightsIn) throw new Error("[TERR] create(kind='heights'): cfg.heights is required");

            const tcfg0 = isObj(c.terrain) ? c.terrain : {};
            const tcfgNoPhys = Object.assign({}, tcfg0, {
                name: c.name,
                heights: heightsIn,
                attach: attachFlag,
            });

            let surface = terr.terrainHeights(tcfgNoPhys);
            surface = post(surface);

            if (physCfg != null) surface = withBody(engine, terr, surface, physCfg, "dynamicMesh");
            return surface;
        }

        return post(terrain(Object.assign({}, isObj(c.terrain) ? c.terrain : c)));
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