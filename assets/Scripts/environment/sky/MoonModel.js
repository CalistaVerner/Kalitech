// FILE: Scripts/systems/sky/MoonModel.js
"use strict";

const SkyMath = require("./SkyMath.js");

class MoonModel {
    constructor() {
        this.phaseOffset01 = 0.5;

        this.nightIntensity = 0.18;
        this.dayIntensity = 0.01;

        this.baseMoon = {r: 0.45, g: 0.55, b: 0.85};

        this.azimuthDeg = -20.0;
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.phaseOffset01 != null) {
            const v = +cfg.phaseOffset01;
            if (Number.isFinite(v)) this.phaseOffset01 = v;
        }

        if (cfg.nightIntensity != null) {
            const v = +cfg.nightIntensity;
            if (Number.isFinite(v)) this.nightIntensity = Math.max(0.0, v);
        }

        if (cfg.dayIntensity != null) {
            const v = +cfg.dayIntensity;
            if (Number.isFinite(v)) this.dayIntensity = Math.max(0.0, v);
        }

        if (cfg.azimuthDeg != null) {
            const v = +cfg.azimuthDeg;
            if (Number.isFinite(v)) this.azimuthDeg = v;
        }

        if (cfg.baseMoon) {
            const c = cfg.baseMoon;
            if (c.r != null) this.baseMoon.r = +c.r;
            if (c.g != null) this.baseMoon.g = +c.g;
            if (c.b != null) this.baseMoon.b = +c.b;
        }
    }

    evaluate(time01, sunEval) {
        const phase = SkyMath.wrap((+time01 || 0.0) + this.phaseOffset01, 0, 1);

        const altSin = Math.sin((phase * Math.PI * 2.0) - Math.PI * 0.5);
        const altitude =
            SkyMath.lerp(-0.20, 0.95, (altSin + 1.0) * 0.5) * (Math.PI / 2.0) -
            (Math.PI / 2.0) * 0.10;

        const azimuth = (phase * Math.PI * 2.0) + SkyMath.degToRad(this.azimuthDeg);
        const moonPosDir = SkyMath.dirFromAltAz(altitude, azimuth);

        const dayFactor = sunEval ? (sunEval.dayFactor || 0.0) : 0.0;
        const nightFactor = 1.0 - SkyMath.clamp(dayFactor, 0, 1);

        const intensity = SkyMath.lerp(this.dayIntensity, this.nightIntensity, nightFactor);

        const tw = sunEval ? (sunEval.twilight || 0.0) : 0.0;
        const twTint = SkyMath.clamp(1.0 - tw, 0, 1);

        const r = SkyMath.lerp(this.baseMoon.r, 0.55, 0.20 * twTint);
        const g = SkyMath.lerp(this.baseMoon.g, 0.60, 0.20 * twTint);
        const b = SkyMath.lerp(this.baseMoon.b, 0.92, 0.35 * twTint);

        const rayDir = {x: -moonPosDir.x, y: -moonPosDir.y, z: -moonPosDir.z};

        return {
            moonPosDir,
            rayDir,
            color: {r, g, b},
            intensity
        };
    }
}

module.exports = MoonModel;