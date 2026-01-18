// FILE: resources/kalitech/engine/bootstrap/Bootstrap.js
"use strict";

const {DEFAULT_CONFIG} = require("./Config.js");
const {getRoot, ensureRootState} = require("./Root.js");
const U = require("./Util.js");
const {createDeferredProxy} = require("./Deferred.js");
const {buildDataConfigApi} = require("./DataConfig.js");
const {normalizeMeta} = require("./Meta.js");
const {createEngineProxy} = require("./EngineProxy.js");


const K = ensureRootState(getRoot());

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function str(v) {
    return String(v == null ? "" : v);
}

function sOrUnknown(v) {
    const s = str(v).trim();
    return s ? s : "unknown";
}

function requireModule(moduleId) {
    try {
        return require(moduleId);
    } catch (e) {
        throw new Error("[bootstrap] require failed: " + sOrUnknown(moduleId) + " :: " + sOrUnknown(e && e.message));
    }
}

function instantiateModule(exp, engine, meta) {
    const name = sOrUnknown(meta && meta.name);
    if (typeof exp !== "function") {
        throw new Error("[bootstrap] Module export must be a function (engine,K)=>api for: " + name);
    }
    const api = exp(engine, K);
    if (!api || typeof api !== "object") {
        throw new Error("[bootstrap] Module factory returned invalid api for: " + name);
    }
    return api;
}

function ensureENGINE() {
    if (!K.ENGINE) K.ENGINE = Object.create(null);
    return K.ENGINE;
}

function pickEngineKey(meta, fallbackId) {
    const k = meta && meta.key ? str(meta.key).trim() : "";
    if (k) return k;

    const mid = meta && meta.moduleId ? str(meta.moduleId).trim() : "";
    if (mid) return mid;

    const n = meta && meta.name ? str(meta.name).trim() : "";
    if (n) {
        if (n.indexOf("@module/") === 0 || n.indexOf("@builtin/") === 0) {
            const tail = n.split("/").pop() || "module";
            return tail.toLowerCase();
        }
        return n.toLowerCase();
    }

    const fb = str(fallbackId);
    const tail = fb.split("/").pop() || "module";
    return tail.replace(/\.js$/i, "").toLowerCase();
}

function logEngineModule(ENGINE, key, meta, moduleId) {
    try {
        const msg =
            "[ENGINE] module registered: " +
            "key='" + sOrUnknown(key) + "' " +
            "name='" + sOrUnknown(meta && meta.name) + "' " +
            "ver='" + sOrUnknown(meta && meta.version) + "'" +
            " from='" + sOrUnknown(moduleId) + "'";

        const L = ENGINE && ENGINE.log;
        if (L && typeof L.info === "function") {
            L.info(msg);
            return;
        }

        if (globalThis.LOG && typeof globalThis.LOG.info === "function") {
            globalThis.LOG.info(msg);
            return;
        }
        if (typeof print === "function") {
            print(msg);
            return;
        }
        if (typeof console !== "undefined" && console.log) console.log(msg);
    } catch (_) {
    }
}

function loadEngineModulesFromManifest() {
    const man = require("@module/manifest");
    const list = man && man.modules;

    if (!Array.isArray(list) || !list.length) {
        throw new Error("[bootstrap] Missing or invalid engine modules manifest: @module/manifest");
    }

    const out = [];
    for (let i = 0; i < list.length; i++) {
        const id = str(list[i]).trim();
        if (!id) continue;
        out.push(id);
    }

    if (!out.length) throw new Error("[bootstrap] Engine modules manifest is empty: @module/manifest");
    return out;
}

class KalitechBootstrap {
    constructor(defaults) {
        this.defaults = defaults;
        this.config = U.deepMergePlain({}, defaults);

        K.config = this.config;
        K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);

        ensureENGINE();
    }

    static createDefault() {
        return new KalitechBootstrap(DEFAULT_CONFIG);
    }

    init() {
        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);

        if (expose) {
            globalThis.DATA_CONFIG = createDeferredProxy(() => K.dataConfigApi || null, "DATA_CONFIG");
            globalThis.ENGINE = createDeferredProxy(() => K.ENGINE || null, "ENGINE");
        }

        return this;
    }

    attachEngine(engine) {
        engine = req(engine, "[bootstrap] engine is required");
        if (K._engineAttached && K._engine === engine) return true;

        K._engine = engine;
        K._engineAttached = true;

        const expose = !!(this.config.builtins && this.config.builtins.exposeGlobals);
        const engVer = U.readEngineVersion(engine);

        //const ENGINE = ensureENGINE();
        const ENGINE = createEngineProxy(engine);

        try {
            K.dataConfig = (this.config && this.config.dataConfig) ? this.config.dataConfig : Object.create(null);
            K.dataConfigApi = buildDataConfigApi(engine, K.dataConfig);
            if (expose) globalThis.DATA_CONFIG = K.dataConfigApi;
        } catch (_) {
        }

        const moduleIds = loadEngineModulesFromManifest();

        for (let i = 0; i < moduleIds.length; i++) {
            const moduleId = moduleIds[i];

            const exp = requireModule(moduleId);
            const meta = normalizeMeta(exp, moduleId, moduleId, engine);

            const mid = sOrUnknown(meta && meta.moduleId);
            const gname = (meta && meta.globalName) ? str(meta.globalName).trim() : "";

            if (meta && meta.engineMin && engVer && !U.semverGte(engVer, meta.engineMin)) {
                throw new Error(
                    "[bootstrap] Engine version " + sOrUnknown(engVer) +
                    " is below minimum " + sOrUnknown(meta.engineMin) +
                    " for module " + mid
                );
            }

            const api = instantiateModule(exp, engine, meta);

            const key = (meta && meta.moduleId && str(meta.moduleId).trim())
                ? str(meta.moduleId).trim()
                : pickEngineKey(meta, moduleId);

            if (ENGINE.hasModule(key)) {
                throw new Error("[ENGINE] duplicate module key '" + key + "' while registering: " + moduleId);
            }
            ENGINE.setModule(key, api);


            //ENGINE[key] = api;

            if (expose) globalThis.ENGINE = ENGINE;

            if (expose) {
                if (gname && !globalThis[gname]) globalThis[gname] = api;
                const up = str(key).toUpperCase();
                if (up && !globalThis[up]) globalThis[up] = api;
            }

            K.instances = K.instances || Object.create(null);
            K.instancesMeta = K.instancesMeta || Object.create(null);
            K.moduleIds = K.moduleIds || Object.create(null);

            K.instances[key] = api;
            K.instancesMeta[key] = meta;

            const idKey = (meta && meta.moduleId && str(meta.moduleId).trim())
                ? str(meta.moduleId).trim()
                : sOrUnknown(meta && meta.name);

            K.moduleIds[idKey] = moduleId;

            logEngineModule(ENGINE, key, meta, moduleId);
        }

        try {
            const ccfg = (this.config && this.config.controllers) ? this.config.controllers : Object.create(null);
            const regs = Array.isArray(ccfg.registrators) ? ccfg.registrators : [];

            if (ENGINE.controllers && typeof ENGINE.controllers.loadRegistrators === "function") {
                ENGINE.controllers.loadRegistrators(regs);
            }
        } catch (e) {
            try {
                const msg = "[bootstrap] controllers.registrators failed: " + sOrUnknown(e && e.message);
                if (ENGINE.log && typeof ENGINE.log.error === "function") ENGINE.log.error(msg);
                else if (globalThis.LOG && typeof globalThis.LOG.error === "function") globalThis.LOG.error(msg);
            } catch (_) {
            }
        }

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
        const k = str(key);
        if (!k) return this.whenEngine(fn);
        if (K._once[k]) return false;
        K._once[k] = true;
        return this.whenEngine(fn);
    }
}

module.exports = KalitechBootstrap;
module.exports.createDefault = KalitechBootstrap.createDefault;
module.exports.safeJson = U.safeJson;
