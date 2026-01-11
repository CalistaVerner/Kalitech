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

        // AAA: two textures always present (A=Day, B=Night) + SkyBlend 0..1
        this.texA = null; // day
        this.texB = null; // night

        // keep procedural contribution (avoid flat look)
        this.texBlendDay = 0.55;
        this.texBlendNight = 0.35;

        // HDR scale for bound sky textures
        this.texExposureDay = 1.80;
        this.texExposureNight = 0.65;

        // optional artistic crossfade shaping (dayFactor -> dayBlend)
        // dayBlend=0 => fully night, dayBlend=1 => fully day
        this.crossfade = {
            enabled: true,
            start: 0.10, // below: fully night
            end: 0.35    // above: fully day
        };

        this._lastTexA = "";
        this._lastTexB = "";
        this._lastKey = "";
    }

    applyCfg(cfg) {
        if (!cfg) return;

        // Accept either explicit A/B keys or day/night keys.
        if (cfg.skyDomeTexA != null) this.texA = String(cfg.skyDomeTexA);
        if (cfg.skyDomeTexB != null) this.texB = String(cfg.skyDomeTexB);

        if (cfg.skyDomeTexDay != null) this.texA = String(cfg.skyDomeTexDay);
        if (cfg.skyDomeTexNight != null) this.texB = String(cfg.skyDomeTexNight);

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

        // AAA: no swapping, two samplers always present
        req(render.skyDomeTexA, "[skydome] render.skyDomeTexA(asset) is required");
        req(render.skyDomeTexB, "[skydome] render.skyDomeTexB(asset) is required");

        req(celEval && typeof celEval.dayFactor === "number", "[skydome] celEval.dayFactor is required");
        const df = celEval.dayFactor;

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

        const haze = SkyMath.lerp(this.hazeNight, this.hazeDay, df);
        const exposure = SkyMath.lerp(this.exposureNight, this.exposureDay, df);

        // procedural vs texture mix
        const texBlend = Math.max(0, Math.min(1, SkyMath.lerp(this.texBlendNight, this.texBlendDay, df)));
        const texExposure = Math.max(0.001, SkyMath.lerp(this.texExposureNight, this.texExposureDay, df));

        // dayBlend (0..1): night->day curve
        let dayBlend = SkyMath.clamp(df, 0, 1);
        if (this.crossfade.enabled) {
            const s0 = Number(this.crossfade.start);
            const s1 = Number(this.crossfade.end);
            const e0 = Number.isFinite(s0) ? s0 : 0.10;
            const e1 = Number.isFinite(s1) ? s1 : 0.35;
            dayBlend = SkyMath.smoothstep(Math.min(e0, e1), Math.max(e0, e1), dayBlend);
        }

        // Shader mixes A->B by SkyBlend, where A=day, B=night:
        // SkyBlend=0 => day(A), SkyBlend=1 => night(B)
        const skyBlend = 1.0 - dayBlend;

        const key =
            df.toFixed(4) + "|" +
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
            texExposure,

            // AAA crossfade A/B
            skyBlend
        });
    }
}

module.exports = SkyDome;