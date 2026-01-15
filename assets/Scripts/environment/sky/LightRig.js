"use strict";

const SkyMath = require("./SkyMath.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function clamp01(x) {
    x = +x;
    if (!Number.isFinite(x)) return 0;
    return x < 0 ? 0 : (x > 1 ? 1 : x);
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

class LightRig {
    constructor() {
        this._enabled = true;

        // Ambient artistic defaults (CDPR-ish)
        this.ambientDay = {r: 0.22, g: 0.25, b: 0.30, intensity: 1.0};
        this.ambientNight = {r: 0.04, g: 0.06, b: 0.10, intensity: 0.75};

        // Shadows config (applied once when cfg changes)
        this.shadows = {
            mapSize: 2048,
            splits: 3,
            lambda: 0.65,
            intensity: 0.65,
            snap: true
        };

        this._render = null;

        this._lastPrimary = null;
        this._lastShadowKey = "";
        this._cfgRef = null;
    }

    init(engine, render) {
        req(engine, "[sky][light] engine is required");
        this._render = req(render, "[sky][light] render is required");
    }

    destroy() {
    }

    setEnabled(v) {
        this._enabled = !!v;
    }

    applyCfg(cfg) {
        if (cfg === this._cfgRef) return;
        this._cfgRef = cfg;

        // Optional overrides (but no fallback hunts; only inside cfg)
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
            if (s.mapSize != null) this.shadows.mapSize = (s.mapSize | 0);
            if (s.splits != null) this.shadows.splits = (s.splits | 0);
            if (s.lambda != null) this.shadows.lambda = +s.lambda;
            if (s.intensity != null) this.shadows.intensity = +s.intensity;
            if (s.snap != null) this.shadows.snap = !!s.snap;
        }
    }

    update(engine, render, cel, dt) {
        if (!this._enabled) return;

        req(render, "[sky][light] render is required");
        req(cel, "[sky][light] cel is required");
        req(cel.sun && cel.sun.rayDir, "[sky][light] cel.sun.rayDir is required");
        req(cel.moon && cel.moon.rayDir, "[sky][light] cel.moon.rayDir is required");

        // 1) Primary switch: MUST drive RenderApi + shadows binding
        const primary = String(cel.primary);
        if (primary !== this._lastPrimary) {
            this._lastPrimary = primary;
            render.setPrimaryDirectional(primary);
        }

        // 2) Directional lights (sun & moon always exist; intensities vary)
        render.sunCfg({
            dir: [cel.sun.rayDir.x, cel.sun.rayDir.y, cel.sun.rayDir.z],
            color: [cel.sun.color.r, cel.sun.color.g, cel.sun.color.b],
            intensity: +cel.sun.intensity
        });

        render.moonCfg({
            dir: [cel.moon.rayDir.x, cel.moon.rayDir.y, cel.moon.rayDir.z],
            color: [cel.moon.color.r, cel.moon.color.g, cel.moon.color.b],
            intensity: +cel.moon.intensity
        });

        // 3) Ambient (mix by dayFactor, CDPR-ish: night still has some fill)
        const df = clamp01(cel.dayFactor);
        const ar = lerp(this.ambientNight.r, this.ambientDay.r, df);
        const ag = lerp(this.ambientNight.g, this.ambientDay.g, df);
        const ab = lerp(this.ambientNight.b, this.ambientDay.b, df);
        const ai = Math.max(0.0, lerp(this.ambientNight.intensity, this.ambientDay.intensity, df));

        render.ambientCfg({
            color: {r: ar, g: ag, b: ab},
            intensity: ai
        });

        // 4) Shadows: apply STRICT cfg once (renderer itself updates every frame because light direction changes)
        const s = this.shadows;
        const key = [
            s.mapSize | 0,
            s.splits | 0,
            (+s.lambda).toFixed(4),
            (+s.intensity).toFixed(4),
            s.snap ? 1 : 0
        ].join("|");

        if (key !== this._lastShadowKey) {
            this._lastShadowKey = key;

            render.sunShadowsCfg({
                shadows: {
                    mapSize: 16384,
                    splits: 4,
                    lambda: 0.72,
                    intensity: 0.75,
                    snap: true,
                    snapFirstCascades: 2,
                    extentsPadding: 1.02,

                    pipeline: [
                        {type: "hysteresis", hysteresis: 10.0, smoothing: 0.10},

                        {type: "basis"},
                        {
                            type: "tightFit",
                            pad: 1.02,
                            forceSquare: true,
                            sizeQuantizeTexels: 1.0,
                            minNear: 0.5,
                            casterBackBase: 140,
                            casterBackCascadeMul: 0.9,
                            receiverFrontBase: 40,
                            lockNearCascadeSize: true,
                            nearTierTexels: 128,
                            nearShrinkHysteresisTiers: 1.0
                        },
                        {
                            type: "temporalGate",
                            minRotateDeg: 0.25,
                            minMoveTexels: 1.25,
                            teleportMoveTexels: 24.0,
                            gatedFirstCascades: 1
                        },
                        {
                            type: "texelSnap",
                            enabled: true,
                            snapFirstCascades: 2
                        },
                        {
                            type: "trace",
                            enabled: true,
                            everyFrames: 60,
                            allSplits: false
                        }
                    ]
                }
            });


        }
    }
}

module.exports = LightRig;