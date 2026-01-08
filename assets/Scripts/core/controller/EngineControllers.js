// FILE: Scripts/core/controller/EngineControllers.js
"use strict";

const {ControllerRegistry} = require("./ControllerRegistry.js");

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
        this._registries = new Map(); // name -> ControllerRegistry
    }

    controllers(name) {
        name = reqStr(name, "[EngineControllers] name is required");
        let r = this._registries.get(name);
        if (!r) {
            r = new ControllerRegistry(name);
            this._registries.set(name, r);
        }
        return r;
    }
}

/**
 * Идемпотентно цепляет контроллер-хаб к ctx/engine.
 * Выбираем одно место истины: ctx.engine.controllersHub
 */
function ensureControllersHub(ctx) {
    ctx = req(ctx, "[ensureControllersHub] ctx is required");
    const engine = ctx.engine || ctx;

    if (!engine.controllersHub) engine.controllersHub = new EngineControllers();
    return engine.controllersHub;
}

module.exports = {EngineControllers, ensureControllersHub};