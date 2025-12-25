// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

const DEFAULT_CONFIG = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials"
    },
    packageStyle: { enabled: false, roots: {} },
    materials: { dbPath: "Scripts/core/materials/materials.json" },
    builtins: {
        modules: {
            assert: "@builtin/assert",
            deepMerge: "@builtin/deepMerge",
            schema: "@builtin/schema",
            paths: "@builtin/paths",
            math: "@builtin/math",
            editorPreset: "@builtin/editorPreset",
            materials: "@builtin/MaterialsRegistry"
        },
        exposeGlobals: true,
        globalAliases: { M: "materials" }
    }
};

const ROOT_KEY = "__kalitech";
if (!globalThis[ROOT_KEY]) globalThis[ROOT_KEY] = Object.create(null);
const K = globalThis[ROOT_KEY];

function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }
function clamp(x, a, b) { x = +x; return x < a ? a : (x > b ? b : x); }
function isObjectLike(x) { const t = typeof x; return x != null && (t === "object" || t === "function"); }

function ensureRootState(root) {
    if (!root.builtins) root.builtins = Object.create(null);
    if (!root._deferred) root._deferred = [];
    if (!root._onceKeys) root._onceKeys = Object.create(null);
    if (!root._engineAttached) root._engineAttached = false;
    if (!root._engine) root._engine = null;
    return root;
}

function assignIfMissing(dst, src) {
    for (const k in src) {
        if (!(k in dst)) dst[k] = src[k];
    }
    return dst;
}

function deepMergePlain(dst, src) {
    if (!src || typeof src !== "object") return dst;
    if (!dst || typeof dst !== "object") dst = {};
    for (const k of Object.keys(src)) {
        const sv = src[k];
        const dv = dst[k];
        if (sv && typeof sv === "object" && !Array.isArray(sv)) {
            dst[k] = deepMergePlain(dv && typeof dv === "object" && !Array.isArray(dv) ? dv : {}, sv);
        } else {
            dst[k] = sv;
        }
    }
    return dst;
}

class ConfigStore {
    constructor(root, defaults) {
        this.root = root;
        this.defaults = defaults;
        this._cfg = null;
    }

    get() {
        if (this._cfg) return this._cfg;

        const fromUser = this._tryLoadJson();
        const merged = deepMergePlain(deepMergePlain({}, this.defaults), fromUser || {});

        this.root.config = merged;
        this._cfg = merged;
        return merged;
    }

    _tryLoadJson() {
        try {
            if (!this.root._engineAttached || !this.root._engine) return null;
            const assets = this.root._engine.assets && this.root._engine.assets();
            if (!assets || typeof assets.readText !== "function") return null;
            const txt = assets.readText("kalitech.config.json");
            if (!txt) return null;
            return JSON.parse(txt);
        } catch (_) {
            return null;
        }
    }
}

class GlobalScope {
    constructor(root) {
        this.root = root;
    }

    set(name, value, overwrite) {
        if (!isObjectLike(value)) return value;
        if (!overwrite && globalThis[name]) return value;
        globalThis[name] = value;
        return value;
    }

    alias(aliasName, targetName) {
        if (globalThis[aliasName]) return false;
        const t = globalThis[targetName] || this.root.builtins[targetName];
        if (!t) return false;
        globalThis[aliasName] = t;
        return true;
    }
}

class BuiltinRegistry {
    constructor(root, globals, getConfig) {
        this.root = root;
        this.globals = globals;
        this.getConfig = getConfig;
    }

    loadAll() {
        const cfg = this.getConfig();
        const mods = (cfg.builtins && cfg.builtins.modules) ? cfg.builtins.modules : {};
        for (const name in mods) this.load(name, mods[name]);
        this._captureFactories();
        this._applyAliases();
    }

    load(name, moduleId) {
        const n = String(name || "").trim();
        if (!n) return null;
        if (this.root.builtins[n]) return this.root.builtins[n];

        let exp = null;
        try { exp = require(moduleId); } catch (_) { exp = null; }
        if (exp != null) this.root.builtins[n] = exp;

        const cfg = this.getConfig();
        if (cfg.builtins && cfg.builtins.exposeGlobals) this.globals.set(n, exp, false);
        return exp;
    }

    _captureFactories() {
        const maybeFactory = this.root.builtins.materials;
        if (typeof this.root.builtins.materialsFactory !== "function" && typeof maybeFactory === "function") {
            this.root.builtins.materialsFactory = maybeFactory;
        }
    }

    _applyAliases() {
        const cfg = this.getConfig();
        const aliases = (cfg.builtins && cfg.builtins.globalAliases) ? cfg.builtins.globalAliases : {};
        for (const a in aliases) this.globals.alias(a, aliases[a]);
    }
}

class Lifecycle {
    constructor(root) {
        this.root = root;
    }

    whenEngine(fn) {
        if (this.root._engineAttached && this.root._engine) {
            try { fn(this.root._engine); } catch (_) {}
            return true;
        }
        this.root._deferred.push(fn);
        return false;
    }

    whenEngineOnce(key, fn) {
        const k = String(key || "");
        if (!k) return this.whenEngine(fn);
        if (this.root._onceKeys[k]) return false;
        this.root._onceKeys[k] = true;
        return this.whenEngine(fn);
    }

    drain(engine) {
        const q = this.root._deferred;
        this.root._deferred = [];
        for (let i = 0; i < q.length; i++) {
            try { q[i](engine); } catch (_) {}
        }
    }
}

class KalitechBootstrap {
    constructor(root, defaults) {
        this.K = ensureRootState(root);
        this.globals = new GlobalScope(this.K);
        this.lifecycle = new Lifecycle(this.K);
        this.configStore = new ConfigStore(this.K, defaults);

        this.registry = new BuiltinRegistry(
            this.K,
            this.globals,
            () => this.configStore.get()
        );

        const cfg = this.configStore.get();
        assignIfMissing(this.K, { config: cfg });
    }

    get config() {
        return this.configStore.get();
    }

    init() {
        this.K.builtins.safeJson = safeJson;
        this.K.builtins.clamp = clamp;

        const cfg = this.config;
        if (cfg.builtins && cfg.builtins.exposeGlobals) {
            this.globals.set("safeJson", safeJson, false);
            this.globals.set("clamp", clamp, false);
        }

        this.registry.loadAll();

        this.lifecycle.whenEngineOnce("builtin:engine-wiring", (engine) => {
            this._wireEngine(engine);
        });

        return this;
    }

    _wireEngine(engine) {
        const cfg = this.config;

        const log = engine && engine.log ? engine.log() : null;
        const events = engine && engine.events ? engine.events() : null;

        this.K.builtins.engine = engine;
        this.K.builtins.events = events;

        if (cfg.builtins && cfg.builtins.exposeGlobals) {
            this.globals.set("engine", engine, false);
            this.globals.set("events", events, false);
            this.globals.set("eventsUtil", events, false);
        }

        this.globals.alias("M", "materials");

        if (log && log.info) log.info("[builtin/bootstrap] engine attached");

        if (events && typeof events.once === "function") {
            events.once("world:ready", (payload) => {
                if (log && log.info) log.info("[builtin] world:ready payload=" + safeJson(payload));
            });
        } else {
            if (log && log.debug) log.debug("[builtin] events.once not available");
        }
    }

    attachEngine(engine) {
        if (!engine) return false;
        if (this.K._engineAttached && this.K._engine === engine) return true;

        this.K._engine = engine;
        this.K._engineAttached = true;

        const cfg = this.config;

        if (cfg.builtins && cfg.builtins.exposeGlobals) {
            this.globals.set("engine", engine, false);
        }

        let factory = this.K.builtins.materialsFactory;
        if (typeof factory !== "function" && typeof this.K.builtins.materials === "function") {
            factory = this.K.builtins.materials;
            this.K.builtins.materialsFactory = factory;
        }

        if (typeof factory === "function") {
            const api = factory(this.K);
            this.K.builtins.materials = api;

            if (cfg.builtins && cfg.builtins.exposeGlobals) {
                this.globals.set("materials", api, true);
                this.globals.set("M", api, true);
            }
        }

        this.lifecycle.drain(engine);
        return true;
    }

    detachEngine() {
        this.K._engine = null;
        this.K._engineAttached = false;
        return true;
    }

    whenEngine(fn) { return this.lifecycle.whenEngine(fn); }
    whenEngineOnce(key, fn) { return this.lifecycle.whenEngineOnce(key, fn); }
}

const boot = new KalitechBootstrap(K, DEFAULT_CONFIG).init();

module.exports = {
    config: boot.config,
    safeJson,
    clamp,
    whenEngine: boot.whenEngine.bind(boot),
    whenEngineOnce: boot.whenEngineOnce.bind(boot),
    attachEngine: boot.attachEngine.bind(boot),
    detachEngine: boot.detachEngine.bind(boot)
};