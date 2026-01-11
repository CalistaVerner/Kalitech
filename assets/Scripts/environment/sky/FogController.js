"use strict";

const SkyMath = require("./SkyMath.js");

class FogController {
    constructor() {
        this.fogBase = { r: 0.70, g: 0.78, b: 0.90 };
        this.fogDistance = 250.0;
        this.fogDensityDay = 1.10;
        this.fogDensityNight = 1.35;

        this._lastFogDensity = NaN;
        this._lastFogColorKey = "";
        this._lastFogDistance = NaN;
    }

    applyCfg(cfg) {
        if (!cfg || !cfg.fog) return;

        const fog = cfg.fog;

        if (fog.color) {
            const c = fog.color;
            if (c.r != null) this.fogBase.r = +c.r;
            if (c.g != null) this.fogBase.g = +c.g;
            if (c.b != null) this.fogBase.b = +c.b;
        }

        if (fog.distance != null) {
            const v = +fog.distance;
            if (Number.isFinite(v)) this.fogDistance = v;
        }

        if (fog.densityDay != null) {
            const v = +fog.densityDay;
            if (Number.isFinite(v)) this.fogDensityDay = v;
        }

        if (fog.densityNight != null) {
            const v = +fog.densityNight;
            if (Number.isFinite(v)) this.fogDensityNight = v;
        }

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][fog] applyCfg color=(" + this.fogBase.r + "," + this.fogBase.g + "," + this.fogBase.b + ")" +
                " densityDay=" + this.fogDensityDay +
                " densityNight=" + this.fogDensityNight +
                " distance=" + this.fogDistance
            );
        }
    }

    init(render) {
        if (!render || typeof render.fogCfg !== "function") {
            throw new Error("[fog] render.fogCfg(cfg) is required");
        }

        render.fogCfg({
            color: [this.fogBase.r, this.fogBase.g, this.fogBase.b],
            density: this.fogDensityDay,
            distance: this.fogDistance
        });

        this._lastFogDensity = NaN;
        this._lastFogColorKey = "";
        this._lastFogDistance = NaN;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][fog] init applied");
        }
    }

    update(render, celEval) {
        const d = (celEval && typeof celEval.dayFactor === "number") ? celEval.dayFactor : 1.0;

        const fogD = SkyMath.lerp(this.fogDensityNight, this.fogDensityDay, d);
        const key = SkyMath.rgbKey(this.fogBase.r, this.fogBase.g, this.fogBase.b);

        const changed =
            Math.abs(fogD - this._lastFogDensity) > 0.002 ||
            key !== this._lastFogColorKey ||
            Math.abs(this.fogDistance - this._lastFogDistance) > 0.1;

        if (!changed) return;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][fog] APPLY density=" + fogD.toFixed(4) +
                " distance=" + this.fogDistance.toFixed(1) +
                " dayFactor=" + d.toFixed(4)
            );
        }

        render.fogCfg({
            color: [this.fogBase.r, this.fogBase.g, this.fogBase.b],
            density: fogD,
            distance: this.fogDistance
        });

        this._lastFogDensity = fogD;
        this._lastFogColorKey = key;
        this._lastFogDistance = this.fogDistance;
    }

    destroy() {
    }
}

module.exports = FogController;