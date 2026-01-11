"use strict";

class SkyMath {
    static clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

    static wrap(v, a, b) {
        const span = b - a;
        if (span <= 0) return a;
        let x = (v - a) % span;
        if (x < 0) x += span;
        return a + x;
    }

    static lerp(a, b, t) { return a + (b - a) * t; }

    static smoothstep(edge0, edge1, x) {
        const t = SkyMath.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static degToRad(deg) { return deg * (Math.PI / 180.0); }

    static dirFromAltAz(alt, az) {
        const ca = Math.cos(alt);
        const x = Math.cos(az) * ca;
        const y = Math.sin(alt);
        const z = Math.sin(az) * ca;
        const len = Math.sqrt(x * x + y * y + z * z) || 1.0;
        return { x: x / len, y: y / len, z: z / len };
    }

    static rgbKey(r, g, b) {
        return Number(r).toFixed(4) + "|" + Number(g).toFixed(4) + "|" + Number(b).toFixed(4);
    }

    /**
     * Black-body approximation (Kelvin) -> sRGB 0..1
     * Clamp for stability.
     */
    static kelvinToRgb01(kelvin) {
        let k = Number(kelvin);
        if (!Number.isFinite(k)) k = 6500;
        k = SkyMath.clamp(k, 1000, 40000);

        const t = k / 100.0;

        let r, g, b;
        if (t <= 66.0) {
            r = 255.0;
            g = 99.4708025861 * Math.log(t) - 161.1195681661;
            b = (t <= 19.0) ? 0.0 : (138.5177312231 * Math.log(t - 10.0) - 305.0447927307);
        } else {
            r = 329.698727446 * Math.pow(t - 60.0, -0.1332047592);
            g = 288.1221695283 * Math.pow(t - 60.0, -0.0755148492);
            b = 255.0;
        }

        r = SkyMath.clamp(r, 0.0, 255.0) / 255.0;
        g = SkyMath.clamp(g, 0.0, 255.0) / 255.0;
        b = SkyMath.clamp(b, 0.0, 255.0) / 255.0;

        return {r, g, b};
    }
}

module.exports = SkyMath;