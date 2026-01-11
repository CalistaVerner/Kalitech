// FILE: Scripts/environment/sky/SkyDome.js
"use strict";

const SkyMath = require("./SkyMath.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class SkyDome {
    constructor() {
        this.zenith = {r: 0.08, g: 0.14, b: 0.30};
        this.horizon = {r: 0.65, g: 0.72, b: 0.82};

        this.hazeDay = 0.60;
        this.hazeNight = 0.28;

        this.sunDisk = 45.0;
        this.moonDisk = 120.0;

        this.exposureDay = 1.10;
        this.exposureNight = 0.55;

        // textures (SkyBox-style switching)
        this.defaultAsset = null;
        this.dayAsset = null;
        this.sunsetAsset = null;
        this.nightAsset = null;

        // keep procedural contribution (avoid flat look)
        this.texBlendDay = 0.55;
        this.texBlendNight = 0.35;

        // HDR scale: 10.0 is way too hot for most HDRIs
        this.texExposureDay = 1.80;
        this.texExposureNight = 0.65;

        this.lastAsset = "";
        this._lastKey = "";
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.skyDomeTex != null) this.defaultAsset = String(cfg.skyDomeTex);
        if (cfg.skyDomeTexDay != null) this.dayAsset = String(cfg.skyDomeTexDay);
        if (cfg.skyDomeTexSunset != null) this.sunsetAsset = String(cfg.skyDomeTexSunset);
        if (cfg.skyDomeTexNight != null) this.nightAsset = String(cfg.skyDomeTexNight);

        if (cfg.skyDome) {
            const d = cfg.skyDome;

            if (d.zenithColor) {
                const c = d.zenithColor;
                if (c.r != null) this.zenith.r = +c.r;
                if (c.g != null) this.zenith.g = +c.g;
                if (c.b != null) this.zenith.b = +c.b;
            }
            if (d.horizonColor) {
                const c = d.horizonColor;
                if (c.r != null) this.horizon.r = +c.r;
                if (c.g != null) this.horizon.g = +c.g;
                if (c.b != null) this.horizon.b = +c.b;
            }

            if (d.hazeDay != null) this.hazeDay = +d.hazeDay;
            if (d.hazeNight != null) this.hazeNight = +d.hazeNight;

            if (d.sunDisk != null) this.sunDisk = +d.sunDisk;
            if (d.moonDisk != null) this.moonDisk = +d.moonDisk;

            if (d.exposureDay != null) this.exposureDay = +d.exposureDay;
            if (d.exposureNight != null) this.exposureNight = +d.exposureNight;

            if (d.texBlendDay != null) this.texBlendDay = +d.texBlendDay;
            if (d.texBlendNight != null) this.texBlendNight = +d.texBlendNight;

            if (d.texExposureDay != null) this.texExposureDay = +d.texExposureDay;
            if (d.texExposureNight != null) this.texExposureNight = +d.texExposureNight;
        }

        const log = ENGINE && ENGINE.log ? ENGINE.log : null;
        if (log && log.debug) {
            log.debug(
                "[sky][skydome] applyCfg" +
                (this.defaultAsset ? (" default='" + this.defaultAsset + "'") : " default=<null>") +
                (this.dayAsset ? (" day='" + this.dayAsset + "'") : "") +
                (this.sunsetAsset ? (" sunset='" + this.sunsetAsset + "'") : "") +
                (this.nightAsset ? (" night='" + this.nightAsset + "'") : "") +
                " texBlendDay=" + Number(this.texBlendDay).toFixed(3) +
                " texBlendNight=" + Number(this.texBlendNight).toFixed(3) +
                " texExposureDay=" + Number(this.texExposureDay).toFixed(3) +
                " texExposureNight=" + Number(this.texExposureNight).toFixed(3)
            );
        }
    }

    pickAsset(dayFactor) {
        const d = (typeof dayFactor === "number") ? dayFactor : 1.0;

        if (!this.dayAsset && !this.sunsetAsset && !this.nightAsset) return this.defaultAsset;

        if (d < 0.10) return this.nightAsset || this.defaultAsset;
        if (d < 0.35) return this.sunsetAsset || this.dayAsset || this.defaultAsset;
        return this.dayAsset || this.defaultAsset;
    }

    update(render, celEval) {
        req(render, "[skydome] render is required");
        req(render.skyDomeCfg, "[skydome] render.skyDomeCfg(cfg) is required");
        req(render.skyDomeTex, "[skydome] render.skyDomeTex(asset) is required");

        const df = req(celEval && celEval.dayFactor, "[skydome] celEval.dayFactor is required");
        const s = req(celEval && celEval.sun, "[skydome] celEval.sun is required");
        const m = req(celEval && celEval.moon, "[skydome] celEval.moon is required");

        const asset = this.pickAsset(df);
        if (asset && asset !== this.lastAsset) {
            const log = ENGINE && ENGINE.log ? ENGINE.log : null;
            if (log && log.debug) log.debug("[sky][skydome] switch tex='" + asset + "' dayFactor=" + Number(df).toFixed(4));
            render.skyDomeTex(asset);
            this.lastAsset = asset;
        }

        const haze = SkyMath.lerp(this.hazeNight, this.hazeDay, df);
        const exposure = SkyMath.lerp(this.exposureNight, this.exposureDay, df);

        // clamp “artist knobs” to sane ranges (no silent fallback: deterministic clamp)
        const texBlend = Math.max(0, Math.min(1, SkyMath.lerp(this.texBlendNight, this.texBlendDay, df)));
        const texExposure = Math.max(0.001, SkyMath.lerp(this.texExposureNight, this.texExposureDay, df));

        const key =
            df.toFixed(4) + "|" +
            haze.toFixed(4) + "|" +
            exposure.toFixed(4) + "|" +
            texBlend.toFixed(4) + "|" +
            texExposure.toFixed(4) + "|" +
            this.sunDisk.toFixed(2) + "|" +
            this.moonDisk.toFixed(2) + "|" +
            s.rayDir.x.toFixed(4) + "|" + s.rayDir.y.toFixed(4) + "|" + s.rayDir.z.toFixed(4) + "|" +
            m.rayDir.x.toFixed(4) + "|" + m.rayDir.y.toFixed(4) + "|" + m.rayDir.z.toFixed(4) + "|" +
            s.color.r.toFixed(4) + "|" + s.color.g.toFixed(4) + "|" + s.color.b.toFixed(4) + "|" + (+s.intensity).toFixed(4) + "|" +
            m.color.r.toFixed(4) + "|" + m.color.g.toFixed(4) + "|" + m.color.b.toFixed(4) + "|" + (+m.intensity).toFixed(4) + "|" +
            this.zenith.r.toFixed(4) + "|" + this.zenith.g.toFixed(4) + "|" + this.zenith.b.toFixed(4) + "|" +
            this.horizon.r.toFixed(4) + "|" + this.horizon.g.toFixed(4) + "|" + this.horizon.b.toFixed(4);

        if (key === this._lastKey) return;
        this._lastKey = key;

        render.skyDomeCfg({
            sunDir: [s.rayDir.x, s.rayDir.y, s.rayDir.z],
            moonDir: [m.rayDir.x, m.rayDir.y, m.rayDir.z],

            sunColor: [s.color.r, s.color.g, s.color.b],
            sunIntensity: +s.intensity,

            moonColor: [m.color.r, m.color.g, m.color.b],
            moonIntensity: +m.intensity,

            zenithColor: [this.zenith.r, this.zenith.g, this.zenith.b],
            horizonColor: [this.horizon.r, this.horizon.g, this.horizon.b],

            haze,
            sunDisk: this.sunDisk,
            moonDisk: this.moonDisk,
            exposure,

            texBlend,
            texExposure
        });
    }
}

module.exports = SkyDome;