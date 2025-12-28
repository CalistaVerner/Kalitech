// FILE: resources/kalitech/builtin/Material.js
// Author: Calista Verner
"use strict";

/**
 * Materials registry builtin.
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 */

function isPlainJsObject(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function shallowClone(obj) {
    return obj ? Object.assign({}, obj) : Object.create(null);
}

function stableStringify(obj) {
    // deterministic stringify for cache keys (shallow-ish but stable)
    if (!obj || typeof obj !== "object") return String(obj);
    const keys = Object.keys(obj).sort();
    let out = "{";
    for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        const v = obj[k];
        out += (i ? "," : "") + JSON.stringify(k) + ":" + JSON.stringify(v);
    }
    out += "}";
    return out;
}

class MaterialsRegistry {
    constructor(engine, K) {
        this.engineRef = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        this.defs = null;

        // base caches (no overrides)
        this.cacheMat = Object.create(null);
        this.cacheHandle = Object.create(null);

        // per-override caches
        this.cacheMatOv = Object.create(null);     // key -> Material
        this.cacheHandleOv = Object.create(null);  // key -> handle

        // tuning
        this._enableOverrideCache = true;
        this._overrideCacheMax = 256;
        this._overrideCacheOrder = []; // simple FIFO
    }

    // ---------- core ----------

    engine() {
        const e = this.engineRef;
        if (!e) throw new Error("[MAT] engine not attached");
        return e;
    }

    dbPath() {
        const c = this.K && this.K.config && this.K.config.materials;
        return (c && c.dbPath) ? String(c.dbPath) : "data/materials.json";
    }

    loadDefs() {
        if (this.defs) return this.defs;

        const path = this.dbPath();
        let json;
        try {
            const assets = this.engine().assets && this.engine().assets();
            if (!assets || typeof assets.readText !== "function") {
                throw new Error("engine.assets().readText missing");
            }
            json = assets.readText(path);
        } catch (e) {
            throw new Error("[MAT] failed to read defs at '" + path + "': " + String(e && e.message ? e.message : e));
        }

        try {
            this.defs = JSON.parse(json);
        } catch (e) {
            throw new Error("[MAT] invalid JSON in '" + path + "': " + String(e && e.message ? e.message : e));
        }

        if (!this.defs || typeof this.defs !== "object") {
            throw new Error("[MAT] defs must be an object map in '" + path + "'");
        }
        return this.defs;
    }

    keys() {
        try { return Object.keys(this.loadDefs()); } catch (_) { return []; }
    }

    base(name) {
        const n = String(name || "");
        const all = this.loadDefs();
        const b = all[n];
        if (!b) {
            const sample = this.keys().slice(0, 12).join(", ");
            throw new Error("[MAT] unknown material: " + n + " (db=" + this.dbPath() + ", known=" + sample + (this.keys().length > 12 ? ", ..." : "") + ")");
        }
        return b;
    }

    cloneCfg(base) {
        return {
            def: base.def,
            params: shallowClone(base.params || null),
            scales: base.scales ? shallowClone(base.scales) : undefined
        };
    }

    normalizeOverrides(overrides) {
        // Accept:
        //  - null/undefined
        //  - { params:{...}, scales:{...} } (full form)
        //  - { Color:[...], Roughness:0.5 } (short form => params)
        if (!overrides) return null;

        if (overrides.params || overrides.scales) {
            return {
                params: overrides.params ? shallowClone(overrides.params) : null,
                scales: overrides.scales ? shallowClone(overrides.scales) : null
            };
        }

        if (isPlainJsObject(overrides)) {
            return { params: shallowClone(overrides), scales: null };
        }

        return null;
    }

    applyOverrides(cfg, normalized) {
        if (!normalized) return;
        if (normalized.params) Object.assign(cfg.params, normalized.params);
        if (normalized.scales) cfg.scales = Object.assign(cfg.scales || {}, normalized.scales);
    }

    unwrapMaterial(v) {
        let m = v;

        try {
            if (m && typeof m.__material === "function") m = m.__material();
        } catch (_) {}

        if (!m) throw new Error("[MAT] expected Material, got: " + String(m));

        if (isPlainJsObject(m)) {
            throw new Error("[MAT] expected host Material, got plain JS object");
        }

        return m;
    }

    // ---------- caching helpers ----------

    _ovKey(name, normalized) {
        if (!normalized) return null;
        const p = normalized.params ? stableStringify(normalized.params) : "";
        const s = normalized.scales ? stableStringify(normalized.scales) : "";
        return String(name) + "|p=" + p + "|s=" + s;
    }

    _ovCachePut(map, key, value) {
        if (!this._enableOverrideCache) return;
        map[key] = value;
        this._overrideCacheOrder.push(key);

        const max = this._overrideCacheMax | 0;
        while (this._overrideCacheOrder.length > max) {
            const old = this._overrideCacheOrder.shift();
            if (old && map[old]) delete map[old];
            if (old && this.cacheMatOv[old]) delete this.cacheMatOv[old];
            if (old && this.cacheHandleOv[old]) delete this.cacheHandleOv[old];
        }
    }

    // ---------- main API ----------

    getHandle(name, overrides = null) {
        const n = String(name || "");
        const ov = this.normalizeOverrides(overrides);

        if (!ov && this.cacheHandle[n]) return this.cacheHandle[n];

        const ovKey = this._ovKey(n, ov);
        if (ovKey && this.cacheHandleOv[ovKey]) return this.cacheHandleOv[ovKey];

        const cfg = this.cloneCfg(this.base(n));
        this.applyOverrides(cfg, ov);

        const matApi = this.engine().material && this.engine().material();
        if (!matApi || typeof matApi.create !== "function") {
            throw new Error("[MAT] engine.material().create(cfg) is required");
        }

        const h = matApi.create(cfg);

        if (!ov) this.cacheHandle[n] = h;
        else this._ovCachePut(this.cacheHandleOv, ovKey, h);

        return h;
    }

    getMaterial(name, overrides = null) {
        const n = String(name || "");
        const ov = this.normalizeOverrides(overrides);

        if (!ov && this.cacheMat[n]) return this.cacheMat[n];

        const ovKey = this._ovKey(n, ov);
        if (ovKey && this.cacheMatOv[ovKey]) return this.cacheMatOv[ovKey];

        const cfg = this.cloneCfg(this.base(n));
        this.applyOverrides(cfg, ov);

        const matApi = this.engine().material && this.engine().material();
        if (!matApi || typeof matApi.create !== "function") {
            throw new Error("[MAT] engine.material().create(cfg) is required");
        }

        const created = matApi.create(cfg);
        const mat = this.unwrapMaterial(created);

        if (!ov) this.cacheMat[n] = mat;
        else this._ovCachePut(this.cacheMatOv, ovKey, mat);

        return mat;
    }

    // ---------- sugar ----------

    get(name, overrides = null) { return this.getMaterial(name, overrides); }
    handle(name, overrides = null) { return this.getHandle(name, overrides); }

    preset(name, overrides) {
        const self = this;
        const n = String(name || "");
        const ov = this.normalizeOverrides(overrides);

        function fn(moreOverrides) {
            if (moreOverrides) {
                const a = self.normalizeOverrides(ov) || { params: null, scales: null };
                const b = self.normalizeOverrides(moreOverrides) || { params: null, scales: null };

                const merged = { params: null, scales: null };
                if (a.params || b.params) merged.params = Object.assign({}, a.params || null, b.params || null);
                if (a.scales || b.scales) merged.scales = Object.assign({}, a.scales || null, b.scales || null);

                return self.getMaterial(n, merged);
            }
            return self.getMaterial(n, ov);
        }

        fn.handle = function (moreOverrides) {
            if (moreOverrides) {
                const a = self.normalizeOverrides(ov) || { params: null, scales: null };
                const b = self.normalizeOverrides(moreOverrides) || { params: null, scales: null };

                const merged = { params: null, scales: null };
                if (a.params || b.params) merged.params = Object.assign({}, a.params || null, b.params || null);
                if (a.scales || b.scales) merged.scales = Object.assign({}, a.scales || null, b.scales || null);

                return self.getHandle(n, merged);
            }
            return self.getHandle(n, ov);
        };

        fn.name = n;
        fn.overrides = ov || null;
        return fn;
    }

    params(name, paramsObj) {
        return this.getMaterial(name, { params: paramsObj || null });
    }

    configure(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : {};
        if (cfg.overrideCache !== undefined) this._enableOverrideCache = !!cfg.overrideCache;
        if (cfg.overrideCacheMax !== undefined) this._overrideCacheMax = Math.max(0, cfg.overrideCacheMax | 0);
        return this;
    }

    reload() {
        this.defs = null;

        for (const k in this.cacheMat) delete this.cacheMat[k];
        for (const k in this.cacheHandle) delete this.cacheHandle[k];

        for (const k in this.cacheMatOv) delete this.cacheMatOv[k];
        for (const k in this.cacheHandleOv) delete this.cacheHandleOv[k];
        this._overrideCacheOrder.length = 0;

        return true;
    }
}

// factory(engine, K) => api
function create(engine, K) {
    if (!engine) throw new Error("[MAT] engine is required");
    return new MaterialsRegistry(engine, K);
}

// META (adult contract)
create.META = {
    name: "materials",
    globalName: "MAT",
    version: "1.0.0",
    description: "Materials registry with JSON DB, caching, overrides and presets",
    engineMin: "0.1.0"
};

module.exports = create;