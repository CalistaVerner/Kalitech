// FILE: @builtin/modules/Controllers/helpers/ControllerRegistry.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqStr(s, msg) {
    if (typeof s !== "string" || !s) throw new Error(msg);
    return s;
}

function reqFn(fn, msg) {
    if (typeof fn !== "function") throw new Error(msg);
    return fn;
}

function safeBool(v, fb) {
    return (v === true) ? true : (v === false ? false : fb);
}

function safeNum(v, fb) {
    return Number.isFinite(v) ? v : fb;
}

class ControllerRegistry {
    constructor(name) {
        this.name = name || "registry";
        this._defs = new Map(); // id -> def
    }

    // FULL WIPE registry (hot reload friendly)
    clear() {
        this._defs.clear();
        return this;
    }

    register(id, Ctor, opts) {
        id = reqStr(id, `[Registry:${this.name}] id is required`);
        Ctor = reqFn(Ctor, `[Registry:${this.name}] Ctor is required`);

        opts = opts || {};
        const def = Object.freeze({
            id,
            Ctor,
            order: safeNum(opts.order, 0),
            deps: Array.isArray(opts.deps) ? opts.deps.slice() : [],
            enabled: safeBool(opts.enabled, true),
            when: (typeof opts.when === "function") ? opts.when : null
        });

        // HOT-RELOAD SAFE:
        // - previously: throw on duplicate
        // - now: overwrite (last wins)
        this._defs.set(id, def);
        return this;
    }

    registerPack(packFn, packCfg) {
        packFn = reqFn(packFn, `[Registry:${this.name}] packFn(registry, cfg) is required`);
        packFn(this, packCfg || null);
        return this;
    }

    build(ctx, entity, cfg) {
        ctx = req(ctx, `[Registry:${this.name}] ctx is required`);
        entity = req(entity, `[Registry:${this.name}] entity is required`);

        const active = [];
        for (const def of this._defs.values()) {
            if (!def.enabled) continue;
            if (def.when && def.when(ctx, entity, cfg) !== true) continue;
            active.push(def);
        }

        const activeSet = new Map();
        for (let i = 0; i < active.length; i++) activeSet.set(active[i].id, active[i]);

        for (const def of active) {
            for (let i = 0; i < def.deps.length; i++) {
                const dep = def.deps[i];
                if (!this._defs.has(dep)) throw new Error(`[Registry:${this.name}] '${def.id}' depends on unknown '${dep}'`);
                if (!activeSet.has(dep)) throw new Error(`[Registry:${this.name}] '${def.id}' depends on disabled '${dep}'`);
            }
        }

        const indeg = new Map();
        const edges = new Map();
        for (const def of active) {
            indeg.set(def.id, 0);
            edges.set(def.id, []);
        }
        for (const def of active) {
            for (let i = 0; i < def.deps.length; i++) {
                const dep = def.deps[i];
                edges.get(dep).push(def.id);
                indeg.set(def.id, indeg.get(def.id) + 1);
            }
        }

        const queue = [];
        for (const def of active) if (indeg.get(def.id) === 0) queue.push(def);
        queue.sort((a, b) => (a.order - b.order) || (a.id < b.id ? -1 : (a.id > b.id ? 1 : 0)));

        const orderedDefs = [];
        while (queue.length) {
            const def = queue.shift();
            orderedDefs.push(def);

            const out = edges.get(def.id);
            for (let i = 0; i < out.length; i++) {
                const to = out[i];
                indeg.set(to, indeg.get(to) - 1);
                if (indeg.get(to) === 0) {
                    queue.push(activeSet.get(to));
                    queue.sort((a, b) => (a.order - b.order) || (a.id < b.id ? -1 : (a.id > b.id ? 1 : 0)));
                }
            }
        }

        if (orderedDefs.length !== active.length) {
            const stuck = [];
            for (const def of active) if (indeg.get(def.id) > 0) stuck.push(def.id);
            throw new Error(`[Registry:${this.name}] dependency cycle: ` + stuck.join(" -> "));
        }

        const list = new Array(orderedDefs.length);
        const ids = new Array(orderedDefs.length);

        for (let i = 0; i < orderedDefs.length; i++) {
            const def = orderedDefs[i];
            ids[i] = def.id;
            list[i] = new def.Ctor(cfg || null);
        }

        return {list, ids};
    }
}

module.exports = {ControllerRegistry};