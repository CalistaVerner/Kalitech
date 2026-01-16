// FILE: resources/kalitech/builtin/Particles.js
// Author: Calista Verner
"use strict";

const {req} = require("../Entity/helpers/EntUtil.js");

function isObj(v) {
    return !!v && typeof v === "object";
}

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function mergeShallow(base, over) {
    const out = Object.assign({}, base || null);
    if (!over) return out;
    const ks = Object.keys(over);
    for (let i = 0; i < ks.length; i++) out[ks[i]] = over[ks[i]];
    return out;
}

function create(engine, K) {
    req(engine, "[PARTICLES] engine is required");
    req(typeof engine.particles === "function", "[PARTICLES] engine.particles() is required");

    const api = engine.particles();

    // ------------------------------------------------------------
    // AAA layer state
    // ------------------------------------------------------------
    const templates = Object.create(null);        // name -> cfg
    const pools = Object.create(null);            // name -> [handle,...]
    const inUse = new Map();                      // handle.id -> name
    const stats = {created: 0, reused: 0, destroyed: 0};

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

    function acquire(name, cfg) {
        const pool = poolArr(name);
        while (pool.length) {
            const h = pool.pop();
            if (!h) continue;
            inUse.set(h.id, name);
            stats.reused++;
            return h;
        }

        const h = api.create(cfg);
        if (!h) return null;

        inUse.set(h.id, name);
        stats.created++;
        return h;
    }

    function release(h) {
        if (!h) return false;
        const name = inUse.get(h.id);
        if (!name) {
            safeDestroy(h);
            return false;
        }

        inUse.delete(h.id);

        // reset-ish: stop continuous emission; leave emitter alive for reuse
        try {
            if (typeof api.stop === "function") api.stop(h);
            if (typeof api.setEnabled === "function") api.setEnabled(h, true);
        } catch (_) {
        }

        poolArr(name).push(h);
        return true;
    }

    function ttlRelease(h, ttlMs) {
        ttlMs = (ttlMs | 0) || 0;
        const ms = Math.max(25, ttlMs > 0 ? ttlMs : 900);
        if (typeof setTimeout !== "function") return;

        setTimeout(() => {
            try {
                // If already freed or replaced, ignore
                if (!inUse.has(h.id)) return;
                release(h);
            } catch (_) {
            }
        }, ms);
    }

    function define(name, cfg) {
        name = String(name || "").trim();
        if (!name) throw new Error("[PARTICLES] define(name,cfg): name is required");
        if (!isObj(cfg)) throw new Error("[PARTICLES] define(name,cfg): cfg object is required");
        templates[name] = Object.freeze(Object.assign({}, cfg));
        if (!pools[name]) pools[name] = [];
        return true;
    }

    function spawn(nameOrCfg, opts) {
        opts = opts || null;

        let name = null;
        let baseCfg = null;

        if (typeof nameOrCfg === "string") {
            name = nameOrCfg;
            baseCfg = templates[name];
            if (!baseCfg) throw new Error("[PARTICLES] spawn: unknown template '" + name + "'");
        } else if (isObj(nameOrCfg)) {
            name = "_anon";
            baseCfg = nameOrCfg;
        } else {
            throw new Error("[PARTICLES] spawn(nameOrCfg, opts): invalid first arg");
        }

        const over = (opts && isObj(opts.cfg)) ? opts.cfg : null;
        const cfg = mergeShallow(baseCfg, over);

        // one-shot defaults
        if (cfg.enabled == null) cfg.enabled = true;
        if (cfg.rate == null) cfg.rate = 0; // burst by default

        // runtime overrides (pos/rot/scale/velocity)
        if (opts && isObj(opts.pos)) cfg.pos = opts.pos;
        if (opts && isObj(opts.rot)) cfg.rot = opts.rot;
        if (opts && opts.scale != null) cfg.scale = +opts.scale;

        if (opts && (isObj(opts.dir) || isObj(opts.velocity))) {
            const v = mergeShallow(cfg.velocity, isObj(opts.velocity) ? opts.velocity : null);
            if (isObj(opts.dir)) v.dir = opts.dir;
            cfg.velocity = v;
        }

        const h = acquire(name, cfg);
        if (!h) return null;

        // apply transform (safe even if engine already applied in create)
        try {
            if (cfg.pos && typeof api.setPosition === "function") api.setPosition(h, cfg.pos);
            if (cfg.rot && typeof api.setRotation === "function") api.setRotation(h, cfg.rot);
            if (cfg.scale != null && typeof api.setScale === "function") api.setScale(h, cfg.scale);
        } catch (_) {
        }

        // apply late config changes (dir/cone etc) if engine supports
        try {
            if (typeof api.configure === "function") api.configure(h, cfg);
        } catch (_) {
        }

        // emit
        const burst = opts ? (opts.burst | 0) : 0;
        try {
            if (burst > 0 && typeof api.emit === "function") api.emit(h, burst);
            else if (burst > 0 && typeof api.emitAll === "function") api.emitAll(h);
            else if (typeof api.emitAll === "function") api.emitAll(h);
        } catch (_) {
        }

        // schedule release (not destroy)
        const ttlMs = opts ? (opts.ttlMs | 0) : 0;
        ttlRelease(h, ttlMs > 0 ? ttlMs : ((cfg.ttlMs | 0) || 900));

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
            stats: Object.assign({}, stats)
        });
    }

    // ------------------------------------------------------------
    // Backward compatible thin proxy + AAA extensions
    // ------------------------------------------------------------
    return Object.freeze({
        // low-level
        create: (cfg) => api.create(cfg),
        destroy: (h) => api.destroy(h),
        configure: (h, cfg) => api.configure(h, cfg),
        setEnabled: (h, on) => api.setEnabled(h, on),
        play: (h) => api.play(h),
        stop: (h) => api.stop(h),
        setPosition: (h, v) => api.setPosition(h, v),
        setRotation: (h, q) => api.setRotation(h, q),
        setScale: (h, s) => api.setScale(h, s),
        emitAll: (h) => api.emitAll(h),
        emit: (h, n) => (typeof api.emit === "function" ? api.emit(h, n | 0) : api.emitAll(h)),
        alive: () => api.alive(),

        // AAA
        define,
        spawn,
        release,
        flush,
        info
    });
}

create.META = {
    moduleId: "particles",
    globalName: "PARTICLES",
    version: "3.0.0",
    description: "AAA particle spawning: templates + pooling + one-shot burst + direction/shape",
    engineMin: "0.2.0"
};

module.exports = create;
module.exports.META = create.META;