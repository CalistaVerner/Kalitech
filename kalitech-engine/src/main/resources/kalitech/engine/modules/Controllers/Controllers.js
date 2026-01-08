// FILE: resources/kalitech/engine/modules/Controllers/Controllers.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function reqStr(s, msg) {
    if (typeof s !== "string" || !s) throw new Error(msg);
    return s;
}

function loadCore() {
    const {ControllerStack} = require("@module/Controllers/ControllerStack");
    const {ControllerRegistry} = require("@module/Controllers/ControllerRegistry");
    const {ensureControllersHub} = require("@module/Controllers/EngineControllers");
    const {EntityController} = require("@module/Controllers/EntityController");
    return {ControllerStack, ControllerRegistry, ensureControllersHub, EntityController};
}

function create(engine, K) {
    req(engine, "[Controllers] engine is required");
    req(K, "[Controllers] root state is required");

    const {ControllerStack, ControllerRegistry, ensureControllersHub, EntityController} = loadCore();
    const hub = ensureControllersHub(engine, K);

    // SPACE-FIRST:
    // if registry name requested but not registered yet -> create empty slot (space)
    function resolveRegistry(nameOrRegistry) {
        if (typeof nameOrRegistry === "string") {
            const name = reqStr(nameOrRegistry, "[Controllers] registry name is required");

            // hub.get/hub.set must exist (your hub already uses them in loadRegistrators flow)
            let r = (hub && typeof hub.get === "function") ? hub.get(name) : null;
            if (!r) {
                r = new ControllerRegistry(name);
                if (hub && typeof hub.set === "function") hub.set(r);
            }
            return r;
        }
        return req(nameOrRegistry, "[Controllers] registry is required");
    }

    function buildStack(registryOrName, ctx, entity, cfg) {
        const registry = resolveRegistry(registryOrName);
        ctx = req(ctx, "[Controllers] ctx is required");
        entity = req(entity, "[Controllers] entity is required");

        if (typeof registry.build !== "function") {
            throw new Error("[Controllers] registry.build(ctx,entity,cfg) is required");
        }

        const built = registry.build(ctx, entity, cfg || null);
        const stack = new ControllerStack(built.list, built.ids);
        if (typeof stack.bind === "function") stack.bind(ctx, entity);
        return stack;
    }

    function absorbRegistratorExport(exp, id) {
        // 1) function (hub, engine, K)
        // 2) { registries: [ControllerRegistry, ...] }
        // 3) { createXRegistry(): ControllerRegistry }
        if (typeof exp === "function") {
            exp(hub, engine, K);
            return;
        }

        if (exp && Array.isArray(exp.registries)) {
            for (let i = 0; i < exp.registries.length; i++) hub.set(exp.registries[i]);
            return;
        }

        let ok = false;
        if (exp && typeof exp === "object") {
            for (const k of Object.keys(exp)) {
                if (k.indexOf("create") !== 0 || k.lastIndexOf("Registry") !== k.length - "Registry".length) continue;
                const fn = exp[k];
                if (typeof fn !== "function") continue;
                const r = fn();
                hub.set(r);
                ok = true;
            }
        }

        if (!ok) throw new Error("[Controllers] invalid registrator export: " + reqStr(id, "id"));
    }

    const api = Object.freeze({
        Registry: ControllerRegistry,

        // legacy / optional bootstrap path (can exist, but game doesn't depend on it)
        loadRegistrators(list) {
            if (!Array.isArray(list)) return false;
            for (let i = 0; i < list.length; i++) {
                const id = String(list[i] || "");
                if (!id) continue;
                const exp = require(id);
                absorbRegistratorExport(exp, id);
            }
            return true;
        },

        // SPACE: return slot (creates if missing)
        registry(name) {
            return resolveRegistry(reqStr(name, "[Controllers] registry name is required"));
        },

        // GAME-SIDE REGISTRATION: register ready registry instance
        registerRegistry(registry) {
            registry = req(registry, "[Controllers] registry is required");
            if (!hub || typeof hub.set !== "function") {
                throw new Error("[Controllers] controllers hub has no set(registry)");
            }
            hub.set(registry);
            return registry;
        },

        // GAME-SIDE REGISTRATION: fill slot via function
        register(name, fn) {
            name = reqStr(name, "[Controllers] name is required");
            fn = req(fn, "[Controllers] fn is required");
            if (typeof fn !== "function") throw new Error("[Controllers] register(name, fn): fn must be function");
            const r = resolveRegistry(name);
            fn(r);
            if (hub && typeof hub.set === "function") hub.set(r);
            return r;
        },

        stack(registryOrName, ctx, entity, cfg) {
            return buildStack(registryOrName, ctx, entity, cfg);
        },

        entity(registryOrName, ctx, entity, cfg) {
            const stack = buildStack(registryOrName, ctx, entity, cfg);
            return new EntityController(ctx, entity, stack);
        }
    });

    return api;
}

create.META = {
    moduleId: "controllers",
    globalName: "CTRL",
    version: "1.0.3",
    description: "ENGINE.controllers hub space + game-side registration (registerRegistry/register).",
    engineMin: "0.1.0"
};

module.exports = create;