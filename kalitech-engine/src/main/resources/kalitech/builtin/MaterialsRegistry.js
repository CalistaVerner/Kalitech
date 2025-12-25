// FILE: resources/kalitech/builtin/MaterialsRegistry.js
// Author: Calista Verner
"use strict";

class MaterialsRegistry {
    constructor(K) {
        this.K = K;
        this.defs = null;
        this.cacheMat = Object.create(null);
        this.cacheHandle = Object.create(null);
    }

    engine() {
        const e = this.K && this.K._engine;
        if (!e) throw new Error("[materials] engine not attached");
        return e;
    }

    dbPath() {
        const c = this.K && this.K.config && this.K.config.materials;
        return (c && c.dbPath) ? String(c.dbPath) : "Scripts/core/materials/materials.json";
    }

    loadDefs() {
        if (this.defs) return this.defs;
        const json = this.engine().assets().readText(this.dbPath());
        this.defs = JSON.parse(json);
        return this.defs;
    }

    base(name) {
        const n = String(name || "");
        const all = this.loadDefs();
        const b = all[n];
        if (!b) throw new Error("[materials] unknown material: " + n);
        return b;
    }

    cloneCfg(base) {
        return {
            def: base.def,
            params: Object.assign({}, base.params || {}),
            scales: base.scales ? Object.assign({}, base.scales) : undefined
        };
    }

    applyOverrides(cfg, overrides) {
        if (!overrides) return;
        if (overrides.params) Object.assign(cfg.params, overrides.params);
        if (overrides.scales) cfg.scales = Object.assign(cfg.scales || {}, overrides.scales);
    }

    isPlainJsObject(x) {
        if (!x || typeof x !== "object") return false;
        const p = Object.getPrototypeOf(x);
        return p === Object.prototype || p === null;
    }

    unwrapMaterial(v) {
        let m = v;

        try {
            if (m && typeof m.__material === "function") m = m.__material();
        } catch (_) {}

        if (!m) throw new Error("[materials] expected Material, got: " + String(m));

        if (this.isPlainJsObject(m)) {
            throw new Error("[materials] expected host Material, got plain JS object");
        }

        return m;
    }

    getHandle(name, overrides = null) {
        if (!overrides && this.cacheHandle[name]) return this.cacheHandle[name];

        const cfg = this.cloneCfg(this.base(name));
        this.applyOverrides(cfg, overrides);

        const h = this.engine().material().create(cfg);
        if (!overrides) this.cacheHandle[name] = h;
        return h;
    }

    getMaterial(name, overrides = null) {
        if (!overrides && this.cacheMat[name]) return this.cacheMat[name];

        const cfg = this.cloneCfg(this.base(name));
        this.applyOverrides(cfg, overrides);

        const created = this.engine().material().create(cfg);
        const mat = this.unwrapMaterial(created);

        if (!overrides) this.cacheMat[name] = mat;
        return mat;
    }

    reload() {
        this.defs = null;
        for (const k in this.cacheMat) delete this.cacheMat[k];
        for (const k in this.cacheHandle) delete this.cacheHandle[k];
        return true;
    }

    keys() {
        try {
            return Object.keys(this.loadDefs());
        } catch (_) {
            return [];
        }
    }
}

module.exports = function createMaterialsBuiltin(K) {
    return new MaterialsRegistry(K);
};