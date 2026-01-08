// FILE: resources/kalitech/engine/bootstrap/Controllers.js
"use strict";

const {isPlainObj, isObj} = require("./Util.js");

function createControllersApi(K) {
    function ensureRegistry(name) {
        const k = String(name || "");
        if (!k) throw new Error("[CONTROLLERS] registry name is required");
        let r = K.controllers[k];
        if (!r) {
            r = {name: k, defs: Object.create(null)}; // id -> def
            K.controllers[k] = r;
        }
        return r;
    }

    function has(registryName, id) {
        const R = ensureRegistry(registryName);
        id = String(id || "");
        return !!(id && R.defs[id]);
    }

    function register(registryName, id, spec) {
        const R = ensureRegistry(registryName);
        id = String(id || "");
        if (!id) throw new Error("[CONTROLLERS] id is required");
        if (R.defs[id]) throw new Error("[CONTROLLERS] duplicate id: " + registryName + "::" + id);

        spec = isObj(spec) ? spec : Object.create(null);

        const def = {
            id,
            order: Number.isFinite(spec.order) ? spec.order : 0,
            deps: Array.isArray(spec.deps) ? spec.deps.slice() : [],
            enabled: (spec.enabled === false) ? false : true,
            when: (typeof spec.when === "function") ? spec.when : null,

            moduleId: spec.moduleId ? String(spec.moduleId) : "",
            exportName: spec.exportName ? String(spec.exportName) : "",
            Ctor: (typeof spec.Ctor === "function") ? spec.Ctor : null
        };

        R.defs[id] = def;
        return true;
    }

    function _resolveCtor(def) {
        if (def.Ctor) return def.Ctor;
        if (!def.moduleId) throw new Error("[CONTROLLERS] no Ctor/moduleId for: " + def.id);

        const exp = require(def.moduleId);
        const ctor = def.exportName ? exp[def.exportName] : exp;

        if (typeof ctor !== "function") {
            throw new Error("[CONTROLLERS] resolved ctor is not a function for: " + def.id +
                " moduleId=" + def.moduleId + " export=" + def.exportName);
        }

        def.Ctor = ctor;
        return ctor;
    }

    function build(registryName, ctx, entity, cfg) {
        const R = ensureRegistry(registryName);
        const defsArr = Object.keys(R.defs).map(k => R.defs[k]);

        const active = [];
        const activeSet = Object.create(null);

        for (let i = 0; i < defsArr.length; i++) {
            const d = defsArr[i];
            if (!d.enabled) continue;
            if (d.when && d.when(ctx, entity, cfg) !== true) continue;
            active.push(d);
            activeSet[d.id] = d;
        }

        for (let i = 0; i < active.length; i++) {
            const d = active[i];
            for (let j = 0; j < d.deps.length; j++) {
                const dep = d.deps[j];
                if (!R.defs[dep]) throw new Error("[CONTROLLERS] unknown dep: " + d.id + " -> " + dep);
                if (!activeSet[dep]) throw new Error("[CONTROLLERS] dep disabled: " + d.id + " -> " + dep);
            }
        }

        const indeg = Object.create(null);
        const edges = Object.create(null);

        for (let i = 0; i < active.length; i++) {
            const d = active[i];
            indeg[d.id] = 0;
            edges[d.id] = [];
        }
        for (let i = 0; i < active.length; i++) {
            const d = active[i];
            for (let j = 0; j < d.deps.length; j++) {
                const dep = d.deps[j];
                edges[dep].push(d.id);
                indeg[d.id] = (indeg[d.id] | 0) + 1;
            }
        }

        const q = [];
        for (let i = 0; i < active.length; i++) if (indeg[active[i].id] === 0) q.push(active[i]);
        q.sort((a, b) => (a.order - b.order) || (a.id < b.id ? -1 : (a.id > b.id ? 1 : 0)));

        const ordered = [];
        while (q.length) {
            const d = q.shift();
            ordered.push(d);

            const out = edges[d.id];
            for (let i = 0; i < out.length; i++) {
                const to = out[i];
                indeg[to]--;
                if (indeg[to] === 0) {
                    q.push(activeSet[to]);
                    q.sort((a, b) => (a.order - b.order) || (a.id < b.id ? -1 : (a.id > b.id ? 1 : 0)));
                }
            }
        }

        if (ordered.length !== active.length) {
            const stuck = [];
            for (let i = 0; i < active.length; i++) if (indeg[active[i].id] > 0) stuck.push(active[i].id);
            throw new Error("[CONTROLLERS] dependency cycle: " + stuck.join(" -> "));
        }

        const list = new Array(ordered.length);
        const ids = new Array(ordered.length);

        for (let i = 0; i < ordered.length; i++) {
            const d = ordered[i];
            const Ctor = _resolveCtor(d);
            ids[i] = d.id;
            list[i] = new Ctor(cfg || null);
        }

        return {list, ids};
    }

    return {ensureRegistry, has, register, build};
}

function loadRegistrators(engine, K, controllersCfg, CONTROLLERS) {
    const cfg = isPlainObj(controllersCfg) ? controllersCfg : Object.create(null);
    const regs = Array.isArray(cfg.registrators) ? cfg.registrators : [];

    for (let i = 0; i < regs.length; i++) {
        const mid = String(regs[i] || "");
        if (!mid) continue;
        if (K.controllersRegs.indexOf(mid) >= 0) continue;

        const regExp = require(mid);
        if (typeof regExp !== "function") {
            throw new Error("[CONTROLLERS] registrator must export function(engine,K,CONTROLLERS,cfg): " + mid);
        }

        regExp(engine, K, CONTROLLERS, K.config);
        K.controllersRegs.push(mid);
    }
}

module.exports = {createControllersApi, loadRegistrators};