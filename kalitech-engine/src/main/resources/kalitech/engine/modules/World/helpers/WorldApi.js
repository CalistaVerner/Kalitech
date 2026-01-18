// FILE: resources/kalitech/builtin/helpers/world/WorldApi.js
"use strict";

const {req, deepMerge, isObj, subsystem, numInt, str, bool} = require("./WorldUtil.js");
const {WorldBuilder} = require("./WorldBuilder.js");
const {WorldSession} = require("./WorldSession.js");

const WORLD_SCHEMA_VERSION = 1;

const DEFAULT_DAY_SECONDS = 86400;
const DEFAULT_DAY_LENGTH = 1800; // Variant A: 1 game day per 30 real minutes

function stableIdFromModule(modulePath) {
    const m = String(modulePath || "").trim();
    if (!m) return null;

    let x = m.replace(/\\/g, "/");
    x = x.replace(/\?.*$/, "");
    x = x.replace(/\.js$/i, "");
    x = x.replace(/[^a-zA-Z0-9/_\.\-]/g, "_");
    x = x.replace(/\/+/g, "/");
    x = x.toLowerCase();

    return "sys." + x;
}

function ensureUniqueStableIds(systems) {
    const seen = new Set();
    for (let i = 0; i < systems.length; i++) {
        const s = systems[i];
        const id = s && s.stableId != null ? String(s.stableId) : "";
        if (!id) continue;

        if (seen.has(id)) throw new Error("[WORLD] duplicate stableId: " + id);
        seen.add(id);
    }
}

function normalizeTimeDesc(time) {
    if (!isObj(time)) return null;

    const out = {};

    // Absolute time (game seconds)
    if (time.worldTime != null) out.worldTime = +time.worldTime;

    // Legacy multiplier (still supported)
    if (time.timeRate != null) out.timeRate = +time.timeRate;

    // Calendar model
    if (time.daySeconds != null) out.daySeconds = +time.daySeconds;
    if (time.dayLength != null) out.dayLength = +time.dayLength;
    if (time.day != null) out.day = (time.day | 0);
    if (time.timeOfDay != null) out.timeOfDay = +time.timeOfDay;

    // Simulation controls
    if (time.paused != null) out.paused = !!time.paused;
    if (time.fixedStep != null) out.fixedStep = +time.fixedStep;
    if (time.maxDelta != null) out.maxDelta = +time.maxDelta;

    return out;
}

function normalizeMode(mode) {
    const m = (mode == null) ? "game" : String(mode);
    const t = m.trim();
    return t ? t : "game";
}

function defaultTimeVariantA() {
    return {
        daySeconds: DEFAULT_DAY_SECONDS,
        dayLength: DEFAULT_DAY_LENGTH,
        paused: false,
        maxDelta: 0.25
    };
}

class WorldApi {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        req(engine, "[WORLD] engine is required");
        subsystem(engine, "world");

        this._defaults = {
            name: "world",
            start: true,
            runtime: "world",
            orderStep: 10
        };
    }

    /**
     * Reads current world time snapshot from engine (expanded JSON object).
     * Returns null if world is not running / not available.
     *
     * Expected (expanded):
     *  - worldTime: number
     *  - timeRate: number
     *  - paused: boolean
     *  - fixedStep?: number
     *  - maxDelta?: number
     *  - daySeconds?: number
     *  - dayLength?: number
     *  - day?: number
     *  - timeOfDay?: number
     *  - tod01?: number
     *  - hour?: number
     *  - minute?: number
     *  - second?: number
     */
    getWorldTime() {
        const w = subsystem(this.engine, "world");
        if (!w || typeof w.getWorldTime !== "function") return null;

        const t = w.getWorldTime();
        if (!t || typeof t !== "object") return null;

        // Ensure JSON-safe plain object (defensive copy, no host objects)
        const out = {};

        if (t.worldTime != null) out.worldTime = +t.worldTime;
        if (t.timeRate != null) out.timeRate = +t.timeRate;
        if (t.paused != null) out.paused = !!t.paused;

        if (t.fixedStep != null) out.fixedStep = +t.fixedStep;
        if (t.maxDelta != null) out.maxDelta = +t.maxDelta;

        if (t.daySeconds != null) out.daySeconds = +t.daySeconds;
        if (t.dayLength != null) out.dayLength = +t.dayLength;

        if (t.day != null) out.day = (t.day | 0);
        if (t.timeOfDay != null) out.timeOfDay = +t.timeOfDay;

        if (t.tod01 != null) out.tod01 = +t.tod01;
        if (t.hour != null) out.hour = (t.hour | 0);
        if (t.minute != null) out.minute = (t.minute | 0);
        if (t.second != null) out.second = (t.second | 0);

        return out;
    }

    /**
     * Pure env seed factory. No require(), no IO.
     * Variant A default time model:
     *   - 24h game day (86400 game seconds)
     *   - 1 day passes in 30 real minutes (dayLength=1800)
     *
     * You can override speed with:
     *  - opts.dayLength (seconds per game day)
     *  - opts.time.dayLength
     */
    env(opts) {
        opts = (opts && typeof opts === "object") ? opts : {};

        const name = str(opts.name, "main");
        const start = bool(opts.start, true);
        const runtime = str(opts.runtime ?? opts.profile, this._defaults.runtime);
        const orderStep = numInt(opts.orderStep, this._defaults.orderStep);

        const out = {
            name,
            start,
            mode: normalizeMode(opts.mode),
            schemaVersion: WORLD_SCHEMA_VERSION,
            runtime,
            orderStep,
            systems: [],
            entities: []
        };

        // Default Variant A time, then allow overrides.
        const baseTime = defaultTimeVariantA();

        // Shortcut: env({ dayLength: 900 }) -> 1 day per 15 minutes
        if (opts.dayLength != null) baseTime.dayLength = +opts.dayLength;
        if (opts.daySeconds != null) baseTime.daySeconds = +opts.daySeconds;

        const userTime = normalizeTimeDesc(opts.time);
        const mergedTime = userTime ? deepMerge(baseTime, userTime) : baseTime;

        if (mergedTime && Object.keys(mergedTime).length) out.time = mergedTime;

        return out;
    }

    /**
     * Object builder session from arbitrary seed
     */
    $(seed) {
        return new WorldSession(this, seed || {});
    }

    /**
     * Simple entrypoint:
     * WORLD.boot(envDesc, worldSystems[, overrides])
     */
    boot(desc, systems, overrides) {
        const d = (desc && typeof desc === "object") ? desc : {};
        const sys = Array.isArray(systems) ? systems : [];

        const finalDesc = deepMerge(deepMerge({}, d), overrides || {});
        finalDesc.systems = sys;

        delete finalDesc.entities;
        return this.create(finalDesc);
    }

    normalize(desc) {
        desc = (desc && typeof desc === "object") ? desc : {};

        const name = str(desc.name, this._defaults.name);
        const start = bool(desc.start, this._defaults.start);
        const runtime = str(desc.runtime ?? desc.profile, this._defaults.runtime);

        const orderStep = numInt(desc.orderStep, this._defaults.orderStep);
        const systemsIn = Array.isArray(desc.systems) ? desc.systems : [];

        const systems = [];

        for (let i = 0; i < systemsIn.length; i++) {
            const it = systemsIn[i];

            if (typeof it === "string") {
                const module = it.trim();
                if (!module) throw new Error("[WORLD] systems[" + i + "]: empty module string");

                systems.push(this._mkJsSystem({
                    module,
                    runtime,
                    order: i * orderStep,
                    stableId: stableIdFromModule(module),
                    config: {}
                }));
                continue;
            }

            if (!isObj(it)) throw new Error("[WORLD] systems[" + i + "]: must be string or object");

            if (it.id === "jsSystem" && isObj(it.config)) {
                const cfg = deepMerge({}, it.config);

                const module = str(cfg.module, "");
                if (!module) throw new Error("[WORLD] systems[" + i + "].config.module is required");

                const rt = str(cfg.runtime ?? cfg.profile, runtime);
                cfg.module = module;
                cfg.runtime = rt;

                const order = numInt(it.order, i * orderStep);
                const stableId = (it.stableId != null) ? String(it.stableId) : stableIdFromModule(module);

                systems.push({id: "jsSystem", order, stableId, config: cfg});
                continue;
            }

            if (it.config && isObj(it.config)) {
                const cfg = deepMerge({}, it.config);

                const module = str(cfg.module, "");
                if (!module) throw new Error("[WORLD] systems[" + i + "].config.module is required");

                const rt = str(cfg.runtime ?? cfg.profile, runtime);
                cfg.module = module;
                cfg.runtime = rt;

                const order = numInt(it.order, i * orderStep);
                const stableId = (it.stableId != null) ? String(it.stableId) : stableIdFromModule(module);

                systems.push({id: "jsSystem", order, stableId, config: cfg});
                continue;
            }

            if (it.module != null) {
                const module = str(it.module, "");
                if (!module) throw new Error("[WORLD] systems[" + i + "].module is required");

                const rt = str(it.runtime ?? it.profile, runtime);
                const order = numInt(it.order, i * orderStep);
                const stableId = (it.stableId != null) ? String(it.stableId) : stableIdFromModule(module);

                const cfg = deepMerge({}, it);
                delete cfg.id;
                delete cfg.order;
                delete cfg.stableId;
                delete cfg.module;
                delete cfg.runtime;
                delete cfg.profile;
                delete cfg.config;

                cfg.module = module;
                cfg.runtime = rt;

                systems.push({id: "jsSystem", order, stableId, config: cfg});
                continue;
            }

            throw new Error("[WORLD] systems[" + i + "]: cannot infer jsSystem (missing module/config.module)");
        }

        const time = normalizeTimeDesc(desc.time);

        ensureUniqueStableIds(systems);

        const out = {name, start, systems};
        if (time && Object.keys(time).length) out.time = time;

        return out;
    }

    _mkJsSystem({module, runtime, order, stableId, config}) {
        const cfg = deepMerge({}, config || {});
        cfg.module = str(module, "");
        if (!cfg.module) throw new Error("[WORLD] jsSystem: module is required");
        cfg.runtime = str(runtime, this._defaults.runtime);

        return {
            id: "jsSystem",
            order: numInt(order, 0),
            stableId: (stableId != null) ? String(stableId) : stableIdFromModule(cfg.module),
            config: cfg
        };
    }

    create(desc) {
        const w = subsystem(this.engine, "world");
        req(w && typeof w.create === "function", "[WORLD] engine.world().create(desc) missing");

        const normalized = this.normalize(desc);
        w.create(normalized);
        return normalized;
    }

    builder(seed) {
        const b = new WorldBuilder(this);
        if (seed != null) b.merge(seed);
        return b;
    }
}

module.exports = {WorldApi};