// FILE: Scripts/systems/sky.js
// Author: Calista Verner
"use strict";

class SkySystem {
    constructor() {
        this.t = 0.0;
        this.enabled = true;

        this.dayLengthSec = 30.0;
        this.azimuthDeg = 35.0;

        this.nightIntensity = 0.02;
        this.dayIntensity = 1.35;
        this.sunsetWarmth = 0.35;

        this.fogBase = { r: 0.70, g: 0.78, b: 0.90 };
        this.fogDistance = 250.0;
        this.fogDensityDay = 1.10;
        this.fogDensityNight = 1.35;

        this.ambientDay = { r: 0.25, g: 0.28, b: 0.35, intensity: 0.55 };
        this.ambientNight = { r: 0.10, g: 0.12, b: 0.18, intensity: 0.12 };

        this.skyboxAsset = "Textures/Sky/skyBox.dds";
        this.shadowsCfg = { mapSize: 2048, splits: 3, lambda: 0.65 };

        this.wiredEvents = false;
        this.staticApplied = false;

        this.lastFogDensity = NaN;
        this.lastFogColorKey = "";
        this.lastFogDistance = NaN;

        this.dbgAcc = 0.0;
    }

    init(ctx) {
        engine.log().info("[sky] init");

        this.applyConfig(this.readCfg(ctx));
        this.applyStaticOnce();

        this.t = this.dayLengthSec * 0.18;
        this.applyAtTime01(this.t / this.dayLengthSec);

        this.wireEventsOnce();
    }

    update(ctx, tpf) {
        this.applyConfig(this.readCfg(ctx));
        if (!this.enabled) return;

        const dt = this.getTpf(ctx, tpf);

        this.t += dt;
        if (this.t > this.dayLengthSec) this.t -= this.dayLengthSec;

        this.applyAtTime01(this.t / this.dayLengthSec);

        this.dbgAcc += dt;
        if (this.dbgAcc > 2.0) {
            this.dbgAcc = 0.0;
            const phase = this.t / this.dayLengthSec;
            engine.log().debug("[sky] phase=" + phase.toFixed(3) + " t=" + this.t.toFixed(2) + " dt=" + dt.toFixed(4));
        }
    }

    destroy() {}

    readCfg(ctx) {
        if (!ctx) return null;
        if (ctx.config) return ctx.config;
        if (ctx.cfg) return ctx.cfg;
        if (ctx.system && ctx.system.config) return ctx.system.config;
        return null;
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

    fogKeyRGB(r, g, b) {
        return Number(r).toFixed(4) + "|" + Number(g).toFixed(4) + "|" + Number(b).toFixed(4);
    }

    dirFromAltAz(alt, az) {
        const ca = Math.cos(alt);
        const x = Math.cos(az) * ca;
        const y = Math.sin(alt);
        const z = Math.sin(az) * ca;
        const len = Math.sqrt(x * x + y * y + z * z) || 1.0;
        return { x: x / len, y: y / len, z: z / len };
    }

    applyConfig(cfg) {
        if (!cfg) return;

        if (cfg.enabled === true) this.enabled = true;
        if (cfg.enabled === false) this.enabled = false;

        const dls = +cfg.dayLengthSec;
        if (Number.isFinite(dls) && dls > 1) this.dayLengthSec = dls;

        const az = +cfg.azimuthDeg;
        if (Number.isFinite(az)) this.azimuthDeg = az;

        const ni = +cfg.nightIntensity;
        if (Number.isFinite(ni)) this.nightIntensity = ni;

        const di = +cfg.dayIntensity;
        if (Number.isFinite(di)) this.dayIntensity = di;

        const sw = +cfg.sunsetWarmth;
        if (Number.isFinite(sw)) this.sunsetWarmth = sw;

        if (cfg.skybox != null) this.skyboxAsset = String(cfg.skybox);

        if (cfg.fog) {
            const fc = cfg.fog.color;
            if (fc) {
                if (fc.r != null) this.fogBase.r = +fc.r;
                if (fc.g != null) this.fogBase.g = +fc.g;
                if (fc.b != null) this.fogBase.b = +fc.b;
            }

            const fd = +cfg.fog.distance;
            if (Number.isFinite(fd)) this.fogDistance = fd;

            const fdd = +cfg.fog.densityDay;
            if (Number.isFinite(fdd)) this.fogDensityDay = fdd;

            const fdn = +cfg.fog.densityNight;
            if (Number.isFinite(fdn)) this.fogDensityNight = fdn;
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

        if (cfg.shadows) {
            const s = cfg.shadows;
            const ms = +s.mapSize;
            const sp = +s.splits;
            const lm = +s.lambda;

            if (Number.isFinite(ms)) this.shadowsCfg.mapSize = ms;
            if (Number.isFinite(sp)) this.shadowsCfg.splits = sp;
            if (Number.isFinite(lm)) this.shadowsCfg.lambda = lm;
        }
    }

    applyStaticOnce() {
        if (this.staticApplied) return;
        this.staticApplied = true;

        render.ensureScene();

        render.ambientCfg({
            color: [this.ambientDay.r, this.ambientDay.g, this.ambientDay.b],
            intensity: this.ambientDay.intensity
        });

        render.sunCfg({
            dir: [-1, -1, -0.3],
            color: [1.0, 0.98, 0.90],
            intensity: 1.2
        });

        if (this.skyboxAsset) render.skyboxCube(this.skyboxAsset);

        const ms = math.clamp((Math.round(Number(this.shadowsCfg.mapSize) || 2048) | 0), 256, 8192);
        const sp = math.clamp((Math.round(Number(this.shadowsCfg.splits) || 3) | 0), 1, 4);
        const lm = Number(this.shadowsCfg.lambda);
        render.sunShadowsCfg({ mapSize: ms, splits: sp, lambda: Number.isFinite(lm) ? lm : 0.65 });

        render.fogCfg({
            color: [this.fogBase.r, this.fogBase.g, this.fogBase.b],
            density: this.fogDensityDay,
            distance: this.fogDistance
        });

        this.lastFogDensity = NaN;
        this.lastFogColorKey = "";
        this.lastFogDistance = NaN;
    }

    applyAtTime01(time01) {
        const phase = math.wrap(time01, 0, 1);

        const alt = Math.sin((phase * Math.PI * 2.0) - Math.PI * 0.5);
        const altitude =
            math.lerp(-0.25, 1.05, (alt + 1.0) * 0.5) * (Math.PI / 2.0) -
            (Math.PI / 2.0) * 0.15;

        const azimuth = (phase * Math.PI * 2.0) + math.degToRad(this.azimuthDeg);
        const d = this.dirFromAltAz(altitude, azimuth);

        const above = math.clamp((d.y + 0.02) / 0.45, 0, 1);
        const dayFactor = math.smoothstep(0.02, 0.25, above);

        const noonBoost = math.smoothstep(0.25, 1.0, above);
        const intensity = math.lerp(this.nightIntensity, this.dayIntensity, dayFactor) * math.lerp(0.55, 1.0, noonBoost);

        const horizonWarm = math.smoothstep(0.0, 0.18, 1.0 - above) * dayFactor;
        const warm = this.sunsetWarmth * horizonWarm;

        const baseR = 1.0, baseG = 0.98, baseB = 0.90;
        const r = math.lerp(baseR, 1.15, warm);
        const g = math.lerp(baseG, 0.92, warm);
        const b = math.lerp(baseB, 0.65, warm);

        render.sunCfg({
            dir: [d.x, d.y, d.z],
            color: [r, g, b],
            intensity: intensity
        });

        const ambR = math.lerp(this.ambientNight.r, this.ambientDay.r, dayFactor);
        const ambG = math.lerp(this.ambientNight.g, this.ambientDay.g, dayFactor);
        const ambB = math.lerp(this.ambientNight.b, this.ambientDay.b, dayFactor);
        const ambI = math.lerp(this.ambientNight.intensity, this.ambientDay.intensity, dayFactor);

        render.ambientCfg({
            color: [ambR, ambG, ambB],
            intensity: ambI
        });

        const fogD = math.lerp(this.fogDensityNight, this.fogDensityDay, dayFactor);
        const key = this.fogKeyRGB(this.fogBase.r, this.fogBase.g, this.fogBase.b);

        if (
            Math.abs(fogD - this.lastFogDensity) > 0.002 ||
            key !== this.lastFogColorKey ||
            Math.abs(this.fogDistance - this.lastFogDistance) > 0.1
        ) {
            render.fogCfg({
                color: [this.fogBase.r, this.fogBase.g, this.fogBase.b],
                density: fogD,
                distance: this.fogDistance
            });
            this.lastFogDensity = fogD;
            this.lastFogColorKey = key;
            this.lastFogDistance = this.fogDistance;
        }
    }

    wireEventsOnce() {
        if (this.wiredEvents) return;
        this.wiredEvents = true;

        try {
            const ev = engine.events();

            ev.on("sky:setTime", (p) => {
                if (!p) return;

                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.dayLengthSec = dls;

                const t01 = +p.time01;
                if (Number.isFinite(t01)) {
                    this.t = this.dayLengthSec * math.wrap(t01, 0, 1);
                } else {
                    const ts = +p.timeSec;
                    if (Number.isFinite(ts)) this.t = ts;
                }

                this.applyAtTime01(this.t / this.dayLengthSec);
            });

            ev.on("sky:setSpeed", (p) => {
                if (!p) return;
                const dls = +p.dayLengthSec;
                if (Number.isFinite(dls) && dls > 1) this.dayLengthSec = dls;
            });

            ev.on("sky:setEnabled", (p) => {
                if (!p) return;
                if (p.enabled === true) this.enabled = true;
                if (p.enabled === false) this.enabled = false;
            });
        } catch (e) {
            engine.log().warn("[sky] events wiring skipped: " + e);
        }
    }
}

const SYS = new SkySystem();

module.exports.init = function (ctx) { SYS.init(ctx); };
module.exports.update = function (ctx, tpf) { SYS.update(ctx, tpf); };
module.exports.destroy = function () { SYS.destroy(); };