// FILE: Scripts/systems/sky/SkySystem.js
"use strict";

const SkyClock = require("./SkyClock.js");
const SunModel = require("./SunModel.js");
const LightRig = require("./LightRig.js");
const SkyBox = require("./SkyBox.js");
const FogController = require("./FogController.js");

class SkySystem {
    constructor() {
        this.clock = new SkyClock();
        this.sun = new SunModel();
        this.lights = new LightRig();
        this.skybox = new SkyBox();
        this.fog = new FogController();

        this.staticApplied = false;
        this.wiredEvents = false;

        this.dbgAcc = 0.0;
    }

    init(ctx) {
        engine.log().info("[sky] init");

        const cfg = this.readCfg(ctx);
        this.applyConfig(cfg);

        this.applyStaticOnce(cfg);

        this.clock.t = this.clock.dayLengthSec * 0.18;
        this.applyFrame(this.clock.time01);

        this.wireEventsOnce();
    }

    update(ctx, tpf) {
        const cfg = this.readCfg(ctx);
        this.applyConfig(cfg);

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
            engine.log().debug("[sky] phase=" + this.clock.time01.toFixed(3) + " t=" + this.clock.t.toFixed(2) + " dt=" + dt.toFixed(4));
        }
    }

    destroy() {
        this.lights.destroy();
    }

    applyFrame(time01) {
        const sunEval = this.sun.evaluate(time01);
        this.lights.update(engine, sunEval);
        this.skybox.update(render, sunEval);
        this.fog.update(render, sunEval);
    }

    readCfg(ctx) {
        if (!ctx) return null;
        if (ctx.config) return ctx.config;
        if (ctx.cfg) return ctx.cfg;
        if (ctx.system && ctx.system.config) return ctx.system.config;
        return null;
    }

    applyConfig(cfg) {
        if (!cfg) return;
        this.clock.applyCfg(cfg);
        this.sun.applyCfg(cfg);
        this.lights.applyCfg(cfg);
        this.skybox.applyCfg(cfg);
        this.fog.applyCfg(cfg);
    }

    applyStaticOnce(cfg) {
        if (this.staticApplied) return;
        this.staticApplied = true;

        render.ensureScene();

        this.lights.init(engine);
        this.fog.init(render);

        const initSun = this.sun.evaluate(0.18);
        this.skybox.update(render, initSun);

        const sh = (cfg && cfg.shadows) ? cfg.shadows : null;
        const ms = math.clamp((Math.round(Number(sh && sh.mapSize) || 2048) | 0), 256, 8192);
        const sp = math.clamp((Math.round(Number(sh && sh.splits) || 3) | 0), 1, 4);
        const lm = Number(sh && sh.lambda);
        render.sunShadowsCfg({ mapSize: ms, splits: sp, lambda: Number.isFinite(lm) ? lm : 0.65 });
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
            const v = +engine.time().tpf();
            if (Number.isFinite(v) && v > 0) return v;
        } catch (_) {}

        return 1.0 / 60.0;
    }

    wireEventsOnce() {
        if (this.wiredEvents) return;
        this.wiredEvents = true;

        try {
            const ev = engine.events();

            ev.on("sky:setTime", (p) => {
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

            ev.on("sky:setSpeed", (p) => {
                if (!p) return;
                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.clock.dayLengthSec = dls;
            });

            ev.on("sky:setEnabled", (p) => {
                if (!p) return;
                if (p.enabled === true) this.clock.enabled = true;
                if (p.enabled === false) this.clock.enabled = false;
            });
        } catch (e) {
            engine.log().warn("[sky] events wiring skipped: " + e);
        }
    }
}

module.exports = SkySystem;