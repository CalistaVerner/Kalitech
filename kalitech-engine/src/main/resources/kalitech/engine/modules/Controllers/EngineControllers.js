// FILE: @module/Controllers/EngineControllers.js
"use strict";

const {ControllerRegistry} = require("@module/Controllers/ControllerRegistry");

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
            throw new Error("[EngineControllers] registry '" + name + "' already exists");
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