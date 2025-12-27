// FILE: Scripts/systems/sky/FogController.js
"use strict";

const SkyMath = require("./SkyMath.js");

class FogController {
    constructor() {
        this.fogBase = { r: 0.70, g: 0.78, b: 0.90 };
        this.fogDistance = 250.0;
        this.fogDensityDay = 1.10;
        this.fogDensityNight = 1.35;

        this.lastFogDensity = NaN;
        this.lastFogColorKey = "";
        this.lastFogDistance = NaN;
    }

    applyCfg(cfg) {
        if (!cfg || !cfg.fog) return;

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

    init(render) {
        render.fogCfg({
            color: [this.fogBase.r, this.fogBase.g, this.fogBase.b],
            density: this.fogDensityDay,
            distance: this.fogDistance
        });

        this.lastFogDensity = NaN;
        this.lastFogColorKey = "";
        this.lastFogDistance = NaN;
    }

    update(render, sunEval) {
        const fogD = SkyMath.lerp(this.fogDensityNight, this.fogDensityDay, sunEval.dayFactor);
        const key = SkyMath.rgbKey(this.fogBase.r, this.fogBase.g, this.fogBase.b);

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
}

module.exports = FogController;