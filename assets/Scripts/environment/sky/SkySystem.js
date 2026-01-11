"use strict";

const SkyClock = require("./SkyClock.js");
const CelestialModel = require("./CelestialModel.js");
const LightRig = require("./LightRig.js");
const SkyBox = require("./SkyBox.js");
const FogController = require("./FogController.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isFn(f) {
    return typeof f === "function";
}

function dbg(log, s) {
    if (log && log.debug) log.debug(s);
}

function mapGet(m, key) {
    if (!m) return null;

    // Map-like: has/get
    if (typeof m === "object" && typeof m.has === "function" && typeof m.get === "function") {
        if (m.has(key)) return m.get(key);
        return null;
    }

    // Plain object
    if (typeof m === "object") {
        if (Object.prototype.hasOwnProperty.call(m, key)) return m[key];
        return null;
    }

    return null;
}

function tryGet(ctx, key) {
    if (!ctx) return null;

    // 1) ctx.get("x")
    if (typeof ctx.get === "function") {
        const v = ctx.get(key);
        if (v != null) return v;
    }

    // 2) ctx.state()  (0-arity!) -> stateObj, then get from it
    if (typeof ctx.state === "function") {
        const st = ctx.state(); // IMPORTANT: no args
        const v = mapGet(st, key);
        if (v != null) return v;
    }

    // 3) ctx.stateDomain.get("x")
    if (ctx.stateDomain) {
        const v = mapGet(ctx.stateDomain, key);
        if (v != null) return v;
    }

    return null;
}


class SkySystem {
    constructor(engineApi) {
        this.engine = req(engineApi, "[sky] engineApi is required");
        this.render = null;

        this.clock = new SkyClock();
        this.celestial = new CelestialModel();
        this.lights = new LightRig();
        this.skybox = new SkyBox();
        this.fog = new FogController();

        this._cfgRef = null;
        this._cfgPath = "INIT";

        this._didInitTime = false;

        this._eventsWired = false;
        this._unsubs = [];

        this._enabled = true;

        // debug cadence
        this._dbgAcc = 0.0;
        this._dbgEvery = 1.0;
        this._dbgLastPrimary = null;

        // one-shot diag
        this._diagCfgPrinted = false;
    }

    init(ctx) {
        const log = ENGINE && ENGINE.log ? ENGINE.log : null;

        (ENGINE.log || console).info("[sky] init");

        if (!isFn(this.engine.render)) {
            throw new Error("[sky] engineApi.render() is required (pass ctx.engine.api(), not ctx.engine)");
        }

        this.render = req(this.engine.render(), "[sky] engine.render() returned null");

        dbg(log, "[sky] ctx keys = " + Object.keys(ctx || {}).join(","));
        dbg(log, "[sky] ctx has config=" + !!(ctx && (ctx.config || ctx.cfg || ctx.params || (ctx.system && (ctx.system.config || ctx.system.cfg)))));
        dbg(log, "[sky] ctx has get=" + !!(ctx && isFn(ctx.get)) + " has=" + !!(ctx && isFn(ctx.has)) + " state=" + !!(ctx && isFn(ctx.state)));
        dbg(log, "[sky] ctx has stateDomain=" + !!(ctx && ctx.stateDomain) + " perfDomain=" + !!(ctx && ctx.perfDomain));

        const cfg = this.readCfg(ctx);
        this.applyCfg(cfg);

        this.assertRenderContract();

        this.lights.init(this.engine);
        this.fog.init(this.render);

        if (!this._didInitTime) {
            if (!cfg || cfg.startTime01 == null) this.clock.setTime01(0.25);
            this._didInitTime = true;
        }

        this.applyFrame(0);
        this.wireEventsOnce();
    }

    update(ctx, tpf) {
        const cfg = this.readCfg(ctx);
        this.applyCfg(cfg);

        const dt = this.getDt(tpf);

        if (!this._enabled || !this.clock.enabled) return;

        this.clock.step(dt);
        this.applyFrame(dt);

        // heartbeat
        if (cfg && cfg.debug && cfg.debug.skyEvery != null) {
            const v = +cfg.debug.skyEvery;
            if (Number.isFinite(v) && v >= 0) this._dbgEvery = v;
        }

        this._dbgAcc += dt;
        if (this._dbgEvery > 0 && this._dbgAcc >= this._dbgEvery) {
            this._dbgAcc = 0;

            const cel = this.celestial.evaluate(this.clock.time01);
            const log = ENGINE && ENGINE.log ? ENGINE.log : null;
            dbg(log,
                "[sky] tick time01=" + cel.time01.toFixed(4) +
                " dayFactor=" + cel.dayFactor.toFixed(4) +
                " primary=" + cel.primary +
                " cfgPath=" + this._cfgPath
            );
        }
    }

    destroy() {
        for (let i = 0; i < this._unsubs.length; i++) this._unsubs[i]();
        this._unsubs.length = 0;

        this.lights.destroy();
        this.fog.destroy();

        (ENGINE.log || console).info("[sky] destroy");
    }

    applyFrame(dt) {
        const cel = this.celestial.evaluate(this.clock.time01);

        if (cel.primary !== this._dbgLastPrimary) {
            this._dbgLastPrimary = cel.primary;
            const log = ENGINE && ENGINE.log ? ENGINE.log : null;
            dbg(log,
                "[sky] PRIMARY -> " + cel.primary +
                " time01=" + cel.time01.toFixed(4) +
                " dayFactor=" + cel.dayFactor.toFixed(4)
            );
        }

        this.lights.update(this.engine, cel, dt);
        this.skybox.update(this.render, cel.dayFactor);
        this.fog.update(this.render, cel);
    }

    applyCfg(cfg) {
        const log = ENGINE && ENGINE.log ? ENGINE.log : null;

        if (!cfg) {
            dbg(log, "[sky][cfg] not found (using defaults) path=" + this._cfgPath);
            return;
        }

        if (cfg === this._cfgRef) return;
        this._cfgRef = cfg;

        this.clock.applyCfg(cfg);
        this.celestial.applyCfg(cfg);
        this.lights.applyCfg(cfg);
        this.skybox.applyCfg(cfg);
        this.fog.applyCfg(cfg);

        dbg(log, "[sky][cfg] applied ref-change path=" + this._cfgPath);
    }

    /**
     * IMPORTANT:
     * Your ctx shows domains: stateDomain + ctx.state()/get()/has().
     * Config is not in ctx.config — so we extract from those domains.
     */
    readCfg(ctx) {
        const log = ENGINE && ENGINE.log ? ENGINE.log : null;

        if (!ctx) {
            this._cfgPath = "ctx:null";
            return null;
        }

        // 1) obvious fields
        if (ctx.config) {
            this._cfgPath = "ctx.config";
            return ctx.config;
        }
        if (ctx.cfg) {
            this._cfgPath = "ctx.cfg";
            return ctx.cfg;
        }
        if (ctx.params) {
            this._cfgPath = "ctx.params";
            return ctx.params;
        }
        if (ctx.settings) {
            this._cfgPath = "ctx.settings";
            return ctx.settings;
        }

        // 2) system wrapper
        if (ctx.system) {
            if (ctx.system.config) {
                this._cfgPath = "ctx.system.config";
                return ctx.system.config;
            }
            if (ctx.system.cfg) {
                this._cfgPath = "ctx.system.cfg";
                return ctx.system.cfg;
            }
        }

        // 3) domains / getters (your actual case)
        // try common keys (order: strict)
        const keys = [
            "config",
            "cfg",
            "systemConfig",
            "systemCfg",
            "sysConfig",
            "settings",
            "params",
            "moduleConfig"
        ];

        for (let i = 0; i < keys.length; i++) {
            const k = keys[i];
            const v = tryGet(ctx, k);
            if (v != null) {
                this._cfgPath = "domain:" + k;
                if (!this._diagCfgPrinted) {
                    this._diagCfgPrinted = true;
                    dbg(log, "[sky][cfg] FOUND via " + this._cfgPath);
                    dbg(log, "[sky][cfg] cfg keys=" + Object.keys(v).join(","));
                }
                return v;
            }
        }

        // 4) one-shot deep diag: list keys that exist in stateDomain if possible
        if (!this._diagCfgPrinted) {
            this._diagCfgPrinted = true;

            dbg(log, "[sky][cfg] NOT FOUND. diag:");
            if (ctx.stateDomain && typeof ctx.stateDomain === "object") {
                dbg(log, "[sky][cfg] stateDomain keys=" + Object.keys(ctx.stateDomain).join(","));
            }
            // if ctx.has exists, probe what it claims to have
            if (isFn(ctx.has)) {
                const probe = ["config", "cfg", "systemConfig", "systemCfg", "settings", "params", "moduleConfig"];
                for (let i = 0; i < probe.length; i++) {
                    const k = probe[i];
                    dbg(log, "[sky][cfg] ctx.has('" + k + "')=" + !!ctx.has(k));
                }
            }
        }

        this._cfgPath = "NOT_FOUND";
        return null;
    }

    getDt(tpf) {
        const p = Number(tpf);
        if (Number.isFinite(p) && p > 0) return p;
        return 1.0 / 60.0;
    }

    assertRenderContract() {
        const r = req(this.render, "[sky] render is required");

        req(r.ensureScene, "[sky] render.ensureScene() is required");
        req(r.sunCfg, "[sky] render.sunCfg(cfg) is required");
        req(r.ambientCfg, "[sky] render.ambientCfg(cfg) is required");
        req(r.fogCfg, "[sky] render.fogCfg(cfg) is required");
        req(r.skyboxCube, "[sky] render.skyboxCube(asset) is required");

        if (!isFn(r.sunShadowsCfg) && !isFn(r.sunShadows)) {
            throw new Error("[sky] render.sunShadowsCfg(cfg) or render.sunShadows(mapSize) is required");
        }

        if (r.setPrimaryDirectional != null && !isFn(r.setPrimaryDirectional)) {
            throw new Error("[sky] render.setPrimaryDirectional must be a function if provided");
        }

        if (r.moonCfg != null && !isFn(r.moonCfg)) {
            throw new Error("[sky] render.moonCfg must be a function if provided");
        }

        r.ensureScene();

        const log = ENGINE && ENGINE.log ? ENGINE.log : null;
        dbg(log, "[sky] render contract OK");
    }

    wireEventsOnce() {
        if (this._eventsWired) return;
        this._eventsWired = true;

        const log = ENGINE && ENGINE.log ? ENGINE.log : null;

        const ev = (typeof globalThis !== "undefined") ? globalThis.EVENTS : undefined;
        if (!ev) {
            dbg(log, "[sky] EVENTS not present");
            return;
        }
        if (!isFn(ev.on)) throw new Error("[sky] EVENTS.on(name, fn) is required");

        const on = (name, fn) => {
            const ret = ev.on(name, fn);
            if (isFn(ret)) this._unsubs.push(ret);
            else if (isFn(ev.off)) this._unsubs.push(() => ev.off(name, fn));
        };

        on("sky:setTime", (p) => {
            if (!p) return;

            if (p.time01 != null) this.clock.setTime01(+p.time01);
            else if (p.timeSec != null) this.clock.setTimeSec(+p.timeSec);

            if (p.dayLengthSec != null) {
                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.clock.dayLengthSec = dls;
            }

            dbg(log, "[sky][event] sky:setTime " + JSON.stringify(p));
            this.applyFrame(0);
        });

        on("sky:setEnabled", (p) => {
            if (!p) return;
            if (p.enabled === true) this.setEnabled(true);
            if (p.enabled === false) this.setEnabled(false);
            dbg(log, "[sky][event] sky:setEnabled enabled=" + this._enabled);
        });

        on("sky:setSpeed", (p) => {
            if (!p) return;
            const dls = +p.dayLengthSec;
            if (Number.isFinite(dls) && dls > 1) this.clock.dayLengthSec = dls;
            dbg(log, "[sky][event] sky:setSpeed dayLengthSec=" + this.clock.dayLengthSec);
        });

        dbg(log, "[sky] EVENTS wired");
    }

    setEnabled(v) {
        this._enabled = !!v;
        this.lights.setEnabled(this._enabled);

        const log = ENGINE && ENGINE.log ? ENGINE.log : null;
        dbg(log, "[sky] setEnabled=" + this._enabled);
    }
}

module.exports = SkySystem;
