// FILE: resources/kalitech/builtin/helpers/world/WorldApi.js
"use strict";

const {req, deepMerge, isObj, subsystem, numInt, str, bool} = require("./WorldUtil.js");
const {WorldBuilder} = require("./WorldBuilder.js");
const {WorldSession} = require("./WorldSession.js");

const WORLD_SCHEMA_VERSION = 1;

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

    if (time.worldTime != null) out.worldTime = +time.worldTime;
    if (time.timeRate != null) out.timeRate = +time.timeRate;
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
     * Reads current world time snapshot from engine (JSON object).
     * Returns null if world is not running / not available.
     *
     * Expected fields:
     *  - worldTime: number
     *  - timeRate: number
     *  - paused: boolean
     *  - frameIndex: number
     *  - tickIndex: number
     *  - realDt: number
     *  - simDt: number
     *  - stepDt: number
     *  - interpAlpha: number
     *  - fixedStep?: number
     *  - maxDelta?: number
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
        if (t.frameIndex != null) out.frameIndex = +t.frameIndex;
        if (t.tickIndex != null) out.tickIndex = +t.tickIndex;
        if (t.realDt != null) out.realDt = +t.realDt;
        if (t.simDt != null) out.simDt = +t.simDt;
        if (t.stepDt != null) out.stepDt = +t.stepDt;
        if (t.interpAlpha != null) out.interpAlpha = +t.interpAlpha;
        if (t.fixedStep != null) out.fixedStep = +t.fixedStep;
        if (t.maxDelta != null) out.maxDelta = +t.maxDelta;

        return out;
    }

    /**
     * Pure env seed factory. No require(), no IO.
     * You can pass it into WORLD.$(seed) and continue chaining.
     *
     * Supported:
     *  - mode: "game" | "editor" | ...
     *  - name: string
     *  - start: boolean
     *  - runtime/profile: string
     *  - orderStep: int
     *  - time: {worldTime,timeRate,paused,fixedStep,maxDelta}
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

        const time = normalizeTimeDesc(opts.time);
        if (time && Object.keys(time).length) out.time = time;

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

        delete finalDesc.entities; // world-only
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

    // kept for compatibility (if somebody uses WorldBuilder directly)
    builder(seed) {
        const b = new WorldBuilder(this);
        if (seed != null) b.merge(seed);
        return b;
    }
}

module.exports = {WorldApi};
