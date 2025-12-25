// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

const config = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials"
    },
    packageStyle: {enabled: false, roots: {}},
    materials: {dbPath: "Scripts/core/materials/materials.json"},
    builtins: {
        modules: {
            assert: "@builtin/assert",
            deepMerge: "@builtin/deepMerge",
            schema: "@builtin/schema",
            paths: "@builtin/paths",
            math: "@builtin/math",
            editorPreset: "@builtin/editorPreset",
            materials: "@builtin/materials"
        }, exposeGlobals: true, globalAliases: {M: "materials"}
    }
};

if (!globalThis.__kalitech) globalThis.__kalitech = Object.create(null);
const K = globalThis.__kalitech;

K.config = config;
if (!K.builtins) K.builtins = Object.create(null);
if (!K._deferred) K._deferred = [];
if (!K._onceKeys) K._onceKeys = Object.create(null);
if (!K._engineAttached) K._engineAttached = false;
if (!K._engine) K._engine = null;

function safeJson(v) {
    try {
        return JSON.stringify(v);
    } catch (_) {
        return String(v);
    }
}

function clamp(x, a, b) {
    x = +x;
    return x < a ? a : (x > b ? b : x);
}

function isObjectLike(x) {
    const t = typeof x;
    return x != null && (t === "object" || t === "function");
}

class KalitechBootstrap {
    constructor(root) {
        this.K = root;
        this.config = root.config;
    }

    expose(name, value, {overwrite = false} = {}) {
        if (!this.config.builtins || !this.config.builtins.exposeGlobals) return value;
        if (!isObjectLike(value)) return value;
        if (!overwrite && globalThis[name]) return value;
        globalThis[name] = value;
        return value;
    }

    register(name, moduleId) {
        const n = String(name || "").trim();
        if (!n) return null;
        if (this.K.builtins[n]) return this.K.builtins[n];

        let exp = null;
        try {
            exp = require(moduleId);
        } catch (_) {
            exp = null;
        }
        if (exp != null) this.K.builtins[n] = exp;

        this.expose(n, exp, {overwrite: false});
        return exp;
    }

    alias(aliasName, targetName) {
        const a = String(aliasName || "").trim();
        const t = String(targetName || "").trim();
        if (!a || !t) return false;
        if (globalThis[a]) return false;

        const target = globalThis[t] || this.K.builtins[t];
        if (!target) return false;

        globalThis[a] = target;
        return true;
    }

    whenEngine(fn) {
        if (this.K._engineAttached && this.K._engine) {
            try {
                fn(this.K._engine);
            } catch (_) {
            }
            return true;
        }
        this.K._deferred.push(fn);
        return false;
    }

    whenEngineOnce(key, fn) {
        const k = String(key || "");
        if (!k) return this.whenEngine(fn);
        if (this.K._onceKeys[k]) return false;
        this.K._onceKeys[k] = true;
        return this.whenEngine(fn);
    }

    attachEngine(engine) {
        if (!engine) return false;
        if (this.K._engineAttached && this.K._engine === engine) return true;

        this.K._engine = engine;
        this.K._engineAttached = true;

        this.expose("engine", engine, {overwrite: false});

        let factory = this.K.builtins.materialsFactory;

        if (typeof factory !== "function" && typeof this.K.builtins.materials === "function") {
            factory = this.K.builtins.materials;
            this.K.builtins.materialsFactory = factory;
        }

        if (typeof factory === "function") {
            const api = factory(this.K);
            this.K.builtins.materials = api;
            this.expose("materials", api, {overwrite: true});
            this.expose("M", api, {overwrite: true});
        }

        const q = this.K._deferred;
        this.K._deferred = [];
        for (let i = 0; i < q.length; i++) {
            try {
                q[i](engine);
            } catch (_) {
            }
        }
        return true;
    }

    detachEngine() {
        this.K._engine = null;
        this.K._engineAttached = false;
        return true;
    }
}

const boot = new KalitechBootstrap(K);

K.builtins.safeJson = safeJson;
K.builtins.clamp = clamp;
boot.expose("safeJson", safeJson);
boot.expose("clamp", clamp);

const mods = (config.builtins && config.builtins.modules) ? config.builtins.modules : {};
for (const name in mods) {
    const exp = boot.register(name, mods[name]);
    if (name === "materials" && typeof exp === "function" && !K.builtins.materialsFactory) {
        K.builtins.materialsFactory = exp;
    }
}

const aliases = (config.builtins && config.builtins.globalAliases) ? config.builtins.globalAliases : {};
for (const a in aliases) boot.alias(a, aliases[a]);

boot.whenEngineOnce("builtin:engine-wiring", (engine) => {
    const log = engine && engine.log ? engine.log() : null;
    const events = engine && engine.events ? engine.events() : null;

    K.builtins.engine = engine;
    K.builtins.events = events;

    boot.expose("events", events, {overwrite: false});
    boot.expose("eventsUtil", events, {overwrite: false});
    boot.alias("M", "materials");

    if (log && log.info) log.info("[builtin/bootstrap] engine attached");

    if (events && typeof events.once === "function") {
        events.once("world:ready", (payload) => {
            if (log && log.info) log.info("[builtin] world:ready payload=" + safeJson(payload));
        });
    } else {
        if (log && log.debug) log.debug("[builtin] events.once not available");
    }
});

module.exports = {
    config,
    safeJson,
    clamp,
    whenEngine: boot.whenEngine.bind(boot),
    whenEngineOnce: boot.whenEngineOnce.bind(boot),
    attachEngine: boot.attachEngine.bind(boot),
    detachEngine: boot.detachEngine.bind(boot)
};