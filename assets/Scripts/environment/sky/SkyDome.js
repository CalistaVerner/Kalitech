// FILE: Scripts/systems/sky/SkyDome.js
"use strict";

const SkyMath = require("./SkyMath.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function clamp01(x) {
    return Math.max(0.0, Math.min(1.0, x));
}

function lerpColor(a, b, t) {
    return {
        r: SkyMath.lerp(a.r, b.r, t),
        g: SkyMath.lerp(a.g, b.g, t),
        b: SkyMath.lerp(a.b, b.b, t)
    };
}

class SkyDome {
    constructor() {
        this.zenithDay = {r: 0.08, g: 0.14, b: 0.30};
        this.horizonDay = {r: 0.65, g: 0.72, b: 0.82};

        this.zenithNight = {r: 0.01, g: 0.02, b: 0.06};
        this.horizonNight = {r: 0.03, g: 0.04, b: 0.08};

        this.hazeDay = 0.60;
        this.hazeNight = 0.28;

        this.sunDisk = 45.0;
        this.moonDisk = 120.0;

        this.exposureDay = 1.10;
        this.exposureNight = 0.55;

        this.twilightWarmth = 0.22;
        this.twilightHazeBoost = 0.10;
        this.twilightExposureBoost = 0.10;

        this.texA = null;
        this.texB = null;

        this.texBlendDay = 0.55;
        this.texBlendNight = 0.35;

        this.texExposureDay = 1.80;
        this.texExposureNight = 0.65;

        this.crossfade = {
            enabled: true,
            start: 0.10,
            end: 0.35
        };

        this._lastTexA = "";
        this._lastTexB = "";
        this._lastKey = "";
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.skyDomeTexA != null) this.texA = String(cfg.skyDomeTexA);
        if (cfg.skyDomeTexB != null) this.texB = String(cfg.skyDomeTexB);

        if (cfg.skyDomeTexDay != null) this.texA = String(cfg.skyDomeTexDay);
        if (cfg.skyDomeTexNight != null) this.texB = String(cfg.skyDomeTexNight);

        if (cfg.skyDome) {
            const d = cfg.skyDome;

            if (d.zenithColor) {
                const c = d.zenithColor;
                if (c.r != null) this.zenithDay.r = +c.r;
                if (c.g != null) this.zenithDay.g = +c.g;
                if (c.b != null) this.zenithDay.b = +c.b;
            }
            if (d.horizonColor) {
                const c = d.horizonColor;
                if (c.r != null) this.horizonDay.r = +c.r;
                if (c.g != null) this.horizonDay.g = +c.g;
                if (c.b != null) this.horizonDay.b = +c.b;
            }

            if (d.zenithDay) {
                const c = d.zenithDay;
                if (c.r != null) this.zenithDay.r = +c.r;
                if (c.g != null) this.zenithDay.g = +c.g;
                if (c.b != null) this.zenithDay.b = +c.b;
            }
            if (d.horizonDay) {
                const c = d.horizonDay;
                if (c.r != null) this.horizonDay.r = +c.r;
                if (c.g != null) this.horizonDay.g = +c.g;
                if (c.b != null) this.horizonDay.b = +c.b;
            }
            if (d.zenithNight) {
                const c = d.zenithNight;
                if (c.r != null) this.zenithNight.r = +c.r;
                if (c.g != null) this.zenithNight.g = +c.g;
                if (c.b != null) this.zenithNight.b = +c.b;
            }
            if (d.horizonNight) {
                const c = d.horizonNight;
                if (c.r != null) this.horizonNight.r = +c.r;
                if (c.g != null) this.horizonNight.g = +c.g;
                if (c.b != null) this.horizonNight.b = +c.b;
            }

            if (d.hazeDay != null) this.hazeDay = +d.hazeDay;
            if (d.hazeNight != null) this.hazeNight = +d.hazeNight;

            if (d.sunDisk != null) this.sunDisk = +d.sunDisk;
            if (d.moonDisk != null) this.moonDisk = +d.moonDisk;

            if (d.exposureDay != null) this.exposureDay = +d.exposureDay;
            if (d.exposureNight != null) this.exposureNight = +d.exposureNight;

            if (d.twilightWarmth != null) this.twilightWarmth = +d.twilightWarmth;
            if (d.twilightHazeBoost != null) this.twilightHazeBoost = +d.twilightHazeBoost;
            if (d.twilightExposureBoost != null) this.twilightExposureBoost = +d.twilightExposureBoost;

            if (d.texBlendDay != null) this.texBlendDay = +d.texBlendDay;
            if (d.texBlendNight != null) this.texBlendNight = +d.texBlendNight;

            if (d.texExposureDay != null) this.texExposureDay = +d.texExposureDay;
            if (d.texExposureNight != null) this.texExposureNight = +d.texExposureNight;

            if (d.crossfade) {
                const x = d.crossfade;
                if (x.enabled != null) this.crossfade.enabled = !!x.enabled;
                if (x.start != null) this.crossfade.start = +x.start;
                if (x.end != null) this.crossfade.end = +x.end;
            }
        }
    }

    update(render, celEval) {
        req(render, "[skydome] render is required");

        req(render.skyDomeCfg, "[skydome] render.skyDomeCfg(cfg) is required");
        req(render.skyDomeTexA, "[skydome] render.skyDomeTexA(asset) is required");
        req(render.skyDomeTexB, "[skydome] render.skyDomeTexB(asset) is required");

        req(celEval && typeof celEval.dayFactor === "number", "[skydome] celEval.dayFactor is required");

        const df = clamp01(celEval.dayFactor);
        const twilight = clamp01(celEval.twilight != null ? celEval.twilight : 0.0);

        const s = req(celEval && celEval.sun, "[skydome] celEval.sun is required");
        const m = req(celEval && celEval.moon, "[skydome] celEval.moon is required");

        const a = req(this.texA, "[skydome] skyDomeTexDay/skyDomeTexA is required (day texture)");
        const b = req(this.texB, "[skydome] skyDomeTexNight/skyDomeTexB is required (night texture)");

        if (a !== this._lastTexA) {
            render.skyDomeTexA(a);
            this._lastTexA = a;
        }
        if (b !== this._lastTexB) {
            render.skyDomeTexB(b);
            this._lastTexB = b;
        }

        let dayBlend = df;
        if (this.crossfade.enabled) {
            const s0 = Number(this.crossfade.start);
            const s1 = Number(this.crossfade.end);
            const e0 = Number.isFinite(s0) ? s0 : 0.10;
            const e1 = Number.isFinite(s1) ? s1 : 0.35;
            dayBlend = SkyMath.smoothstep(Math.min(e0, e1), Math.max(e0, e1), dayBlend);
        }

        const hazeBase = SkyMath.lerp(this.hazeNight, this.hazeDay, dayBlend);
        const exposureBase = SkyMath.lerp(this.exposureNight, this.exposureDay, dayBlend);

        const haze = clamp01(hazeBase + twilight * this.twilightHazeBoost);
        const exposure = Math.max(0.05, exposureBase + twilight * this.twilightExposureBoost);

        const texBlend = clamp01(SkyMath.lerp(this.texBlendNight, this.texBlendDay, dayBlend));
        const texExposure = Math.max(0.001, SkyMath.lerp(this.texExposureNight, this.texExposureDay, dayBlend));

        const skyBlend = 1.0 - dayBlend;

        const zen = lerpColor(this.zenithNight, this.zenithDay, dayBlend);
        const hor = lerpColor(this.horizonNight, this.horizonDay, dayBlend);

        const warm = clamp01(twilight * this.twilightWarmth);
        const horWarm = {
            r: SkyMath.lerp(hor.r, 1.05, warm),
            g: SkyMath.lerp(hor.g, 0.62, warm),
            b: SkyMath.lerp(hor.b, 0.30, warm)
        };

        const key =
            dayBlend.toFixed(4) + "|" +
            twilight.toFixed(4) + "|" +
            haze.toFixed(4) + "|" +
            exposure.toFixed(4) + "|" +
            texBlend.toFixed(4) + "|" +
            texExposure.toFixed(4) + "|" +
            skyBlend.toFixed(4) + "|" +
            this.sunDisk.toFixed(2) + "|" +
            this.moonDisk.toFixed(2) + "|" +
            s.rayDir.x.toFixed(4) + "|" + s.rayDir.y.toFixed(4) + "|" + s.rayDir.z.toFixed(4) + "|" +
            m.rayDir.x.toFixed(4) + "|" + m.rayDir.y.toFixed(4) + "|" + m.rayDir.z.toFixed(4) + "|" +
            s.color.r.toFixed(4) + "|" + s.color.g.toFixed(4) + "|" + s.color.b.toFixed(4) + "|" + (+s.intensity).toFixed(4) + "|" +
            m.color.r.toFixed(4) + "|" + m.color.g.toFixed(4) + "|" + m.color.b.toFixed(4) + "|" + (+m.intensity).toFixed(4) + "|" +
            zen.r.toFixed(4) + "|" + zen.g.toFixed(4) + "|" + zen.b.toFixed(4) + "|" +
            horWarm.r.toFixed(4) + "|" + horWarm.g.toFixed(4) + "|" + horWarm.b.toFixed(4);

        if (key === this._lastKey) return;
        this._lastKey = key;

        render.skyDomeCfg({

            sunDir: [s.rayDir.x, s.rayDir.y, s.rayDir.z],
            moonDir: [m.rayDir.x, m.rayDir.y, m.rayDir.z],

            sunColor: [s.color.r, s.color.g, s.color.b],
            sunIntensity: +s.intensity,

            moonColor: [m.color.r, m.color.g, m.color.b],
            moonIntensity: +m.intensity,

            zenithColor: [zen.r, zen.g, zen.b],
            horizonColor: [horWarm.r, horWarm.g, horWarm.b],

            haze,
            sunDisk: this.sunDisk,
            moonDisk: this.moonDisk,
            exposure,

            texBlend,
            texExposure,

            skyBlend
        });
    }
}

module.exports = SkyDome;