// FILE: @module/Controllers/EngineControllers.js
"use strict";

const {ControllerRegistry} = require("./ControllerRegistry");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqStr(s, msg) {
    if (typeof s !== "string" || !s) throw new Error(msg);
    return s;
}

class EngineControllers {
    constructor() {
        this._registries = Object.create(null); // name -> ControllerRegistry
    }

    // read-only
    get(name) {
        name = reqStr(name, "[EngineControllers] name is required");
        return this._registries[name] || null;
    }

    // explicit registration (game-side)
    set(registry) {
        registry = req(registry, "[EngineControllers] registry is required");

        const name = reqStr(registry.name, "[EngineControllers] registry.name is required");
        const existing = this._registries[name];

        // idempotent: allow setting same instance
        if (existing && existing !== registry) {
            // CDPR-style hot reload friendliness: replace on purpose
            // (old one will be GC'ed, avoids stale defs / duplicates)
            this._registries[name] = registry;
            return registry;
        }

        this._registries[name] = registry;
        return registry;
    }

    // space slot: create if missing (engine provides space)
    controllers(name) {
        name = reqStr(name, "[EngineControllers] name is required");
        let r = this._registries[name];
        if (!r) {
            r = new ControllerRegistry(name);
            this._registries[name] = r;
        }
        return r;
    }

    // FULL WIPE one registry
    reset(name) {
        name = reqStr(name, "[EngineControllers] name is required");
        const r = this._registries[name];
        if (!r) return null;

        // Prefer clear() if provided
        if (typeof r.clear === "function") {
            try {
                r.clear();
            } catch (_) {
            }
            return r;
        }

        // Fallback: replace with new instance
        const nr = new ControllerRegistry(name);
        this._registries[name] = nr;
        return nr;
    }

    // FULL WIPE all registries
    resetAll() {
        const keys = Object.keys(this._registries);
        for (let i = 0; i < keys.length; i++) {
            const k = keys[i];
            const r = this._registries[k];
            if (!r) continue;

            if (typeof r.clear === "function") {
                try {
                    r.clear();
                } catch (_) {
                }
            } else {
                this._registries[k] = new ControllerRegistry(k);
            }
        }
        return true;
    }
}

// FIX: store hub in K (JS object), not in engine (HostObject)
function ensureControllersHub(engine, K) {
    req(engine, "[ensureControllersHub] engine is required");
    K = req(K, "[ensureControllersHub] K is required");

    if (!K.services) K.services = Object.create(null);
    if (!K.services.controllersHub) K.services.controllersHub = new EngineControllers();
    return K.services.controllersHub;
}

module.exports = {EngineControllers, ensureControllersHub};