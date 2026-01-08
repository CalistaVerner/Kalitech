// FILE: resources/kalitech/engine/bootstrap/Bootstrap.js
"use strict";

const {DEFAULT_CONFIG} = require("./Config.js");
const {getRoot, ensureRootState} = require("./Root.js");
const U = require("./Util.js");
const {createDeferredProxy} = require("./Deferred.js");
const {buildDataConfigApi} = require("./DataConfig.js");
const {normalizeMeta} = require("./Meta.js");
const {createControllersApi, loadRegistrators} = require("./Controllers.js");

const K = ensureRootState(getRoot());

function requireModule(moduleId) {
    try {
        return require(moduleId);
    } catch (e) {
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
        this.config = U.deepMergePlain({}, defaults);
        K.config = this.config;
        K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);
    }

    static createDefault() {
        return new KalitechBootstrap(DEFAULT_CONFIG);
    }

    init() {
        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);
        const mods = (this.config.builtins && this.config.builtins.modules) ? this.config.builtins.modules : {};

        if (expose) {
            globalThis.DATA_CONFIG = createDeferredProxy(() => K.dataConfigApi || null, "DATA_CONFIG");
        }

        // register builtin module factories (not instantiate yet)
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
        const engVer = U.readEngineVersion(engine);

        // DATA_CONFIG
        try {
            K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);
            K.dataConfigApi = buildDataConfigApi(engine, K.dataConfig);
            if (expose) globalThis.DATA_CONFIG = K.dataConfigApi;
        } catch (e) {
            try {
                LOG && LOG.error && LOG.error("[builtin/bootstrap] DATA_CONFIG init failed: " + (e && e.message ? e.message : e));
            } catch (_) {
            }
        }

        // instantiate builtins
        for (const name of Object.keys(K.modules)) {
            const exp = K.modules[name];
            const moduleId = K.moduleIds[name] || null;

            const meta = normalizeMeta(exp, name, moduleId, engine);
            K.meta[name] = meta;
            K.instancesMeta[name] = meta;

            if (meta.engineMin && engVer && !U.semverGte(engVer, meta.engineMin)) {
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

        // CONTROLLERS (engine-level)
        try {
            const ccfg = (this.config && this.config.controllers) ? this.config.controllers : Object.create(null);
            K.controllersApi = createControllersApi(K);

            if (ccfg && ccfg.exposeGlobals) globalThis.CONTROLLERS = K.controllersApi;

            loadRegistrators(engine, K, ccfg, K.controllersApi);
        } catch (e) {
            try {
                LOG && LOG.error && LOG.error("[builtin/bootstrap] CONTROLLERS init failed: " + (e && e.message ? e.message : e));
            } catch (_) {
            }
        }

        // deferred queue
        const q = K._deferred;
        K._deferred = [];
        for (let i = 0; i < q.length; i++) {
            try {
                q[i](engine);
            } catch (_) {
            }
        }

        return true;
    }

    whenEngine(fn) {
        if (K._engineAttached && K._engine) {
            try {
                fn(K._engine);
            } catch (_) {
            }
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

module.exports = KalitechBootstrap;
module.exports.createDefault = KalitechBootstrap.createDefault;
module.exports.safeJson = U.safeJson;