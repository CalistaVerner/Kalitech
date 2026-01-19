// FILE: resources/kalitech/builtin/Particles.js
// Author: Calista Verner
"use strict";

const {req} = require("../Entity/helpers/EntUtil.js");

function isObj(v) {
    return !!v && typeof v === "object";
}

function hasOwn(o, k) {
    return !!o && Object.prototype.hasOwnProperty.call(o, k);
}

function safeInt(v, fb) {
    v = v | 0;
    return Number.isFinite(v) ? v : (fb | 0);
}

function getLog(engine) {
    try {
        if (engine && typeof engine.log === "function") {
            const l = engine.log();
            if (l && typeof l.error === "function") return l;
        }
    } catch (_) {
    }
    try {
        if (globalThis.ENGINE && typeof ENGINE.log === "function") {
            const l = ENGINE.log();
            if (l && typeof l.error === "function") return l;
        }
    } catch (_) {
    }

    const c = console || Object.create(null);
    return {
        error: (m, e) => (c.error ? c.error(m, e) : void 0),
        warn: (m, e) => (c.warn ? c.warn(m, e) : void 0),
        info: (m, e) => (c.info ? c.info(m, e) : void 0)
    };
}

/**
 * Deep override:
 * - Objects merge recursively
 * - Arrays replace
 * - Primitives replace
 */
function deepMerge(base, over) {
    if (!isObj(over)) return base;

    if (!isObj(base) || Array.isArray(base)) {
        if (Array.isArray(over)) return over.slice();
        const out0 = Object.create(null);
        const ks0 = Object.keys(over);
        for (let i = 0; i < ks0.length; i++) {
            const k = ks0[i];
            const v = over[k];
            out0[k] = (isObj(v) && !Array.isArray(v)) ? deepMerge(null, v) : (Array.isArray(v) ? v.slice() : v);
        }
        return out0;
    }

    const out = Object.assign({}, base);
    const ks = Object.keys(over);
    for (let i = 0; i < ks.length; i++) {
        const k = ks[i];
        const ov = over[k];
        const bv = out[k];

        if (Array.isArray(ov)) {
            out[k] = ov.slice();
            continue;
        }
        if (isObj(ov)) {
            out[k] = deepMerge(bv, ov);
            continue;
        }
        out[k] = ov;
    }
    return out;
}

function stripMax(cfg) {
    if (!cfg || !hasOwn(cfg, "max")) return cfg;
    const out = Object.assign({}, cfg);
    delete out.max;
    return out;
}

function isPlainObject(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function isSpawnOptsLike(v) {
    if (!isPlainObject(v)) return false;
    return (
        hasOwn(v, "pos") || hasOwn(v, "rot") || hasOwn(v, "scale") ||
        hasOwn(v, "dir") || hasOwn(v, "velocity") ||
        hasOwn(v, "burst") || hasOwn(v, "ttlMs") || hasOwn(v, "seed") ||
        hasOwn(v, "override") || hasOwn(v, "deepOverride")
    );
}

function normalizeSpawnArgs(overCfg, opts) {
    // Backward compatible:
    // 1) spawn(name, overCfg, opts)
    // 2) spawn(name, optsBag) where optsBag may include {override|deepOverride}
    // 3) spawn(name, overCfg) (no opts)
    let over = overCfg;
    let o = opts;

    if (opts === undefined && isSpawnOptsLike(overCfg)) {
        o = overCfg;
        over = (isPlainObject(o.override) ? o.override : (isPlainObject(o.deepOverride) ? o.deepOverride : null));
    }

    if (!isPlainObject(over)) over = null;
    if (!isPlainObject(o)) o = null;
    return {over, opts: o};
}

function create(engine, K) {
    req(engine, "[PARTICLES] engine is required");
    req(typeof engine.particles === "function", "[PARTICLES] engine.particles() is required");

    const api = engine.particles();
    const log = getLog(engine);

    const templates = Object.create(null);
    const pools = Object.create(null);
    const inUse = new Map();
    const leaseGen = new Map();
    let leaseSeq = 1;

    const stats = {created: 0, reused: 0, destroyed: 0, released: 0};

    // ------------------------------------------------------------
    // Bank (autoload with retry + fallbacks)
    // ------------------------------------------------------------
    const bank = {
        loaded: false,
        path: "data/particles.json",
        candidates: ["data/particles.json", "particles.json", "config/particles.json"],
        lastError: "",
        lastLogAtMs: 0,
        logThrottleMs: 2000
    };

    function setBankPath(path) {
        bank.path = String(path || "").trim() || bank.path;
        bank.loaded = false;
        bank.lastError = "";
        return bank.path;
    }

    function _logAutoloadFailOnce(msg, err) {
        const now = (typeof Date !== "undefined" && Date.now) ? Date.now() : 0;
        if (now && (now - bank.lastLogAtMs) < bank.logThrottleMs) return;
        bank.lastLogAtMs = now;
        log.error(msg, err);
    }

    function tryAutoLoadBank() {
        if (bank.loaded) return true;

        // Ensure candidates include current path as first try
        const cands = [bank.path].concat(bank.candidates);
        const uniq = [];
        const seen = Object.create(null);
        for (let i = 0; i < cands.length; i++) {
            const p = String(cands[i] || "").trim();
            if (!p) continue;
            if (seen[p]) continue;
            seen[p] = 1;
            uniq.push(p);
        }

        // Try all candidates each time; do not permanently "give up"
        for (let i = 0; i < uniq.length; i++) {
            const p = uniq[i];
            try {
                const txt = ASSETS.readText(p);
                const obj = JSON.parse(txt);
                loadBank(obj);
                bank.path = p;
                bank.loaded = true;
                bank.lastError = "";
                return true;
            } catch (e) {
                bank.lastError = String(e && e.message ? e.message : e);
                _logAutoloadFailOnce("[PARTICLES] bank autoload failed: " + p, e);
            }
        }

        return false;
    }

    function ensureBankLoaded() {
        if (bank.loaded) return true;
        tryAutoLoadBank();
        return bank.loaded;
    }

    function loadBank(bankObj) {
        if (!isObj(bankObj)) throw new Error("[PARTICLES] loadBank(bankObj): object is required");

        const src = (bankObj.templates != null) ? bankObj.templates : bankObj;

        if (Array.isArray(src)) {
            for (let i = 0; i < src.length; i++) {
                const e = src[i];
                if (!isObj(e)) throw new Error("[PARTICLES] templates[] entry must be object");
                const name = String(e.name || "").trim();
                if (!name) throw new Error("[PARTICLES] templates[] entry must have name");
                define(name, e);
            }
        } else if (isObj(src)) {
            const ks = Object.keys(src);
            for (let i = 0; i < ks.length; i++) {
                const name = ks[i];
                define(name, src[name]);
            }
        } else {
            throw new Error("[PARTICLES] bank.templates must be object-map or array");
        }

        bank.loaded = true;
        return true;
    }

    // ------------------------------------------------------------
    // Pooling
    // ------------------------------------------------------------
    function poolArr(name) {
        let a = pools[name];
        if (!a) {
            a = [];
            pools[name] = a;
        }
        return a;
    }

    function safeDestroy(h) {
        if (!h) return;
        try {
            api.destroy(h);
        } catch (_) {
        }
        stats.destroyed++;
    }

    function poolMaxFor(name) {
        const t = templates[name];
        const pool = t && isObj(t.pool) ? t.pool : null;
        const max = pool && pool.max != null ? safeInt(pool.max, 32) : 32;
        return Math.max(0, Math.min(2048, max | 0));
    }

    function acquire(name, cfg) {
        const pool = poolArr(name);
        while (pool.length) {
            const h = pool.pop();
            if (!h) continue;
            inUse.set(h.id, name);
            const gen = (leaseSeq++ | 0);
            leaseGen.set(h.id, gen);
            stats.reused++;
            return {h, fresh: false, gen};
        }

        const h = api.create(cfg);
        if (!h) return null;

        inUse.set(h.id, name);
        const gen = (leaseSeq++ | 0);
        leaseGen.set(h.id, gen);
        stats.created++;
        return {h, fresh: true, gen};
    }

    function release(h) {
        if (!h) return false;
        const name = inUse.get(h.id);
        if (!name) {
            safeDestroy(h);
            return false;
        }

        inUse.delete(h.id);
        leaseGen.delete(h.id);

        try {
            if (typeof api.stop === "function") api.stop(h);
            if (typeof api.clear === "function") api.clear(h);
            if (typeof api.setEnabled === "function") api.setEnabled(h, true);
        } catch (_) {
        }

        const pool = poolArr(name);
        const cap = poolMaxFor(name);
        if (cap > 0 && pool.length < cap) pool.push(h);
        else safeDestroy(h);

        stats.released++;
        return true;
    }

    function ttlRelease(h, ttlMs, gen) {
        ttlMs = (ttlMs | 0) || 0;
        const ms = Math.max(25, ttlMs > 0 ? ttlMs : 900);
        if (typeof setTimeout !== "function") return;

        setTimeout(() => {
            try {
                if (!inUse.has(h.id)) return;
                if (leaseGen.get(h.id) !== gen) return;
                release(h);
            } catch (_) {
            }
        }, ms);
    }

    // ------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------
    function define(name, cfg) {
        name = String(name || "").trim();
        if (!name) throw new Error("[PARTICLES] define(name,cfg): name is required");
        if (!isObj(cfg)) throw new Error("[PARTICLES] define(name,cfg): cfg object is required");
        templates[name] = Object.freeze(Object.assign({}, cfg));
        if (!pools[name]) pools[name] = [];
        return true;
    }

    function getTemplate(name) {
        ensureBankLoaded();
        name = String(name || "").trim();
        const t = templates[name];
        if (!t) throw new Error("[PARTICLES] unknown template '" + name + "'");
        return t;
    }

    function spawn(name, overCfg, opts) {
        const baseCfg = getTemplate(name);

        const n = normalizeSpawnArgs(overCfg, opts);
        const over = n.over;
        const o = n.opts;

        const hasOver = isObj(over) && Object.keys(over).length > 0;
        const hasOpts = isObj(o);

        let cfg = baseCfg;
        if (hasOver) cfg = deepMerge(baseCfg, over);

        if (hasOpts) {
            if (o.pos != null) {
                if (cfg === baseCfg) cfg = Object.assign({}, cfg);
                cfg.pos = o.pos;
            }
            if (o.rot != null) {
                if (cfg === baseCfg) cfg = Object.assign({}, cfg);
                cfg.rot = o.rot;
            }
            if (o.scale != null) {
                if (cfg === baseCfg) cfg = Object.assign({}, cfg);
                cfg.scale = +o.scale;
            }

            if (o.dir != null || o.velocity != null) {
                const vOver = isObj(o.velocity) ? o.velocity : null;
                const vDir = isObj(o.dir) ? {dir: o.dir} : null;

                const baseV = isObj(cfg.velocity) ? cfg.velocity : null;
                const mergedV = deepMerge(baseV || Object.create(null), vOver || Object.create(null));
                if (vDir) mergedV.dir = vDir.dir;

                if (cfg === baseCfg) cfg = Object.assign({}, cfg);
                cfg.velocity = mergedV;
            }

            if (o.seed != null) {
                if (cfg === baseCfg) cfg = Object.assign({}, cfg);
                cfg.seed = o.seed | 0;
            }
        }

        cfg = stripMax(cfg);

        const acq = acquire(name, cfg);
        if (!acq || !acq.h) return null;
        const h = acq.h;

        try {
            if (cfg.pos && typeof api.setPosition === "function") api.setPosition(h, cfg.pos);
            if (cfg.rot && typeof api.setRotation === "function") api.setRotation(h, cfg.rot);
            if (cfg.scale != null && typeof api.setScale === "function") api.setScale(h, cfg.scale);
        } catch (e) {
            log.error("[PARTICLES] spawn: set transform failed", e);
        }

        try {
            if (typeof api.configure === "function") api.configure(h, cfg);
        } catch (e) {
            log.error("[PARTICLES] spawn: configure failed", e);
        }

        try {
            if (typeof api.clear === "function") api.clear(h);

            const burst = hasOpts ? (o.burst | 0) : 0;
            if (burst > 0) {
                if (typeof api.emit === "function") api.emit(h, burst);
                else if (typeof api.emitAll === "function") api.emitAll(h);
            } else {
                if (typeof api.emitAll === "function") api.emitAll(h);
            }
        } catch (e) {
            log.error("[PARTICLES] spawn: emit failed", e);
        }

        const ttlMs = hasOpts ? (o.ttlMs | 0) : 0;
        const t0 = (ttlMs > 0) ? ttlMs : ((cfg.ttlMs | 0) || 900);
        ttlRelease(h, t0, acq.gen);

        return h;
    }

    function flush(name) {
        if (name == null) {
            const keys = Object.keys(pools);
            for (let i = 0; i < keys.length; i++) flush(keys[i]);
            return true;
        }

        name = String(name || "").trim();
        const pool = pools[name];
        if (!pool) return false;

        while (pool.length) safeDestroy(pool.pop());
        return true;
    }

    function info() {
        return Object.freeze({
            alive: (typeof api.alive === "function") ? api.alive() : -1,
            templates: Object.keys(templates).length,
            pooled: Object.keys(pools).reduce((acc, k) => acc + (pools[k] ? pools[k].length : 0), 0),
            inUse: inUse.size,
            stats: Object.assign({}, stats),
            bank: Object.freeze({loaded: bank.loaded, path: bank.path, lastError: bank.lastError})
        });
    }

    // Try once at init; if it fails, future spawns will retry automatically.
    tryAutoLoadBank();

    return Object.freeze({
        create: (cfg) => api.create(cfg),
        destroy: (h) => api.destroy(h),
        configure: (h, cfg) => api.configure(h, cfg),
        setEnabled: (h, on) => api.setEnabled(h, on),
        play: (h) => api.play(h),
        stop: (h) => api.stop(h),
        clear: (h) => (typeof api.clear === "function" ? api.clear(h) : void 0),
        setPosition: (h, v) => api.setPosition(h, v),
        setRotation: (h, q) => api.setRotation(h, q),
        setScale: (h, s) => api.setScale(h, s),
        emitAll: (h) => api.emitAll(h),
        emit: (h, n) => (typeof api.emit === "function" ? api.emit(h, n | 0) : api.emitAll(h)),
        alive: () => api.alive(),

        setBankPath,
        define,
        loadBank,
        getTemplate,
        clearBank: () => {
            for (const k of Object.keys(pools)) flush(k);
            for (const k of Object.keys(templates)) delete templates[k];
            bank.loaded = false;
            bank.lastError = "";
            return true;
        },

        spawn,
        release,
        flush,
        info
    });
}

create.META = {
    moduleId: "particles",
    globalName: "PARTICLES",
    version: "3.2.1",
    description: "AAA particles: templates + pooling + TTL + bank autoload with retry + fallback paths.",
    engineMin: "0.2.0"
};

module.exports = create;
module.exports.META = create.META;