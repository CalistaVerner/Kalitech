"use strict";

const SkyMath = require("./SkyMath.js");

class CelestialModel {
    constructor() {
        this.azimuthDeg = 35.0;

        // sun intensities
        this.sunDayIntensity = 1.35;
        this.sunNightIntensity = 0.0;

        // Kelvin -> RGB
        this.sunKelvinNoon = 6500;
        this.sunKelvinHorizon = 2200;

        // art multiplier
        this.baseSun = {r: 1.0, g: 1.0, b: 1.0};

        // moon
        this.moonIntensity = 0.14;
        this.moonColor = {r: 0.45, g: 0.55, b: 0.85};

        // primary switch hysteresis
        this.daySwitch = 0.10;
        this.dayOn = 0.12;
        this.dayOff = 0.08;

        this._primary = "sun";

        // longer twilight/night (configurable)
        this.dayCurve = {
            horizonOffset: 0.06,
            horizonScale: 0.55,
            dayStart: 0.08,
            dayEnd: 0.38
        };

        // debug
        this._dbgPrimary = null;
    }

    applyCfg(cfg) {
        if (!cfg) return;

        const az = +cfg.azimuthDeg;
        if (Number.isFinite(az)) this.azimuthDeg = az;

        if (cfg.sunDayIntensity != null) {
            const v = +cfg.sunDayIntensity;
            if (Number.isFinite(v)) this.sunDayIntensity = v;
        }
        if (cfg.sunNightIntensity != null) {
            const v = +cfg.sunNightIntensity;
            if (Number.isFinite(v)) this.sunNightIntensity = v;
        }

        if (cfg.sunKelvinNoon != null) {
            const v = +cfg.sunKelvinNoon;
            if (Number.isFinite(v)) this.sunKelvinNoon = SkyMath.clamp(v, 1000, 40000);
        }
        if (cfg.sunKelvinHorizon != null) {
            const v = +cfg.sunKelvinHorizon;
            if (Number.isFinite(v)) this.sunKelvinHorizon = SkyMath.clamp(v, 1000, 40000);
        }

        if (cfg.baseSun) {
            const c = cfg.baseSun;
            if (c.r != null) this.baseSun.r = +c.r;
            if (c.g != null) this.baseSun.g = +c.g;
            if (c.b != null) this.baseSun.b = +c.b;
        }

        if (cfg.moonIntensity != null) {
            const v = +cfg.moonIntensity;
            if (Number.isFinite(v)) this.moonIntensity = v;
        }
        if (cfg.moonColor) {
            const c = cfg.moonColor;
            if (c.r != null) this.moonColor.r = +c.r;
            if (c.g != null) this.moonColor.g = +c.g;
            if (c.b != null) this.moonColor.b = +c.b;
        }

        if (cfg.daySwitch != null) {
            const v = +cfg.daySwitch;
            if (Number.isFinite(v)) this.daySwitch = SkyMath.clamp(v, 0.01, 0.99);
        }

        if (cfg.dayOn != null) {
            const v = +cfg.dayOn;
            if (Number.isFinite(v)) this.dayOn = SkyMath.clamp(v, 0.01, 0.99);
        }
        if (cfg.dayOff != null) {
            const v = +cfg.dayOff;
            if (Number.isFinite(v)) this.dayOff = SkyMath.clamp(v, 0.01, 0.99);
        }

        // if not explicitly set, derive band around daySwitch
        if (cfg.dayOn == null && cfg.dayOff == null) {
            const band = 0.02;
            this.dayOn = SkyMath.clamp(this.daySwitch + band, 0.01, 0.99);
            this.dayOff = SkyMath.clamp(this.daySwitch - band, 0.01, 0.99);
        }

        if (cfg.dayCurve) {
            const dc = cfg.dayCurve;

            if (dc.horizonOffset != null) {
                const v = +dc.horizonOffset;
                if (Number.isFinite(v)) this.dayCurve.horizonOffset = v;
            }
            if (dc.horizonScale != null) {
                const v = +dc.horizonScale;
                if (Number.isFinite(v) && v > 0.01) this.dayCurve.horizonScale = v;
            }
            if (dc.dayStart != null) {
                const v = +dc.dayStart;
                if (Number.isFinite(v)) this.dayCurve.dayStart = SkyMath.clamp(v, 0, 1);
            }
            if (dc.dayEnd != null) {
                const v = +dc.dayEnd;
                if (Number.isFinite(v)) this.dayCurve.dayEnd = SkyMath.clamp(v, 0, 1);
            }
        }

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][celestial] applyCfg azimuthDeg=" + this.azimuthDeg +
                " kelvinNoon=" + this.sunKelvinNoon +
                " kelvinHorizon=" + this.sunKelvinHorizon +
                " dayOn=" + this.dayOn +
                " dayOff=" + this.dayOff +
                " horizonOffset=" + this.dayCurve.horizonOffset +
                " horizonScale=" + this.dayCurve.horizonScale +
                " dayStart=" + this.dayCurve.dayStart +
                " dayEnd=" + this.dayCurve.dayEnd
            );
        }
    }

    evaluate(time01) {
        const phase = SkyMath.wrap(+time01, 0, 1);

        const alt = Math.sin((phase * Math.PI * 2.0) - Math.PI * 0.5);
        const altitude =
            SkyMath.lerp(-0.25, 1.05, (alt + 1.0) * 0.5) * (Math.PI / 2.0) -
            (Math.PI / 2.0) * 0.15;

        const azimuth = (phase * Math.PI * 2.0) + SkyMath.degToRad(this.azimuthDeg);

        const sunPosDir = SkyMath.dirFromAltAz(altitude, azimuth);
        const sunRayDir = {x: -sunPosDir.x, y: -sunPosDir.y, z: -sunPosDir.z};

        const above = SkyMath.clamp(
            (sunPosDir.y + this.dayCurve.horizonOffset) / this.dayCurve.horizonScale,
            0, 1
        );

        const ds = this.dayCurve.dayStart;
        const de = Math.max(ds + 1e-6, this.dayCurve.dayEnd);
        const dayFactor = SkyMath.smoothstep(ds, de, above);

        const noonBoost = SkyMath.smoothstep(0.25, 1.0, above);
        const sunIntensity = Math.max(
            0.0,
            SkyMath.lerp(this.sunNightIntensity, this.sunDayIntensity, dayFactor) *
            SkyMath.lerp(0.55, 1.0, noonBoost)
        );

        // Kelvin blend by above-horizon factor (warm sunrise/sunset)
        const kelT = SkyMath.smoothstep(0.0, 0.65, above);
        const kelvin = SkyMath.lerp(this.sunKelvinHorizon, this.sunKelvinNoon, kelT);
        const bb = SkyMath.kelvinToRgb01(kelvin);

        const sunColor = {
            r: bb.r * this.baseSun.r,
            g: bb.g * this.baseSun.g,
            b: bb.b * this.baseSun.b
        };

        const moonRayDir = {x: -sunRayDir.x, y: -sunRayDir.y, z: -sunRayDir.z};
        const moonColor = {r: this.moonColor.r, g: this.moonColor.g, b: this.moonColor.b};

        // hysteresis
        if (this._primary === "sun") {
            if (dayFactor < this.dayOff) this._primary = "moon";
        } else {
            if (dayFactor > this.dayOn) this._primary = "sun";
        }

        if (this._primary !== this._dbgPrimary) {
            this._dbgPrimary = this._primary;
            if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                ENGINE.log.debug(
                    "[sky][celestial] PRIMARY -> " + this._primary +
                    " (dayFactor=" + dayFactor.toFixed(4) +
                    " above=" + above.toFixed(4) +
                    " sunY=" + sunPosDir.y.toFixed(4) +
                    " kelvin=" + Math.round(kelvin) + ")"
                );
            }
        }

        const isDay = this._primary === "sun";
        const isNight = !isDay;

        return {
            time01: phase,
            dayFactor,
            isDay,
            isNight,
            primary: this._primary,

            sun: {rayDir: sunRayDir, color: sunColor, intensity: sunIntensity},
            moon: {rayDir: moonRayDir, color: moonColor, intensity: this.moonIntensity},

            // extra debug signal if you want
            _dbg: {above, sunY: sunPosDir.y, kelvin}
        };
    }
}

module.exports = CelestialModel;