// FILE: Scripts/systems/sky/SkyMath.js
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
}

module.exports = SkyMath;