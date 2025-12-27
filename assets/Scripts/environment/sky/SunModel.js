// FILE: Scripts/systems/sky/SunModel.js
"use strict";

const SkyMath = require("./SkyMath.js");

class SunModel {
    constructor() {
        this.azimuthDeg = 35.0;

        this.nightIntensity = 0.02;
        this.dayIntensity = 1.35;
        this.sunsetWarmth = 0.35;

        this.baseSun = { r: 1.0, g: 0.98, b: 0.90 };
    }

    applyCfg(cfg) {
        if (!cfg) return;

        const az = +cfg.azimuthDeg;
        if (Number.isFinite(az)) this.azimuthDeg = az;

        const ni = +cfg.nightIntensity;
        if (Number.isFinite(ni)) this.nightIntensity = ni;

        const di = +cfg.dayIntensity;
        if (Number.isFinite(di)) this.dayIntensity = di;

        const sw = +cfg.sunsetWarmth;
        if (Number.isFinite(sw)) this.sunsetWarmth = sw;

        if (cfg.baseSun) {
            const c = cfg.baseSun;
            if (c.r != null) this.baseSun.r = +c.r;
            if (c.g != null) this.baseSun.g = +c.g;
            if (c.b != null) this.baseSun.b = +c.b;
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

        const above = SkyMath.clamp((sunPosDir.y + 0.02) / 0.45, 0, 1);
        const dayFactor = SkyMath.smoothstep(0.02, 0.25, above);
        const noonBoost = SkyMath.smoothstep(0.25, 1.0, above);

        const intensity =
            Math.max(0.0,
                SkyMath.lerp(this.nightIntensity, this.dayIntensity, dayFactor) *
                SkyMath.lerp(0.55, 1.0, noonBoost)
            );

        const horizonWarm = SkyMath.smoothstep(0.0, 0.18, 1.0 - above) * dayFactor;
        const warm = this.sunsetWarmth * horizonWarm;

        const r = SkyMath.lerp(this.baseSun.r, 1.15, warm);
        const g = SkyMath.lerp(this.baseSun.g, 0.92, warm);
        const b = SkyMath.lerp(this.baseSun.b, 0.65, warm);

        const rayDir = { x: -sunPosDir.x, y: -sunPosDir.y, z: -sunPosDir.z };

        return {
            dayFactor,
            isDay: dayFactor > 0.08,
            sunPosDir,
            rayDir,
            color: { r, g, b },
            intensity
        };
    }
}

module.exports = SunModel;