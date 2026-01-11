// FILE: Scripts/environment/sky/SkyDome.js
"use strict";

const SkyMath = require("./SkyMath.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isFn(f) {
    return typeof f === "function";
}

class SkyDome {
    constructor() {
        // Artistic baseline (procedural fallback)
        this.zenith = {r: 0.08, g: 0.14, b: 0.30};
        this.horizon = {r: 0.65, g: 0.72, b: 0.82};

        this.hazeDay = 0.55;
        this.hazeNight = 0.30;

        this.sunDisk = 45.0;
        this.moonDisk = 120.0;

        this.exposureDay = 1.15;
        this.exposureNight = 0.65;

        // --- Texture like SkyBox ---
        this.defaultAsset = null;      // skyDomeTex
        this.dayAsset = null;          // skyDomeTexDay
        this.sunsetAsset = null;       // skyDomeTexSunset
        this.nightAsset = null;        // skyDomeTexNight

        // how much texture overrides procedural (0..1)
        this.texBlendDay = 1.0;
        this.texBlendNight = 1.0;

        this.lastAsset = "";

        // cfg -> render.skyDomeCfg cache
        this._lastKey = "";
    }

    applyCfg(cfg) {
        if (!cfg) return;

        // --- procedural ---
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

            // texture blending
            if (d.texBlendDay != null) this.texBlendDay = +d.texBlendDay;
            if (d.texBlendNight != null) this.texBlendNight = +d.texBlendNight;
        }

        // --- texture assets (SkyBox-style naming, but with SkyDome prefix) ---
        if (cfg.skyDomeTex != null) this.defaultAsset = String(cfg.skyDomeTex);
        if (cfg.skyDomeTexDay != null) this.dayAsset = String(cfg.skyDomeTexDay);
        if (cfg.skyDomeTexSunset != null) this.sunsetAsset = String(cfg.skyDomeTexSunset);
        if (cfg.skyDomeTexNight != null) this.nightAsset = String(cfg.skyDomeTexNight);

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][skydome] applyCfg" +
                (this.defaultAsset ? (" default='" + this.defaultAsset + "'") : " default=<null>") +
                (this.dayAsset ? (" day='" + this.dayAsset + "'") : "") +
                (this.sunsetAsset ? (" sunset='" + this.sunsetAsset + "'") : "") +
                (this.nightAsset ? (" night='" + this.nightAsset + "'") : "") +
                " texBlendDay=" + Number(this.texBlendDay).toFixed(3) +
                " texBlendNight=" + Number(this.texBlendNight).toFixed(3)
            );
        }
    }

    pickAsset(dayFactor) {
        const d = (typeof dayFactor === "number") ? dayFactor : 1.0;

        // If no special assets configured -> use default (may be null)
        if (!this.dayAsset && !this.sunsetAsset && !this.nightAsset) {
            return this.defaultAsset;
        }

        // Same thresholds as SkyBox
        if (d < 0.10) return this.nightAsset || this.defaultAsset;
        if (d < 0.35) return this.sunsetAsset || this.dayAsset || this.defaultAsset;
        return this.dayAsset || this.defaultAsset;
    }

    update(render, celEval) {
        req(render, "[skydome] render is required");
        req(render.skyDomeCfg, "[skydome] render.skyDomeCfg(cfg) is required");

        const df = req(celEval && celEval.dayFactor, "[skydome] celEval.dayFactor is required");
        const s = req(celEval && celEval.sun, "[skydome] celEval.sun is required");
        const m = req(celEval && celEval.moon, "[skydome] celEval.moon is required");

        // --- texture switch (SkyBox style) ---
        const asset = this.pickAsset(df);
        if (asset) {
            if (!isFn(render.skyDomeTex)) {
                throw new Error("[skydome] render.skyDomeTex(asset) is required when skyDomeTex* configured");
            }
            if (asset !== this.lastAsset) {
                if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                    ENGINE.log.debug(
                        "[sky][skydome] switch tex='" + asset + "' dayFactor=" + Number(df).toFixed(4)
                    );
                }
                render.skyDomeTex(asset);
                this.lastAsset = asset;
            }
        }

        // --- procedural uniforms (plus TexBlend) ---
        const haze = SkyMath.lerp(this.hazeNight, this.hazeDay, df);
        const exposure = SkyMath.lerp(this.exposureNight, this.exposureDay, df);
        const texBlend = SkyMath.lerp(this.texBlendNight, this.texBlendDay, df);

        const key =
            df.toFixed(4) + "|" +
            haze.toFixed(4) + "|" +
            exposure.toFixed(4) + "|" +
            texBlend.toFixed(4) + "|" +
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

            // NEW
            texBlend
        });
    }
}

module.exports = SkyDome;