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
    const {ControllerStack} = require("./helpers/ControllerStack");
    const {ControllerRegistry} = require("./helpers/ControllerRegistry");
    const {ensureControllersHub} = require("./helpers/EngineControllers");
    const {EntityController} = require("./helpers/EntityController");
    return {ControllerStack, ControllerRegistry, ensureControllersHub, EntityController};
}

function create(engine, K) {
    req(engine, "[Controllers] engine is required");
    req(K, "[Controllers] root state is required");

    const {ControllerStack, ControllerRegistry, ensureControllersHub, EntityController} = loadCore();
    const hub = ensureControllersHub(engine, K);

    function resolveRegistry(nameOrRegistry) {
        if (typeof nameOrRegistry === "string") {
            const name = reqStr(nameOrRegistry, "[Controllers] registry name is required");

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

    // self-install hot reload hook ONCE (no bootstrap edits required)
    function tryInstallHotReload(apiObj) {
        try {
            // 1) find a SystemContext-like object
            // Prefer K.ctx / K.context, otherwise any object attached by engine scripts.
            const ctx =
                (K && (K.ctx || K.context)) ||
                (engine && (engine.ctx || engine.context)) ||
                null;

            if (!ctx) return false;

            // 2) locate hotReload domain: ctx.hotReload() OR ctx.hotReload
            let hr = null;
            try {
                if (typeof ctx.hotReload === "function") hr = ctx.hotReload();
                else hr = ctx.hotReload;
            } catch (_) {
                hr = null;
            }
            if (!hr || typeof hr.register !== "function") return false;

            // 3) idempotent guard: store flag in ctx.stateDomain OR ctx.state OR ctx.state()
            // We never throw here: hot reload must be zero-crash.
            const FLAG = "__CTRL_HR_INSTALLED__";

            function getStateDomain() {
                try {
                    if (ctx.stateDomain && typeof ctx.stateDomain.get === "function") return ctx.stateDomain;
                } catch (_) {
                }
                try {
                    if (typeof ctx.state === "function") return ctx.state();
                } catch (_) {
                }
                try {
                    if (ctx.state && typeof ctx.state.get === "function") return ctx.state;
                } catch (_) {
                }
                return null;
            }

            const sd = getStateDomain();
            if (sd && typeof sd.get === "function" && sd.get(FLAG) === true) return true;

            hr.register(() => {
                try {
                    apiObj.resetAll();
                } catch (_) {
                }
            });

            if (sd && typeof sd.set === "function") sd.set(FLAG, true);

            return true;
        } catch (_) {
            return false;
        }
    }

    const api = Object.freeze({
        Registry: ControllerRegistry,

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

        registry(name) {
            return resolveRegistry(reqStr(name, "[Controllers] registry name is required"));
        },

        registerRegistry(registry) {
            registry = req(registry, "[Controllers] registry is required");
            if (!hub || typeof hub.set !== "function") {
                throw new Error("[Controllers] controllers hub has no set(registry)");
            }
            hub.set(registry);
            return registry;
        },

        register(name, fn) {
            name = reqStr(name, "[Controllers] name is required");
            fn = req(fn, "[Controllers] fn is required");
            if (typeof fn !== "function") throw new Error("[Controllers] register(name, fn): fn must be function");
            const r = resolveRegistry(name);
            fn(r);
            if (hub && typeof hub.set === "function") hub.set(r);
            return r;
        },

        // FULL WIPE one registry
        reset(name) {
            name = reqStr(name, "[Controllers] name is required");
            if (hub && typeof hub.reset === "function") return hub.reset(name);
            // fallback
            const r = resolveRegistry(name);
            if (r && typeof r.clear === "function") r.clear();
            return r;
        },

        // FULL WIPE all registries
        resetAll() {
            if (hub && typeof hub.resetAll === "function") hub.resetAll();
            return true;
        },

        stack(registryOrName, ctx, entity, cfg) {
            return buildStack(registryOrName, ctx, entity, cfg);
        },

        entity(registryOrName, ctx, entity, cfg) {
            const stack = buildStack(registryOrName, ctx, entity, cfg);
            return new EntityController(ctx, entity, stack);
        }
    });

    // install hook best-effort
    tryInstallHotReload(api);

    return api;
}

create.META = {
    moduleId: "controllers",
    globalName: "CTRL",
    version: "1.0.5",
    description: "ENGINE.controllers hub space + game-side registration + reset/resetAll + self-installed hot reload hook.",
    engineMin: "0.1.0"
};

module.exports = create;