// FILE: resources/kalitech/builtin/materials.js
// Author: Calista Verner
"use strict";

module.exports = function createMaterialsBuiltin(K) {
    // K = globalThis.__kalitech
    const api = Object.create(null);

    let defs = null;
    const cacheMat = Object.create(null);    // name -> JME Material
    const cacheHandle = Object.create(null); // name -> MaterialHandle

    // путь можно переопределить из bootstrap config
    function dbPath() {
        return (K.config && K.config.materials && K.config.materials.dbPath)
            ? String(K.config.materials.dbPath)
            : "Scripts/core/materials/materials.json";
    }

    function loadDefs(engine) {
        if (defs) return defs;
        const json = engine.assets().readText(dbPath());
        defs = JSON.parse(json);
        return defs;
    }

    function cloneCfg(base) {
        return {
            def: base.def,
            params: Object.assign({}, base.params || {}),
            scales: base.scales ? Object.assign({}, base.scales) : undefined
        };
    }

    function applyOverrides(cfg, overrides) {
        if (!overrides) return;
        if (overrides.params) Object.assign(cfg.params, overrides.params);
        if (overrides.scales) cfg.scales = Object.assign(cfg.scales || {}, overrides.scales);
    }

    // --- public API ---

    /** Возвращает MaterialHandle (удобно для surface.setMaterial если он ждёт handle). */
    api.getHandle = function (name, overrides = null) {
        const engine = K._engine;
        if (!engine) throw new Error("[materials] engine not attached");

        const all = loadDefs(engine);
        const base = all[name];
        if (!base) throw new Error("[materials] unknown material: " + name);

        // кэшировать без overrides (иначе ключ должен включать overrides)
        if (!overrides && cacheHandle[name]) return cacheHandle[name];

        const cfg = cloneCfg(base);
        applyOverrides(cfg, overrides);

        const h = engine.material().create(cfg);
        if (!overrides) cacheHandle[name] = h;
        return h;
    };

    /** Возвращает JME Material (то, что у тебя уже работает). */
    api.getMaterial = function (name, overrides = null) {
        const engine = K._engine;
        if (!engine) throw new Error("[materials] engine not attached");

        const all = loadDefs(engine);
        const base = all[name];
        if (!base) throw new Error("[materials] unknown material: " + name);

        if (!overrides && cacheMat[name]) return cacheMat[name];

        const cfg = cloneCfg(base);
        applyOverrides(cfg, overrides);

        const h = engine.material().create(cfg);

        // unwrap handle -> material
        const mat = (h && typeof h.__material === "function") ? h.__material() : h;
        if (!overrides) cacheMat[name] = mat;
        return mat;
    };

    /** Сброс кэша/перечитывание json (для hot-reload). */
    api.reload = function () {
        defs = null;
        for (const k in cacheMat) delete cacheMat[k];
        for (const k in cacheHandle) delete cacheHandle[k];
        return true;
    };

    /** Список имен материалов */
    api.keys = function () {
        const engine = K._engine;
        if (!engine) return [];
        const all = loadDefs(engine);
        return Object.keys(all);
    };

    return api;
};