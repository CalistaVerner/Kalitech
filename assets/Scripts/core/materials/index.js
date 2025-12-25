// FILE: Scripts/materials/index.js
"use strict";

const cache = Object.create(null);
let defs = null;

function loadDefs() {
    if (defs) return defs;
    defs = JSON.parse(engine.assets().readText("Scripts/core/materials/materials.json"));
    return defs;
}

function cloneCfg(base) {
    return {
        def: base.def,
        params: Object.assign({}, base.params || {}),
        scales: base.scales ? Object.assign({}, base.scales) : undefined
    };
}

/**
 * Возвращает JME Material (не handle).
 * @param {string} name
 * @param {{params?:Object, scales?:Object}=} overrides
 * @returns {any} com.jme3.material.Material (host object)
 */
exports.getMaterial = function (name, overrides = null) {
    // cache by name+overrides? пока без — чтобы не плодить материалы
    const all = loadDefs();
    const base = all[name];
    if (!base) throw new Error("[materials] unknown material: " + name);

    const cfg = cloneCfg(base);

    if (overrides) {
        if (overrides.params) Object.assign(cfg.params, overrides.params);
        if (overrides.scales) cfg.scales = Object.assign(cfg.scales || {}, overrides.scales);
    }

    const handle = engine.material().create(cfg);

    // unwrap to real JME Material
    if (handle && typeof handle.__material === "function") return handle.__material();
    if (handle && typeof handle.material === "function") return handle.material(); // на случай другого нейминга

    // fallback: если движок уже начал возвращать Material напрямую
    return handle;
};

/**
 * Старое поведение: возвращает handle (удобно для surface.setMaterial сейчас).
 */
exports.get = function (name, overrides = null) {
    const all = loadDefs();
    const base = all[name];
    if (!base) throw new Error("[materials] unknown material: " + name);

    const cfg = cloneCfg(base);

    if (overrides) {
        if (overrides.params) Object.assign(cfg.params, overrides.params);
        if (overrides.scales) cfg.scales = Object.assign(cfg.scales || {}, overrides.scales);
    }

    return engine.material().create(cfg);
};