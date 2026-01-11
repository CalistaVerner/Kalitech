"use strict";

const SkyMath = require("./SkyMath.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isFn(f) {
    return typeof f === "function";
}

class LightRig {
    constructor() {
        this.render = null;
        this.enabled = true;

        this.minAmbient = 0.20;

        this.ambientDay = { r: 0.25, g: 0.28, b: 0.35, intensity: 0.55 };
        this.ambientNight = { r: 0.10, g: 0.12, b: 0.18, intensity: 0.12 };

        this.shadows = {
            enabled: true,
            mapSizeDay: 4096,
            mapSizeNight: 2048,
            splits: 3,
            lambda: 0.65,
            intensityDay: 0.60,
            intensityNight: 0.35
        };

        this.warmup = {enabled: false, seconds: 0.35};
        this._warmupLeft = 0.0;

        this._lastPrimary = "";
        this._lastShadowKey = "";
        this._lastShadowEnabled = null;

        // debug sampling control
        this._dbgAcc = 0.0;
        this._dbgEvery = 1.0; // seconds
    }

    setEnabled(v) {
        this.enabled = !!v;
        if (!this.enabled) this.disableShadowsHard();
        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][light] setEnabled=" + this.enabled);
        }
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.minAmbient != null) {
            const v = +cfg.minAmbient;
            if (Number.isFinite(v)) this.minAmbient = Math.max(0.0, v);
        }

        if (cfg.ambientDay) {
            const a = cfg.ambientDay;
            if (a.r != null) this.ambientDay.r = +a.r;
            if (a.g != null) this.ambientDay.g = +a.g;
            if (a.b != null) this.ambientDay.b = +a.b;
            if (a.intensity != null) this.ambientDay.intensity = +a.intensity;
        }

        if (cfg.ambientNight) {
            const a = cfg.ambientNight;
            if (a.r != null) this.ambientNight.r = +a.r;
            if (a.g != null) this.ambientNight.g = +a.g;
            if (a.b != null) this.ambientNight.b = +a.b;
            if (a.intensity != null) this.ambientNight.intensity = +a.intensity;
        }

        if (cfg.warmupShadows) {
            const ws = cfg.warmupShadows;
            if (ws.enabled != null) this.warmup.enabled = !!ws.enabled;
            if (ws.seconds != null) {
                const v = +ws.seconds;
                if (Number.isFinite(v) && v >= 0) this.warmup.seconds = v;
            }
        }

        if (cfg.shadows) {
            const sh = cfg.shadows;

            if (sh.enabled != null) this.shadows.enabled = !!sh.enabled;

            if (sh.mapSizeDay != null) this.shadows.mapSizeDay = Math.round(+sh.mapSizeDay);
            if (sh.mapSizeNight != null) this.shadows.mapSizeNight = Math.round(+sh.mapSizeNight);
            if (sh.mapSize != null) this.shadows.mapSizeDay = Math.round(+sh.mapSize);

            if (sh.splits != null) this.shadows.splits = Math.round(+sh.splits);
            if (sh.lambda != null) this.shadows.lambda = +sh.lambda;

            if (sh.intensityDay != null) this.shadows.intensityDay = +sh.intensityDay;
            if (sh.intensityNight != null) this.shadows.intensityNight = +sh.intensityNight;
            if (sh.intensity != null) {
                const v = +sh.intensity;
                if (Number.isFinite(v)) {
                    this.shadows.intensityDay = v;
                    this.shadows.intensityNight = Math.min(v, 0.45);
                }
            }
        }

        if (cfg.debug && cfg.debug.skyEvery != null) {
            const v = +cfg.debug.skyEvery;
            if (Number.isFinite(v) && v >= 0) this._dbgEvery = v;
        }

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][light] applyCfg minAmbient=" + this.minAmbient +
                " shadows=" + JSON.stringify(this.shadows) +
                " warmup=" + JSON.stringify(this.warmup) +
                " dbgEvery=" + this._dbgEvery
            );
        }
    }

    init(engine) {
        req(engine, "[LightRig] engine is required");
        this.render = req(engine.render(), "[LightRig] engine.render() is required");

        req(this.render.ensureScene, "[LightRig] render.ensureScene() is required");
        req(this.render.sunCfg, "[LightRig] render.sunCfg(cfg) is required");
        req(this.render.ambientCfg, "[LightRig] render.ambientCfg(cfg) is required");

        if (!isFn(this.render.sunShadowsCfg) && !isFn(this.render.sunShadows)) {
            throw new Error("[LightRig] render.sunShadowsCfg(cfg) or render.sunShadows(mapSize) is required");
        }

        if (this.render.setPrimaryDirectional != null && !isFn(this.render.setPrimaryDirectional)) {
            throw new Error("[LightRig] render.setPrimaryDirectional must be a function if provided");
        }
        if (this.render.moonCfg != null && !isFn(this.render.moonCfg)) {
            throw new Error("[LightRig] render.moonCfg must be a function if provided");
        }

        this.render.ensureScene();

        this._warmupLeft = (this.warmup.enabled ? Math.max(0, +this.warmup.seconds) : 0);

        this._lastPrimary = "";
        this._lastShadowKey = "";
        this._lastShadowEnabled = null;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][light] init ok warmupLeft=" + this._warmupLeft.toFixed(3));
        }
    }

    update(engine, celEval, tpf) {
        if (!this.enabled) return;

        const dt = Number.isFinite(+tpf) ? +tpf : 0.0;
        if (this._warmupLeft > 0) this._warmupLeft = Math.max(0, this._warmupLeft - dt);

        const primary = req(celEval && celEval.primary, "[LightRig] celEval.primary is required");
        const d = req(celEval && celEval.dayFactor, "[LightRig] celEval.dayFactor is required");

        // primary switch
        if (primary !== this._lastPrimary) {
            this._lastPrimary = primary;
            if (isFn(this.render.setPrimaryDirectional)) this.render.setPrimaryDirectional(primary);
            this._lastShadowKey = "";

            if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                ENGINE.log.debug("[sky][light] primary -> " + primary + " dayFactor=" + d.toFixed(4));
            }
        }

        // sun
        {
            const s = req(celEval.sun, "[LightRig] celEval.sun is required");
            const dir = req(s.rayDir, "[LightRig] celEval.sun.rayDir is required");
            const col = req(s.color, "[LightRig] celEval.sun.color is required");

            this.render.sunCfg({
                dir: [dir.x, dir.y, dir.z],
                color: [col.r, col.g, col.b],
                intensity: +s.intensity
            });
        }

        // moon (optional)
        if (isFn(this.render.moonCfg)) {
            const m = req(celEval.moon, "[LightRig] celEval.moon is required");
            const dir = req(m.rayDir, "[LightRig] celEval.moon.rayDir is required");
            const col = req(m.color, "[LightRig] celEval.moon.color is required");

            this.render.moonCfg({
                dir: [dir.x, dir.y, dir.z],
                color: [col.r, col.g, col.b],
                intensity: (celEval.isNight ? +m.intensity : 0.0)
            });
        }

        // ambient
        const ambR = SkyMath.lerp(this.ambientNight.r, this.ambientDay.r, d);
        const ambG = SkyMath.lerp(this.ambientNight.g, this.ambientDay.g, d);
        const ambB = SkyMath.lerp(this.ambientNight.b, this.ambientDay.b, d);
        const ambI = SkyMath.lerp(this.ambientNight.intensity, this.ambientDay.intensity, d);

        this.render.ambientCfg({
            color: [ambR, ambG, ambB],
            intensity: Math.max(this.minAmbient, ambI)
        });

        // shadows
        this.applyShadows(primary);

        // periodic debug snapshot (controlled)
        this._dbgAcc += dt;
        if (this._dbgEvery > 0 && this._dbgAcc >= this._dbgEvery) {
            this._dbgAcc = 0;
            if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                ENGINE.log.debug(
                    "[sky][light] tick primary=" + primary +
                    " dayFactor=" + d.toFixed(4) +
                    " warmupLeft=" + this._warmupLeft.toFixed(3)
                );
            }
        }
    }

    applyShadows(primary) {
        const warmupDone = this._warmupLeft <= 0;
        const should = warmupDone && this.shadows.enabled;

        if (!should) {
            if (this._lastShadowEnabled !== false) {
                this.disableShadowsHard();
                if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                    ENGINE.log.debug("[sky][shadows] disabled (warmupDone=" + warmupDone + ")");
                }
            }
            return;
        }

        this._lastShadowEnabled = true;

        let mapSize = (primary === "sun") ? this.shadows.mapSizeDay : this.shadows.mapSizeNight;
        let splits = this.shadows.splits;
        let lambda = this.shadows.lambda;
        let intensity = (primary === "sun") ? this.shadows.intensityDay : this.shadows.intensityNight;

        mapSize = this.clampInt(mapSize, 256, 8192, 4096);
        splits = this.clampInt(splits, 1, 4, 3);
        lambda = this.clampNum(lambda, 0.0, 1.0, 0.65);
        intensity = this.clampNum(intensity, 0.0, 1.0, 0.60);

        const key = primary + "|" + mapSize + "|" + splits + "|" + lambda.toFixed(4) + "|" + intensity.toFixed(4);
        if (key === this._lastShadowKey) return;
        this._lastShadowKey = key;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][shadows] APPLY primary=" + primary +
                " mapSize=" + mapSize +
                " splits=" + splits +
                " lambda=" + lambda +
                " intensity=" + intensity
            );
        }

        if (isFn(this.render.sunShadowsCfg)) {
            this.render.sunShadowsCfg({mapSize, splits, lambda, intensity});
        } else {
            this.render.sunShadows(mapSize);
        }
    }

    disableShadowsHard() {
        this._lastShadowEnabled = false;
        this._lastShadowKey = "";

        if (!this.render) return;

        if (isFn(this.render.sunShadowsCfg)) this.render.sunShadowsCfg({
            mapSize: 0,
            splits: 1,
            lambda: 0.65,
            intensity: 0.0
        });
        else this.render.sunShadows(0);
    }

    clampInt(v, min, max, def) {
        const n = Math.round(Number(v));
        if (!Number.isFinite(n)) return def;
        if (n < min) return min;
        if (n > max) return max;
        return n | 0;
    }

    clampNum(v, min, max, def) {
        const n = Number(v);
        if (!Number.isFinite(n)) return def;
        if (n < min) return min;
        if (n > max) return max;
        return n;
    }

    destroy() {
        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][light] destroy");
        }
        this.render = null;
    }
}

module.exports = LightRig;