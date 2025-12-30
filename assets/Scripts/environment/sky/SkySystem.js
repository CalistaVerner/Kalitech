// FILE: Scripts/systems/sky/SkySystem.js
"use strict";

const SkyClock = require("./SkyClock.js");
const SunModel = require("./SunModel.js");
const LightRig = require("./LightRig.js");
const SkyBox = require("./SkyBox.js");
//const FogController = require("./FogController.js");

class SkySystem {
    constructor(engine) {
        this.engine = engine;

        this.clock = new SkyClock();
        this.sun = new SunModel();
        this.lights = new LightRig();
        this.skybox = new SkyBox();
        //this.fog = new FogController();

        this.render = engine.render();

        this.staticApplied = false;
        this.wiredEvents = false;

        this._lastCfgRef = null;
        this._unsubs = [];

        this._lastPostRef = null;
        this._lastShadowMapSize = null;

        this.dbgAcc = 0.0;
    }

    init(ctx) {
        try { LOG.info("[sky] init"); } catch (_) {}
        try {
            const c = ctx.get("config");
            const sh = c && c.shadows;
            try { LOG.info("[sky] cfg.shadows exists=" + !!sh + " mapSize=" + (sh ? sh.mapSize : "null")); } catch (_) {}
        } catch (e) {
            try { LOG.warn("[sky] cfg dump failed: " + e); } catch (_) {}
        }

        const cfg = this.readCfg(ctx);
        this.applyConfigIfChanged(cfg);

        this.applyStaticOnce(cfg);

        this.clock.t = this.clock.dayLengthSec * 0.18;
        this.applyFrame(this.clock.time01);

        this.wireEventsOnce();
    }

    update(ctx, tpf) {
        const cfg = this.readCfg(ctx);
        this.applyConfigIfChanged(cfg);

        this.applyRenderCfgIfPresent(cfg);

        const dt = this.getTpf(ctx, tpf);

        if (!this.clock.enabled) {
            this.lights.setEnabled(false);
            return;
        }
        this.lights.setEnabled(true);

        this.clock.step(dt);
        this.applyFrame(this.clock.time01);

        this.dbgAcc += dt;
        if (this.dbgAcc > 2.0) {
            this.dbgAcc = 0.0;
            try {
                LOG.debug(
                    "[sky] phase=" + this.clock.time01.toFixed(3) +
                    " t=" + this.clock.t.toFixed(2) +
                    " dt=" + dt.toFixed(4)
                );
            } catch (_) {}
        }
    }

    destroy() {
        for (let i = 0; i < this._unsubs.length; i++) {
            try { this._unsubs[i](); } catch (_) {}
        }
        this._unsubs.length = 0;
        this.lights.destroy();
    }

    applyFrame(time01) {
        const sunEval = this.sun.evaluate(time01);

        this.lights.update(this.engine, sunEval);
        this.skybox.update(this.render, sunEval);
        //this.fog.update(this.render, sunEval);
    }

    readCfg(ctx) {
        if (!ctx) return null;

        try {
            if (typeof ctx.get === "function") {
                const c = ctx.get("config");
                if (c) return c;
                const cc = ctx.get("cfg");
                if (cc) return cc;
                const sys = ctx.get("system");
                if (sys && sys.config) return sys.config;
            }
        } catch (_) {}

        try { if (ctx.system && ctx.system.config) return ctx.system.config; } catch (_) {}
        if (ctx.config) return ctx.config;
        if (ctx.cfg) return ctx.cfg;

        return null;
    }

    applyConfigIfChanged(cfg) {
        if (!cfg) return;
        if (cfg === this._lastCfgRef) return;
        this._lastCfgRef = cfg;

        this.clock.applyCfg(cfg);
        this.sun.applyCfg(cfg);
        this.lights.applyCfg(cfg);
        this.skybox.applyCfg(cfg);
        //this.fog.applyCfg(cfg);

        this.applyRenderCfgIfPresent(cfg);
    }

    applyStaticOnce(cfg) {
        if (this.staticApplied) return;
        this.staticApplied = true;

        //this.render.ensureScene();

        this.lights.init(this.engine);
        //this.fog.init(this.render);

        const initSun = this.sun.evaluate(0.18);
        this.skybox.update(this.render, initSun);

        this.applyRenderCfgIfPresent(cfg, true);
    }

    applyRenderCfgIfPresent(cfg, forceDefaults) {
       // try { this.render.ensureScene(); } catch (_) {}

        let ms = null;

        const sh = cfg && cfg.shadows ? cfg.shadows : null;
        if (sh) {
            ms = this.clampInt(sh.mapSize, 0, 8192, 2048);
        } else if (forceDefaults) {
            ms = 2048;
        }

        if (ms !== null) {
            if (ms !== this._lastShadowMapSize) {
                this._lastShadowMapSize = ms;
                try {
                    try { LOG.info("[sky] apply shadows mapSize=" + ms); } catch (_) {}
                    this.render.sunShadows(ms);
                } catch (e) {
                    try { LOG.warn("[sky] render.sunShadows failed: " + e); } catch (_) {}
                }
            }
        }

        const pp = cfg && cfg.post ? cfg.post : null;
        if (pp && pp !== this._lastPostRef) {
            this._lastPostRef = pp;
            try { this.render.postCfg(pp); } catch (e) {
                try { LOG.warn("[sky] render.postCfg failed: " + e); } catch (_) {}
            }
        }
    }

    getTpf(ctx, maybeTpf) {
        const p = +maybeTpf;
        if (Number.isFinite(p) && p > 0) return p;

        try {
            const c = +ctx.tpf;
            if (Number.isFinite(c) && c > 0) return c;
        } catch (_) {}

        try {
            const t = +ctx.time.tpf;
            if (Number.isFinite(t) && t > 0) return t;
        } catch (_) {}

        try {
            const v = +this.engine.time().tpf();
            if (Number.isFinite(v) && v > 0) return v;
        } catch (_) {}

        return 1.0 / 60.0;
    }

    clampInt(v, min, max, def) {
        const n = Math.round(Number(v));
        if (!Number.isFinite(n)) return def;
        if (n < min) return min;
        if (n > max) return max;
        return n | 0;
    }

    wireEventsOnce() {
        if (this.wiredEvents) return;
        this.wiredEvents = true;

        try {
            const ev = EVENTS;

            const on = (name, fn) => {
                const ret = ev.on(name, fn);
                if (typeof ret === "function") this._unsubs.push(ret);
                else if (typeof ev.off === "function") this._unsubs.push(() => ev.off(name, fn));
            };

            on("sky:setTime", (p) => {
                if (!p) return;

                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.clock.dayLengthSec = dls;

                const t01 = +p.time01;
                if (Number.isFinite(t01)) this.clock.setTime01(t01);
                else {
                    const ts = +p.timeSec;
                    if (Number.isFinite(ts)) this.clock.setTimeSec(ts);
                }

                this.applyFrame(this.clock.time01);
            });

            on("sky:setSpeed", (p) => {
                if (!p) return;
                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.clock.dayLengthSec = dls;
            });

            on("sky:setEnabled", (p) => {
                if (!p) return;
                if (p.enabled === true) this.clock.enabled = true;
                if (p.enabled === false) this.clock.enabled = false;
            });

            on("render:shadows", (p) => {
                const ms = this.clampInt(p && p.mapSize, 0, 8192, 2048);
                this._lastShadowMapSize = null;
                this.applyRenderCfgIfPresent({ shadows: { mapSize: ms } }, false);
            });

            on("render:post", (p) => {
                if (!p) return;
                this._lastPostRef = null;
                try { this.render.postCfg(p); } catch (e) {
                    try { LOG.warn("[sky] render:post failed: " + e); } catch (_) {}
                }
            });

        } catch (e) {
            try { LOG.warn("[sky] events wiring skipped: " + e); } catch (_) {}
        }
    }
}

module.exports = SkySystem;