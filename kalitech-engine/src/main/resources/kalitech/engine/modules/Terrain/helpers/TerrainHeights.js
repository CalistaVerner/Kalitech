// FILE: resources/kalitech/builtin/terrain/TerrainHeights.js
"use strict";

const {isObj, i32} = require("./TerrainTypes.js");

class TerrainHeights {
    constructor(terrNative) {
        this.terr = terrNative;
    }

    static toFloat32Array(raw) {
        if (!raw) return null;
        if (raw instanceof Float32Array) return raw;

        // If it's already a JS Array
        if (Array.isArray(raw)) {
            const out = new Float32Array(raw.length);
            for (let i = 0; i < out.length; i++) out[i] = +raw[i] || 0;
            return out;
        }

        // Host arrays / array-like (Java float[], double[], List with length, etc.)
        const len = (typeof raw.length === "number") ? (raw.length | 0) : 0;
        if (len > 0) {
            const out = new Float32Array(len);

            // Fast path: index access raw[i]
            // (works for Graal host arrays, TypedArrays, most array-likes)
            try {
                for (let i = 0; i < len; i++) out[i] = +raw[i] || 0;
                return out;
            } catch (_) {
                // Fallback path: try .get(i) if host exposes it
                if (typeof raw.get === "function") {
                    for (let i = 0; i < len; i++) out[i] = +raw.get(i) || 0;
                    return out;
                }
                // Last-resort: iterate if iterable
                if (raw && typeof raw[Symbol.iterator] === "function") {
                    let i = 0;
                    for (const v of raw) {
                        if (i >= len) break;
                        out[i++] = +v || 0;
                    }
                    return out;
                }
                // If it has length but we can't read values — return as-is
                return raw;
            }
        }

        return raw;
    }

    static inferSizeFromHeights(heights) {
        if (!heights) return 0;
        const len = (typeof heights.length === "number") ? (heights.length | 0) : 0;
        if (len <= 0) return 0;
        const s = Math.round(Math.sqrt(len));
        return (s > 0 && s * s === len) ? s : 0;
    }

    static isPow2(n) {
        return n > 0 && (n & (n - 1)) === 0;
    }

    static isJmeTerrainSize(n) {
        const x = (n | 0) - 1;
        return x > 0 && TerrainHeights.isPow2(x);
    }

    static validateTerrainDims(size, patchSize) {
        const s = size | 0;
        const p = patchSize | 0;

        if (s > 0 && !TerrainHeights.isJmeTerrainSize(s)) {
            throw new Error(`[TERR] size must be (2^k + 1). Got size=${s}`);
        }
        if (p > 0 && !TerrainHeights.isJmeTerrainSize(p)) {
            throw new Error(`[TERR] patchSize must be (2^k + 1). Got patchSize=${p}`);
        }
        if (s > 0 && p > 0 && p > s) {
            throw new Error(`[TERR] patchSize must be <= size. Got patchSize=${p} size=${s}`);
        }
    }

    static assertHeightsMatchSize(heights, size, where) {
        const s = i32(size, 0);
        if (s <= 0) return;

        const need = s * s;
        const got = (heights && typeof heights.length === "number") ? (heights.length | 0) : 0;
        if (got && got !== need) {
            throw new Error(`[TERR] ${where}: heights length must be size*size (${need}), got ${got} (size=${s})`);
        }
    }

    perlin(cfg) {
        if (!this.terr || typeof this.terr.perlinHeights !== "function") {
            throw new Error("[TERR] perlinHeights: native generator not available in this build");
        }
        return TerrainHeights.toFloat32Array(this.terr.perlinHeights(isObj(cfg) ? cfg : {}));
    }

    ridged(cfg) {
        if (!this.terr || typeof this.terr.ridgedHeights !== "function") {
            throw new Error("[TERR] ridgedHeights: native generator not available in this build");
        }
        return TerrainHeights.toFloat32Array(this.terr.ridgedHeights(isObj(cfg) ? cfg : {}));
    }
}

module.exports = {TerrainHeights};
