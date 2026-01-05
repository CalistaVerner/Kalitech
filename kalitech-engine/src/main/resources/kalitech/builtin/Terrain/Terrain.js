// FILE: resources/kalitech/builtin/Terrain.js
// Author: Calista Verner
"use strict";

const META = {
    name: "Terrain",
    globalName: "TERR",
    version: "1.0.0",
    engineMin: "0.1.0",
    description: "Terrain helpers (TerrainQuad, plane/quad) + physics integration",
};

function _isObj(v) { return !!v && typeof v === "object" && !Array.isArray(v); }
function _num(v, def) { const n = +v; return Number.isFinite(n) ? n : (def || 0); }

function _warn(msg) {
    try { if (typeof LOG !== "undefined" && LOG && LOG.warn) LOG.warn(String(msg)); } catch (_) {}
}

function _surfaceIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    return 0;
}

function _bodyIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    return 0;
}

/**
 * Resolve bodyId for surface without creating duplicates.
 * Order:
 *  1) engine.surface().attachedBody(surfaceId)
 *  2) engine.physics().bodyOfSurface(surfaceId)
 *  3) if we got a handle from terrain.physics(..) use its id
 */
function _resolveBodyId(engine, surfaceHandleOrId, maybeBodyHandleOrId) {
    const sid = _surfaceIdOf(surfaceHandleOrId);
    if (sid <= 0) return 0;

    try {
        const s = engine.surface && engine.surface();
        if (s && typeof s.attachedBody === "function") {
            const bid = _bodyIdOf(s.attachedBody(sid));
            if (bid > 0) return bid;
        }
    } catch (e) {
        // ignore
    }

    try {
        const p = engine.physics && engine.physics();
        if (p && typeof p.bodyOfSurface === "function") {
            const bid = _bodyIdOf(p.bodyOfSurface(sid));
            if (bid > 0) return bid;
        }
    } catch (e) {
        // ignore
    }

    const bid = _bodyIdOf(maybeBodyHandleOrId);
    return (bid > 0) ? bid : 0;
}

function _cloneCfg(cfg) {
    return _isObj(cfg) ? Object.assign({}, cfg) : {};
}

/**
 * Create a static body for a surface without creating duplicates.
 * Prefers already-attached body if present.
 */
function _ensureStaticBody(engine, surfaceHandleOrId, physCfg, defaultColliderType) {
    const sid = _surfaceIdOf(surfaceHandleOrId);
    if (sid <= 0) return { bodyId: 0, bodyHandle: null };

    // If already attached, don't create a duplicate.
    const existing = _resolveBodyId(engine, sid, null);
    if (existing > 0) return { bodyId: existing, bodyHandle: null };

    const base = {
        surface: sid,
        mass: 0,
        kinematic: true,
        collider: { type: defaultColliderType || "mesh" },
    };

    const cfg = _isObj(physCfg) ? Object.assign(base, physCfg) : base;

    let bodyHandle = null;
    try {
        if (typeof PHYS !== "undefined" && PHYS && typeof PHYS.body === "function") {
            bodyHandle = PHYS.body(cfg);
        } else {
            const p = engine.physics && engine.physics();
            if (p && typeof p.body === "function") bodyHandle = p.body(cfg);
        }
    } catch (e) {
        _warn("[TERR] ensureStaticBody failed: " + (e && e.message ? e.message : e));
    }

    const bodyId = _resolveBodyId(engine, sid, bodyHandle);
    return { bodyId, bodyHandle };
}

function makeApi(engine) {
    if (!engine) throw new Error("[TERR] engine is required");
    const terr = engine.terrain ? engine.terrain() : null;
    if (!terr) throw new Error("[TERR] engine.terrain() is not available");

    /**
     * Create heightmap terrain.
     * If cfg.physics is provided: creates (or resolves) static body for the terrain.
     * Returns:
     *  - surface handle by default
     *  - { surface, bodyId, body? } when cfg.physics provided
     */
    function terrain(cfg) {
        const c = _cloneCfg(cfg);
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.terrain(c);

        if (physCfg == null) return surface;

        // Prefer Java-side convenience wrapper (terrain.physics)
        let bodyHandle;
        try {
            bodyHandle = terr.physics(surface, physCfg);
        } catch (e) {
            // Fallback (older builds): PHYS.ensureBodyForSurface
            try {
                if (typeof PHYS !== "undefined" && PHYS && typeof PHYS.ensureBodyForSurface === "function") {
                    bodyHandle = PHYS.ensureBodyForSurface(surface, Object.assign({ mass: 0, kinematic: true, collider: { type: "mesh" } }, physCfg || {}));
                } else {
                    throw e;
                }
            } catch (e2) {
                _warn("[TERR] terrain.physics failed: " + (e2 && e2.message ? e2.message : e2));
            }
        }

        const bodyId = _resolveBodyId(engine, surface, bodyHandle);
        const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function") ? PHYS.ref(bodyId) : undefined;
        return Object.freeze({ surface, bodyId, body: bodyRef });
    }

    function terrainHeights(cfg) {
        const c = _cloneCfg(cfg);
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.terrainHeights(c);
        if (physCfg == null) return surface;

        let bodyHandle;
        try {
            bodyHandle = terr.physics(surface, physCfg);
        } catch (e) {
            try {
                if (typeof PHYS !== "undefined" && PHYS && typeof PHYS.ensureBodyForSurface === "function") {
                    bodyHandle = PHYS.ensureBodyForSurface(surface, Object.assign({ mass: 0, kinematic: true, collider: { type: "mesh" } }, physCfg || {}));
                } else {
                    throw e;
                }
            } catch (e2) {
                _warn("[TERR] terrainHeights.physics failed: " + (e2 && e2.message ? e2.message : e2));
            }
        }

        const bodyId = _resolveBodyId(engine, surface, bodyHandle);
        const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function") ? PHYS.ref(bodyId) : undefined;
        return Object.freeze({ surface, bodyId, body: bodyRef });
    }

    function quad(cfg) {
        const c = _cloneCfg(cfg);
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.quad(c);
        if (physCfg == null) return surface;

        // quad() returns Geometry (not TerrainQuad) -> use generic surface-based body.
        const made = _ensureStaticBody(engine, surface, physCfg, "mesh");
        const bodyId = made.bodyId;
        const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function") ? PHYS.ref(bodyId) : undefined;
        return Object.freeze({ surface, bodyId, body: bodyRef });
    }

    function plane(cfg) {
        const c = _cloneCfg(cfg);
        const physCfg = c.physics;
        if (physCfg != null) delete c.physics;

        const surface = terr.plane(c);
        if (physCfg == null) return surface;

        // plane() returns Geometry (not TerrainQuad) -> use generic surface-based body.
        const made = _ensureStaticBody(engine, surface, physCfg, "mesh");
        const bodyId = made.bodyId;
        const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function") ? PHYS.ref(bodyId) : undefined;
        return Object.freeze({ surface, bodyId, body: bodyRef });
    }

    function physics(surfaceHandleOrId, cfg) {
        if (!surfaceHandleOrId) throw new Error("TERR.physics(surface,cfg): surface handle/id required");

        // terrain.physics(...) supports only TerrainQuad. For other surfaces (plane/quad/geometry), fallback to PHYS.body.
        let bodyHandle = null;
        let bodyId = 0;
        try {
            bodyHandle = terr.physics(surfaceHandleOrId, cfg || {});
            bodyId = _resolveBodyId(engine, surfaceHandleOrId, bodyHandle);
        } catch (e) {
            const made = _ensureStaticBody(engine, surfaceHandleOrId, cfg || {}, "mesh");
            bodyId = made.bodyId;
            bodyHandle = made.bodyHandle;
        }
        const bodyRef = (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function") ? PHYS.ref(bodyId) : undefined;
        return Object.freeze({ bodyId, body: bodyRef, handle: bodyHandle });
    }

    function material(surfaceHandle, materialHandleOrCfg) {
        return terr.material(surfaceHandle, materialHandleOrCfg);
    }

    function lod(surfaceHandle, cfg) {
        return terr.lod(surfaceHandle, cfg || {});
    }

    function scale(surfaceHandle, xzScale, cfg) {
        return terr.scale(surfaceHandle, _num(xzScale, 1.0), cfg || null);
    }

    function heightAt(surfaceHandle, x, z, world) {
        if (world === undefined) return terr.heightAt(surfaceHandle, _num(x, 0), _num(z, 0));
        return terr.heightAt(surfaceHandle, _num(x, 0), _num(z, 0), !!world);
    }

    function normalAt(surfaceHandle, x, z, world) {
        if (world === undefined) return terr.normalAt(surfaceHandle, _num(x, 0), _num(z, 0));
        return terr.normalAt(surfaceHandle, _num(x, 0), _num(z, 0), !!world);
    }

    // ------------------------------------------------------------------
    // TerrainQuad editing
    // ------------------------------------------------------------------

    function setHeightmap(surfaceHandle, heights, size, rebuild) {
        // Accept cfg object OR positional args.
        if (_isObj(heights)) {
            return terr.setHeightmap(surfaceHandle, heights);
        }
        return terr.setHeightmap(surfaceHandle, {
            heights: heights,
            size: (size | 0) || undefined,
            rebuild: (rebuild === undefined) ? true : !!rebuild,
        });
    }

    function heightmap(surfaceHandle) {
        return terr.heightmap(surfaceHandle);
    }

    function setHeight(surfaceHandle, x, z, height, world) {
        if (world === undefined) return terr.setHeight(surfaceHandle, _num(x, 0), _num(z, 0), _num(height, 0));
        return terr.setHeight(surfaceHandle, _num(x, 0), _num(z, 0), _num(height, 0), !!world);
    }

    function adjustHeight(surfaceHandle, x, z, delta, world) {
        if (world === undefined) return terr.adjustHeight(surfaceHandle, _num(x, 0), _num(z, 0), _num(delta, 0));
        return terr.adjustHeight(surfaceHandle, _num(x, 0), _num(z, 0), _num(delta, 0), !!world);
    }

    function rebuild(surfaceHandle) {
        return terr.rebuild(surfaceHandle);
    }

    function size(surfaceHandle) {
        return terr.size(surfaceHandle) | 0;
    }

    function patchSize(surfaceHandle) {
        return terr.patchSize(surfaceHandle) | 0;
    }

    // ------------------------------------------------------------------
    // Procedural generation (pure JS)
    // ------------------------------------------------------------------

    function _hash2(ix, iy, seed) {
        // 32-bit mix (deterministic across JS runtimes)
        let h = (ix | 0) * 374761393 + (iy | 0) * 668265263 + (seed | 0) * 1442695041;
        h = (h ^ (h >>> 13)) | 0;
        h = (h * 1274126177) | 0;
        h = (h ^ (h >>> 16)) | 0;
        return h | 0;
    }

    function _fade(t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    function _lerp(a, b, t) { return a + (b - a) * t; }

    function _grad(ix, iy, seed) {
        const h = _hash2(ix, iy, seed);
        // 8 directions on unit circle (cheap)
        const r = h & 7;
        switch (r) {
            case 0: return [ 1, 0];
            case 1: return [-1, 0];
            case 2: return [ 0, 1];
            case 3: return [ 0,-1];
            case 4: return [ 0.70710678, 0.70710678];
            case 5: return [-0.70710678, 0.70710678];
            case 6: return [ 0.70710678,-0.70710678];
            default:return [-0.70710678,-0.70710678];
        }
    }

    function _perlin2(x, y, seed) {
        const x0 = Math.floor(x), y0 = Math.floor(y);
        const x1 = x0 + 1, y1 = y0 + 1;
        const sx = x - x0, sy = y - y0;

        const g00 = _grad(x0, y0, seed), g10 = _grad(x1, y0, seed);
        const g01 = _grad(x0, y1, seed), g11 = _grad(x1, y1, seed);

        const dx0 = sx,     dy0 = sy;
        const dx1 = sx - 1, dy1 = sy;
        const dx2 = sx,     dy2 = sy - 1;
        const dx3 = sx - 1, dy3 = sy - 1;

        const n00 = g00[0] * dx0 + g00[1] * dy0;
        const n10 = g10[0] * dx1 + g10[1] * dy1;
        const n01 = g01[0] * dx2 + g01[1] * dy2;
        const n11 = g11[0] * dx3 + g11[1] * dy3;

        const u = _fade(sx);
        const v = _fade(sy);
        const nx0 = _lerp(n00, n10, u);
        const nx1 = _lerp(n01, n11, u);
        return _lerp(nx0, nx1, v);
    }

    function perlinHeights(cfg) {
        // Prefer native generator when available (editor/runtime parity)
        try {
            if (terr && typeof terr.perlinHeights === "function") {
                const raw = terr.perlinHeights(cfg || {});
                // Convert Java float[] (or array-like) to Float32Array for fast JS loops.
                if (raw instanceof Float32Array) return raw;
                if (raw && typeof raw.length === "number") {
                    const out = new Float32Array(raw.length | 0);
                    for (let i = 0; i < out.length; i++) out[i] = +raw[i] || 0;
                    return out;
                }
                return raw;
            }
        } catch (e) {
            // fall back to pure JS
        }

        const c = _isObj(cfg) ? cfg : {};
        const size = (c.size | 0) || 513;
        const seed = (c.seed | 0) || 0;
        const scale = Math.max(0.0001, _num(c.scale, 64));
        const octaves = Math.max(1, (c.octaves | 0) || 5);
        const persistence = _num(c.persistence, 0.5);
        const lacunarity = _num(c.lacunarity, 2.0);
        const normalize = (c.normalize === undefined) ? true : !!c.normalize;

        const out = new Float32Array(size * size);
        let min =  1e9, max = -1e9;

        for (let z = 0; z < size; z++) {
            for (let x = 0; x < size; x++) {
                let amp = 1.0;
                let freq = 1.0;
                let sum = 0.0;
                for (let o = 0; o < octaves; o++) {
                    const nx = (x / scale) * freq;
                    const nz = (z / scale) * freq;
                    sum += _perlin2(nx, nz, seed + o * 1013) * amp;
                    amp *= persistence;
                    freq *= lacunarity;
                }
                const i = z * size + x;
                out[i] = sum;
                if (sum < min) min = sum;
                if (sum > max) max = sum;
            }
        }

        if (normalize && max > min) {
            const inv = 1.0 / (max - min);
            for (let i = 0; i < out.length; i++) out[i] = (out[i] - min) * inv;
        }

        return out;
    }

    function ridgedHeights(cfg) {
        // Prefer native generator when available
        try {
            if (terr && typeof terr.ridgedHeights === "function") {
                const raw = terr.ridgedHeights(cfg || {});
                if (raw instanceof Float32Array) return raw;
                if (raw && typeof raw.length === "number") {
                    const out = new Float32Array(raw.length | 0);
                    for (let i = 0; i < out.length; i++) out[i] = +raw[i] || 0;
                    return out;
                }
                return raw;
            }
        } catch (e) {
            // fall back to pure JS
        }

        const c = _isObj(cfg) ? cfg : {};
        const base = perlinHeights(Object.assign({}, c, { normalize: false }));
        let min =  1e9, max = -1e9;
        for (let i = 0; i < base.length; i++) {
            const v = 1.0 - Math.abs(base[i]);
            base[i] = v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        const normalize = (c.normalize === undefined) ? true : !!c.normalize;
        if (normalize && max > min) {
            const inv = 1.0 / (max - min);
            for (let i = 0; i < base.length; i++) base[i] = (base[i] - min) * inv;
        }
        return base;
    }

    function procedural(cfg) {
        const c = _cloneCfg(cfg);
        const gen = _isObj(c.gen) ? c.gen : {};
        const type = String(gen.type || "perlin");
        const size = (c.size | 0) || (gen.size | 0) || 513;
        const yScale = _num(c.yScale, _num(c.heightScale, 1));

        let heights;
        if (type === "ridged") heights = ridgedHeights(Object.assign({}, gen, { size }));
        else heights = perlinHeights(Object.assign({}, gen, { size }));

        // If normalized to [0..1], remap to [-1..1] then scale
        if (gen.normalize !== false) {
            for (let i = 0; i < heights.length; i++) heights[i] = (heights[i] * 2.0 - 1.0) * yScale;
        } else {
            for (let i = 0; i < heights.length; i++) heights[i] = heights[i] * yScale;
        }

        c.heights = heights;
        c.size = size;
        // yScale already baked; keep xzScale etc
        return terrainHeights(c);
    }

    function attach(surfaceHandle, entityId) {
        return terr.attach(surfaceHandle, entityId | 0);
    }

    function detach(surfaceHandle) {
        return terr.detach(surfaceHandle);
    }

    return Object.freeze({
        META,

        terrain,
        terrainHeights,
        quad,
        plane,

        material,
        lod,
        scale,
        heightAt,
        normalAt,

        setHeightmap,
        heightmap,
        setHeight,
        adjustHeight,
        rebuild,
        size,
        patchSize,

        perlinHeights,
        ridgedHeights,
        procedural,

        physics,
        attach,
        detach,
    });
}

module.exports = function TerrainModule(engine, K) {
    return makeApi(engine, K);
};

module.exports.META = META;