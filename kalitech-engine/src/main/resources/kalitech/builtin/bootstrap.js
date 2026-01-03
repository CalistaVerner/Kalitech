// FILE: resources/kalitech/builtin/bootstrap.js
// Author: Calista Verner
"use strict";

const DEFAULT_CONFIG = {
    aliases: {
        "@core": "Scripts/core",
        "@lib": "Scripts/lib",
        "@engine": "Scripts/engine",
        "@systems": "Scripts/systems",
        "@materials": "Scripts/materials",
        "@env": "Scripts/environment"
    },

    dataConfig: {
        materials: { path: "data/materials.json" },
        camera:    { path: "data/camera/camera.config.json" },
        movement:  { path: "data/player/movement.config.json" },
        player: { path: "data/player.json" }
    },

    builtins: {
        exposeGlobals: true,
        modules: {
            materials: "@builtin/Material/Material",
            mesh: "@builtin/Mesh/Mesh",
            sound: "@builtin/Sound/Sound",
            entity: "@builtin/Entity/Entity",
            physics: "@builtin/Physics/Physics",
            log: "@builtin/Log/Log",
            input: "@builtin/Input/Input",
            events: "@builtin/Events/Events",
            terrain: "@builtin/Terrain/Terrain",
            hud: "@builtin/Hud/Hud"
        }
    }
};

const ROOT_KEY = "__kalitech";
if (!globalThis[ROOT_KEY]) globalThis[ROOT_KEY] = Object.create(null);
const K = globalThis[ROOT_KEY];

function ensureRootState(root) {
    if (!root.modules) root.modules = Object.create(null);
    if (!root.instances) root.instances = Object.create(null);
    if (!root.meta) root.meta = Object.create(null);
    if (!root.instancesMeta) root.instancesMeta = Object.create(null);
    if (!root.moduleIds) root.moduleIds = Object.create(null);
    if (!root._engine) root._engine = null;
    if (!root._engineAttached) root._engineAttached = false;
    if (!root._deferred) root._deferred = [];
    if (!root._once) root._once = Object.create(null);
    if (!root.config) root.config = Object.create(null);
    if (!root.dataConfig) root.dataConfig = Object.create(null);
    if (!root.dataConfigApi) root.dataConfigApi = null;
    return root;
}
ensureRootState(K);

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
    if (!A || !B) return true;
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

function _isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function _readTextAsset(engine, path) {
    try {
        const a = engine && engine.assets && engine.assets();
        if (a) {
            if (typeof a.readText === "function") return a.readText(path);
            if (typeof a.text === "function") return a.text(path);
            if (typeof a.getText === "function") return a.getText(path);
        }
    } catch (_) {}
    try {
        const fs = engine && engine.fs && engine.fs();
        if (fs && typeof fs.readText === "function") return fs.readText(path);
    } catch (_) {}
    return null;
}

function _buildDataConfigApi(engine, cfgSection) {
    const cfg = _isPlainObj(cfgSection) ? cfgSection : Object.create(null);

    const cacheText = Object.create(null);
    const cacheJson = Object.create(null);

    function list() { return Object.keys(cfg); }

    function pathOf(name) {
        const k = String(name || "");
        const e = cfg[k];
        if (!e) return "";
        if (typeof e === "string") return e;
        if (e && typeof e.path === "string") return e.path;
        return "";
    }

    function readText(name) {
        const p = pathOf(name);
        if (!p) return null;
        if (cacheText[p] != null) return cacheText[p];
        const txt = _readTextAsset(engine, p);
        cacheText[p] = (txt != null) ? String(txt) : null;
        return cacheText[p];
    }

    function readJson(name) {
        const p = pathOf(name);
        if (!p) return null;
        if (cacheJson[p] != null) return cacheJson[p];
        const txt = readText(name);
        if (!txt) { cacheJson[p] = null; return null; }
        try {
            const obj = JSON.parse(String(txt));
            cacheJson[p] = obj;
            return obj;
        } catch (_) {
            cacheJson[p] = null;
            return null;
        }
    }

    function reload(name) {
        const p = pathOf(name);
        if (!p) return false;
        delete cacheText[p];
        delete cacheJson[p];
        return true;
    }

    function reloadAll() {
        const ks = list();
        for (let i = 0; i < ks.length; i++) reload(ks[i]);
        return true;
    }

    function get(name) {
        const k = String(name || "");
        if (!cfg[k]) return null;
        return {
            name: k,
            get path() { return pathOf(k); },
            text: function () { return readText(k); },
            json: function () { return readJson(k); },
            reload: function () { return reload(k); }
        };
    }

    const api = { list, get, pathOf, readText, readJson, reload, reloadAll };

    const keys = Object.keys(cfg);
    for (let i = 0; i < keys.length; i++) {
        const k = keys[i];
        api[k] = get(k);
    }

    return api;
}

function _isObj(x) { return x && typeof x === "object"; }

function _readJsonSafe(text) {
    try { return JSON.parse(String(text == null ? "" : text)); } catch (_) { return null; }
}

function _dirOf(p) {
    p = String(p || "");
    const a = p.lastIndexOf("/");
    const b = p.lastIndexOf("\\");
    const i = Math.max(a, b);
    return i >= 0 ? p.slice(0, i) : "";
}

function _tryReadKaliModFromFsNearModule(moduleId) {
    try {
        if (typeof require !== "function" || typeof require.resolve !== "function") return null;
        const resolved = require.resolve(moduleId);
        if (!resolved) return null;
        const fs = require("fs");
        const path = require("path");
        const dir = _dirOf(resolved);
        const p = path.join(dir, "kaliMod.json");
        if (!fs.existsSync(p)) return null;
        const txt = fs.readFileSync(p, "utf8");
        const obj = _readJsonSafe(txt);
        return _isObj(obj) ? obj : null;
    } catch (_) {
        return null;
    }
}

function _tryReadKaliModFromAssets(engine, moduleId) {
    try {
        if (!engine || !moduleId) return null;

        let rel = String(moduleId || "");
        if (rel.startsWith("@builtin/")) rel = rel.slice("@builtin/".length);

        const i = Math.max(rel.lastIndexOf("/"), rel.lastIndexOf("\\"));
        const dirRel = i >= 0 ? rel.slice(0, i) : rel;

        const base = "resources/kalitech/builtin/";
        const assetPath = base + dirRel.replace(/\\/g, "/") + "/kaliMod.json";

        const txt = _readTextAsset(engine, assetPath);
        const obj = _readJsonSafe(txt);
        return _isObj(obj) ? obj : null;
    } catch (_) {
        return null;
    }
}

function normalizeMeta(exp, fallbackName, moduleId, engine) {
    const fromFs = moduleId ? _tryReadKaliModFromFsNearModule(moduleId) : null;
    const fromAssets = (!fromFs && engine && moduleId) ? _tryReadKaliModFromAssets(engine, moduleId) : null;
    const fromExport = (exp && exp.META && _isObj(exp.META)) ? exp.META : null;

    const src = fromFs || fromAssets || fromExport;

    const name = (src && src.name) ? String(src.name) : String(fallbackName || "");
    const globalName = (src && src.globalName) ? String(src.globalName) : "";
    const version = (src && src.version) ? String(src.version) : "0.0.0";
    const description = (src && src.description) ? String(src.description) : "";
    const engineMin = (src && src.engineMin) ? String(src.engineMin) : "";
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
    return api;
}

class KalitechBootstrap {
    constructor(defaults) {
        this.defaults = defaults;
        this.config = deepMergePlain({}, defaults);
        K.config = this.config;
        K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);
    }

    init() {
        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);
        const mods = (this.config.builtins && this.config.builtins.modules) ? this.config.builtins.modules : {};

        if (expose) {
            globalThis.DATA_CONFIG = createDeferredProxy(() => K.dataConfigApi || null, "DATA_CONFIG");
        }

        for (const key of Object.keys(mods)) {
            const moduleId = mods[key];
            const exp = requireModule(moduleId);

            const meta = normalizeMeta(exp, key, moduleId, null);

            K.modules[meta.name] = exp;
            K.meta[meta.name] = meta;
            K.moduleIds[meta.name] = moduleId;

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

        try {
            K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);
            K.dataConfigApi = _buildDataConfigApi(engine, K.dataConfig);
            if (expose) globalThis.DATA_CONFIG = K.dataConfigApi;
        } catch (e) {
            try { LOG && LOG.error && LOG.error("[builtin/bootstrap] DATA_CONFIG init failed: " + (e && e.message ? e.message : e)); } catch (_) {}
        }

        for (const name of Object.keys(K.modules)) {
            const exp = K.modules[name];
            const moduleId = K.moduleIds[name] || null;

            const meta = normalizeMeta(exp, name, moduleId, engine);
            K.meta[name] = meta;
            K.instancesMeta[name] = meta;

            if (meta.engineMin && engVer && !semverGte(engVer, meta.engineMin)) {
                throw new Error(
                    "[builtin/bootstrap] Engine version " + engVer +
                    " is ниже минимальной " + meta.engineMin +
                    " для модуля " + name
                );
            }

            const api = instantiateModule(exp, engine, meta);
            K.instances[name] = api;

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