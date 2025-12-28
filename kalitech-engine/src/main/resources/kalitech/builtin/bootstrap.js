// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

/**
 * Bootstrap goals:
 *  - KEEP require-resolver config: aliases/packageStyle/materials.dbPath.
 *  - No globalAliases.
 *  - Module contract: export function create(engine, K) => api
 *  - Module meta: create.META = { name, globalName, version, description, engineMin }
 *  - Globals exist early via deferred proxies (MSH/MAT/SND).
 *  - On attachEngine: instantiate all modules and replace proxies with real APIs.
 *  - IMPORTANT: never mutate API objects (they may be Object.freeze()).
 */

const DEFAULT_CONFIG = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials",
        "@env": "Scripts/environment"
    },
    packageStyle: { enabled: false, roots: {} },
    materials: { dbPath: "data/materials.json" },

    builtins: {
        exposeGlobals: true,
        modules: {
            materials: "@builtin/Material/Material",
            primitives: "@builtin/Primitives/Primitives",
            sound: "@builtin/Sound/Sound"
        }
    }
};

// root
const ROOT_KEY = "__kalitech";
if (!globalThis[ROOT_KEY]) globalThis[ROOT_KEY] = Object.create(null);
const K = globalThis[ROOT_KEY];

function ensureRootState(root) {
    if (!root.modules) root.modules = Object.create(null);         // raw exports
    if (!root.instances) root.instances = Object.create(null);     // instantiated apis
    if (!root.meta) root.meta = Object.create(null);               // meta by name
    if (!root.instancesMeta) root.instancesMeta = Object.create(null); // meta for instances
    if (!root._engine) root._engine = null;
    if (!root._engineAttached) root._engineAttached = false;
    if (!root._deferred) root._deferred = [];
    if (!root._once) root._once = Object.create(null);
    if (!root.config) root.config = Object.create(null);
    return root;
}
ensureRootState(K);

// utils
function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }

function deepMergePlain(dst, src) {
    if (!src || typeof src !== "object") return dst;
    if (!dst || typeof dst !== "object") dst = {};
    for (const k of Object.keys(src)) {
        const sv = src[k];
        const dv = dst[k];
        if (sv && typeof sv === "object" && !Array.isArray(sv)) dst[k] = deepMergePlain(dv, sv);
        else dst[k] = sv;
    }
    return dst;
}

function parseSemver(v) {
    if (!v || typeof v !== "string") return null;
    const m = v.trim().match(/^(\d+)\.(\d+)\.(\d+)/);
    if (!m) return null;
    return [m[1] | 0, m[2] | 0, m[3] | 0];
}
function semverGte(a, b) {
    const A = parseSemver(String(a || "")); const B = parseSemver(String(b || ""));
    if (!A || !B) return true; // can't parse => don't block
    if (A[0] !== B[0]) return A[0] > B[0];
    if (A[1] !== B[1]) return A[1] > B[1];
    return A[2] >= B[2];
}
function readEngineVersion(engine) {
    try {
        if (!engine) return null;
        if (typeof engine.version === "function") return String(engine.version());
        if (typeof engine.version === "string") return engine.version;
        if (engine.info && typeof engine.info === "function") {
            const info = engine.info();
            if (info && info.version) return String(info.version);
        }
    } catch (_) {}
    return null;
}

// deferred proxy to prevent "x is not a function" before attachEngine
function createDeferredProxy(resolveFn, label) {
    const state = { resolved: null };

    function ensureResolved() {
        if (state.resolved) return state.resolved;
        const api = resolveFn();
        if (api) state.resolved = api;
        return state.resolved;
    }

    function makeChain(steps) {
        return new Proxy(function () {}, {
            get(_t, prop) {
                if (prop === "__isDeferred") return true;
                if (prop === "__label") return label;
                if (prop === "then") return undefined;
                return makeChain(steps.concat([{ type: "get", key: prop }]));
            },
            apply(_t, _thisArg, args) {
                return makeChain(steps.concat([{ type: "call", args: args || [] }]));
            }
        });
    }

    return new Proxy(Object.create(null), {
        get(_t, prop) {
            const api = ensureResolved();
            if (api) {
                const v = api[prop];
                if (typeof v === "function") return v.bind(api);
                return v;
            }
            return makeChain([{ type: "get", key: prop }]);
        }
    });
}

// module contract
function normalizeMeta(exp, fallbackName) {
    const m = exp && exp.META ? exp.META : null;
    const name = (m && m.name) ? String(m.name) : String(fallbackName || "");
    const globalName = (m && m.globalName) ? String(m.globalName) : "";
    const version = (m && m.version) ? String(m.version) : "0.0.0";
    const description = (m && m.description) ? String(m.description) : "";
    const engineMin = (m && m.engineMin) ? String(m.engineMin) : "";
    return { name, globalName, version, description, engineMin };
}

function requireModule(moduleId) {
    try { return require(moduleId); }
    catch (e) {
        throw new Error("[builtin/bootstrap] require failed: " + moduleId + " :: " + (e && e.message ? e.message : e));
    }
}

function instantiateModule(exp, engine, meta) {
    if (typeof exp !== "function") {
        throw new Error("[builtin/bootstrap] Module export must be a function (engine,K)=>api for: " + meta.name);
    }
    const api = exp(engine, K);
    if (!api || typeof api !== "object") {
        throw new Error("[builtin/bootstrap] Module factory returned invalid api for: " + meta.name);
    }
    // IMPORTANT: do not attach meta onto api; it may be frozen
    return api;
}

// bootstrap
class KalitechBootstrap {
    constructor(defaults) {
        this.defaults = defaults;
        this.config = deepMergePlain({}, defaults);
        K.config = this.config;
    }

    init() {
        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);
        const mods = (this.config.builtins && this.config.builtins.modules) ? this.config.builtins.modules : {};

        for (const key of Object.keys(mods)) {
            const exp = requireModule(mods[key]);
            const meta = normalizeMeta(exp, key);

            K.modules[meta.name] = exp;
            K.meta[meta.name] = meta;

            if (expose) {
                globalThis[meta.name] = createDeferredProxy(() => K.instances[meta.name] || null, meta.name);
                if (meta.globalName) {
                    globalThis[meta.globalName] = createDeferredProxy(() => K.instances[meta.name] || null, meta.globalName);
                }
            }
        }

        return this;
    }

    attachEngine(engine) {
        if (!engine) return false;
        if (K._engineAttached && K._engine === engine) return true;

        K._engine = engine;
        K._engineAttached = true;

        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);
        const engVer = readEngineVersion(engine);

        for (const name of Object.keys(K.modules)) {
            const exp = K.modules[name];
            const meta = K.meta[name] || { name: name };

            if (meta.engineMin && engVer && !semverGte(engVer, meta.engineMin)) {
                throw new Error(
                    "[builtin/bootstrap] Engine version " + engVer +
                    " is ниже минимальной " + meta.engineMin +
                    " для модуля " + name
                );
            }

            const api = instantiateModule(exp, engine, meta);
            K.instances[name] = api;
            K.instancesMeta[name] = meta;

            if (expose) {
                globalThis[name] = api;
                if (meta.globalName) globalThis[meta.globalName] = api;
            }
        }

        const q = K._deferred;
        K._deferred = [];
        for (let i = 0; i < q.length; i++) {
            try { q[i](engine); } catch (_) {}
        }

        return true;
    }

    whenEngine(fn) {
        if (K._engineAttached && K._engine) {
            try { fn(K._engine); } catch (_) {}
            return true;
        }
        K._deferred.push(fn);
        return false;
    }

    whenEngineOnce(key, fn) {
        const k = String(key || "");
        if (!k) return this.whenEngine(fn);
        if (K._once[k]) return false;
        K._once[k] = true;
        return this.whenEngine(fn);
    }
}

const boot = new KalitechBootstrap(DEFAULT_CONFIG).init();

module.exports = {
    config: boot.config,
    attachEngine: boot.attachEngine.bind(boot),
    whenEngine: boot.whenEngine.bind(boot),
    whenEngineOnce: boot.whenEngineOnce.bind(boot),
    safeJson
};