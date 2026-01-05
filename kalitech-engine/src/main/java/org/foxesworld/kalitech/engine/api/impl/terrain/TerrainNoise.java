package org.foxesworld.kalitech.engine.api.impl.terrain;

import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.*;
import static org.foxesworld.kalitech.engine.util.ValueCfg.i32;

/**
 * Procedural noise for terrain:
 *  - Perlin FBM
 *  - Ridged multifractal
 *  - Domain warp
 *  - Biome masks (temperature/moisture + derived biome weights)
 *
 * Goals:
 *  - deterministic
 *  - editor/runtime parity
 *  - explicit parameterization (no hidden randomness)
 */
public final class TerrainNoise {

    public enum Type { PERLIN, RIDGED }

    // ---------------------------------------------------------------------
    // Public API (Value-based for Graal)
    // ---------------------------------------------------------------------

    public float[] perlinHeights(Value cfg) { return heights(Type.PERLIN, cfg); }
    public float[] ridgedHeights(Value cfg) { return heights(Type.RIDGED, cfg); }

    /**
     * Domain-warped variant.
     * cfg = normal noise cfg plus:
     *  - warp: { enabled?:bool=true, seed?:int, scale?:double=32, amp?:double=12, octaves?:int=3, lacunarity?:double=2, persistence?:double=0.5 }
     */
    public float[] perlinWarpHeights(Value cfg) { return heights(Type.PERLIN, cfg, true); }
    public float[] ridgedWarpHeights(Value cfg) { return heights(Type.RIDGED, cfg, true); }

    /**
     * Generate biome masks.
     * cfg:
     *  - size: int (required)
     *  - seed?: int
     *  - elevation: noise cfg (same as perlin cfg) OR you can pass heights directly as cfg.heights
     *  - temp: { seed?:int, scale?:double=256, octaves?:int=4, persistence?:double=0.5, lacunarity?:double=2, offsetX?:double, offsetZ?:double }
     *  - moist: { seed?:int, scale?:double=256, octaves?:int=4, persistence?:double=0.5, lacunarity?:double=2, offsetX?:double, offsetZ?:double }
     *  - tempLapse?: double (default 0.6)   // temperature decreases with elevation
     *  - seaLevel?: double (default 0.35)   // affects beach/swamp masks
     *
     * Returns a Java Map<String,float[]> (Graal exposes it as JS object).
     */
    public Map<String, float[]> biomeMasks(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("biome cfg is null");
        int size = i32(cfg, "size", 0);
        if (size <= 1) throw new IllegalArgumentException("biome.size must be > 1");

        // Elevation field: either provided heights, or generated from cfg.elevation
        float[] elev;
        Value heightsV = member(cfg, "heights");
        if (heightsV != null && !heightsV.isNull() && heightsV.hasArrayElements()) {
            elev = readFloatArray(heightsV);
        } else {
            Value elevCfg = member(cfg, "elevation");
            if (elevCfg == null || elevCfg.isNull()) {
                // fallback: treat cfg itself as elevation noise cfg
                elevCfg = cfg;
            }
            elev = heights(Type.PERLIN, elevCfg);
        }
        if (elev.length != size * size) {
            throw new IllegalArgumentException("biome: elevation/heightmap length must be size*size");
        }

        int seed = i32(cfg, "seed", 0);
        double tempLapse = clamp(num(cfg, "tempLapse", 0.6), 0.0, 2.0);
        double seaLevel = clamp(num(cfg, "seaLevel", 0.35), 0.0, 1.0);

        // temperature & moisture noise
        float[] temp = fieldFrom(cfg, "temp", size, seed ^ 0xA2C2_1B3D, 256.0);
        float[] moist = fieldFrom(cfg, "moist", size, seed ^ 0x77D1_9F21, 256.0);

        // Apply lapse rate: colder at high elevation
        for (int i = 0; i < temp.length; i++) {
            double e = elev[i];
            double t = temp[i];
            t = t - (e * tempLapse);
            temp[i] = (float) clamp01(t);
        }

        // Derived biome weights (soft classification)
        float[] ocean = new float[temp.length];
        float[] beach = new float[temp.length];
        float[] desert = new float[temp.length];
        float[] grass = new float[temp.length];
        float[] forest = new float[temp.length];
        float[] swamp = new float[temp.length];
        float[] tundra = new float[temp.length];
        float[] snow = new float[temp.length];
        float[] mountain = new float[temp.length];

        for (int i = 0; i < temp.length; i++) {
            double e = elev[i];
            double t = temp[i];
            double m = moist[i];

            double oce = smoothstep(seaLevel, seaLevel + 0.06, seaLevel - e); // below sea
            oce = clamp01(oce);

            double bch = (1.0 - oce) * band(e, seaLevel, seaLevel + 0.06);

            double mtn = smoothstep(0.72, 0.90, e);

            double snw = smoothstep(0.68, 0.82, (1.0 - t)) * mtn;
            double tnd = smoothstep(0.55, 0.75, (1.0 - t)) * (1.0 - mtn);

            double dst = (1.0 - oce) * smoothstep(0.55, 0.85, t) * smoothstep(0.65, 0.95, (1.0 - m));
            double swp = (1.0 - oce) * band(e, seaLevel + 0.02, seaLevel + 0.12) * smoothstep(0.55, 0.95, m);

            double frs = (1.0 - oce) * smoothstep(0.35, 0.75, m) * smoothstep(0.20, 0.70, t) * (1.0 - mtn);
            double grs = (1.0 - oce) * smoothstep(0.25, 0.65, m) * smoothstep(0.35, 0.85, t) * (1.0 - frs) * (1.0 - dst);

            ocean[i] = (float) oce;
            beach[i] = (float) clamp01(bch);
            desert[i] = (float) clamp01(dst);
            grass[i] = (float) clamp01(grs);
            forest[i] = (float) clamp01(frs);
            swamp[i] = (float) clamp01(swp);
            tundra[i] = (float) clamp01(tnd);
            snow[i] = (float) clamp01(snw);
            mountain[i] = (float) clamp01(mtn);
        }

        Map<String, float[]> out = new HashMap<>();
        out.put("elevation", elev);
        out.put("temperature", temp);
        out.put("moisture", moist);
        out.put("ocean", ocean);
        out.put("beach", beach);
        out.put("desert", desert);
        out.put("grass", grass);
        out.put("forest", forest);
        out.put("swamp", swamp);
        out.put("tundra", tundra);
        out.put("snow", snow);
        out.put("mountain", mountain);
        return out;
    }

    // ---------------------------------------------------------------------
    // Core height generation
    // ---------------------------------------------------------------------

    public float[] heights(Type type, Value cfg) { return heights(type, cfg, false); }

    private float[] heights(Type type, Value cfg, boolean forceWarp) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("noise cfg is null");

        int size = i32(cfg, "size", 0);
        if (size <= 1) throw new IllegalArgumentException("noise.size must be > 1");

        int seed = i32(cfg, "seed", 0);
        double scale = Math.max(0.0001, num(cfg, "scale", 64.0));
        int octaves = Math.max(1, i32(cfg, "octaves", 5));
        double persistence = num(cfg, "persistence", 0.5);
        double lacunarity = num(cfg, "lacunarity", 2.0);
        double ox = num(cfg, "offsetX", 0.0);
        double oz = num(cfg, "offsetZ", 0.0);
        boolean normalize = bool(cfg, "normalize", true);

        // optional amplitude/base AFTER normalization
        double amplitude = num(cfg, "amplitude", 1.0);
        double base = num(cfg, "base", 0.0);

        // Domain warp
        Value warpV = member(cfg, "warp");
        boolean warpEnabled = forceWarp || (warpV != null && !warpV.isNull() && bool(warpV, "enabled", true));

        WarpParams warp = null;
        if (warpEnabled) {
            int wSeed = (warpV != null && !warpV.isNull()) ? i32(warpV, "seed", seed ^ 0x9E37_79B9) : (seed ^ 0x9E37_79B9);
            warp = new WarpParams(
                    wSeed,
                    Math.max(0.0001, (warpV != null && !warpV.isNull()) ? num(warpV, "scale", 32.0) : 32.0),
                    Math.max(0.0,    (warpV != null && !warpV.isNull()) ? num(warpV, "amp", 12.0)   : 12.0),
                    Math.max(1,      (warpV != null && !warpV.isNull()) ? i32(warpV, "octaves", 3) : 3),
                    (warpV != null && !warpV.isNull()) ? num(warpV, "persistence", 0.5) : 0.5,
                    (warpV != null && !warpV.isNull()) ? num(warpV, "lacunarity", 2.0)  : 2.0
            );
        }

        return generate(type, size, seed, scale, octaves, persistence, lacunarity, ox, oz, normalize, amplitude, base, warp);
    }

    // ---------------------------------------------------------------------
    // Helpers: biome fields + math
    // ---------------------------------------------------------------------

    private static float[] fieldFrom(Value cfg, String key, int size, int defaultSeed, double defaultScale) {
        Value sub = member(cfg, key);
        if (sub == null || sub.isNull()) {
            return generate(Type.PERLIN, size, defaultSeed, defaultScale, 4, 0.5, 2.0, 0.0, 0.0, true, 1.0, 0.0, null);
        }

        Value s = sub;
        int seed = i32(s, "seed", defaultSeed);
        double scale = Math.max(0.0001, num(s, "scale", defaultScale));
        int oct = Math.max(1, i32(s, "octaves", 4));
        double pers = num(s, "persistence", 0.5);
        double lac = num(s, "lacunarity", 2.0);
        double ox = num(s, "offsetX", 0.0);
        double oz = num(s, "offsetZ", 0.0);

        return generate(Type.PERLIN, size, seed, scale, oct, pers, lac, ox, oz, true, 1.0, 0.0, null);
    }

    static float[] generate(Type type,
                            int size,
                            int seed,
                            double scale,
                            int octaves,
                            double persistence,
                            double lacunarity,
                            double ox,
                            double oz,
                            boolean normalize,
                            double amplitude,
                            double base,
                            WarpParams warp) {

        float[] out = new float[size * size];

        double maxAmp = 0.0;
        {
            double amp = 1.0;
            for (int o = 0; o < octaves; o++) {
                maxAmp += amp;
                amp *= persistence;
            }
            if (maxAmp < 1e-9) maxAmp = 1.0;
        }

        Perlin2D basePerlin = new Perlin2D(seed);
        Perlin2D warpPerlinX = (warp != null) ? new Perlin2D(warp.seed ^ 0x1234ABCD) : null;
        Perlin2D warpPerlinZ = (warp != null) ? new Perlin2D(warp.seed ^ 0xCDEF5678) : null;

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {

                double wx = x;
                double wz = z;

                if (warp != null && warp.amp > 0.0) {
                    double dx = fbm(warpPerlinX, (wx / warp.scale) + ox, (wz / warp.scale) + oz,
                            warp.octaves, warp.lacunarity, warp.persistence);
                    double dz = fbm(warpPerlinZ, (wx / warp.scale) - ox, (wz / warp.scale) - oz,
                            warp.octaves, warp.lacunarity, warp.persistence);
                    wx += dx * warp.amp;
                    wz += dz * warp.amp;
                }

                double amp = 1.0;
                double freq = 1.0;
                double sum = 0.0;

                for (int o = 0; o < octaves; o++) {
                    double nx = (wx / scale) * freq + ox;
                    double nz = (wz / scale) * freq + oz;
                    double n = basePerlin.noise(nx, nz);

                    if (type == Type.RIDGED) {
                        double r = 1.0 - Math.abs(n);
                        n = r * r;
                    }

                    sum += n * amp;
                    amp *= persistence;
                    freq *= lacunarity;
                }

                double v;
                if (normalize) {
                    if (type == Type.PERLIN) v = (sum / maxAmp + 1.0) * 0.5;
                    else v = sum / maxAmp;
                    v = clamp01(v);
                } else {
                    v = sum;
                }

                v = base + v * amplitude;
                out[z * size + x] = (float) v;
            }
        }
        return out;
    }

    private static double clamp01(double v) { return (v < 0.0) ? 0.0 : (v > 1.0) ? 1.0 : v; }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    // weight band around [a..b]
    private static double band(double x, double a, double b) {
        double t = (x - a) / (b - a);
        return clamp01(1.0 - Math.abs(t * 2.0 - 1.0));
    }

    private static double fbm(Perlin2D perlin, double x, double y, int oct, double lac, double pers) {
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        for (int o = 0; o < oct; o++) {
            sum += perlin.noise(x * freq, y * freq) * amp;
            amp *= pers;
            freq *= lac;
        }
        return sum;
    }

    record WarpParams(int seed, double scale, double amp, int octaves, double persistence, double lacunarity) {}

    // ---------------------------------------------------------------------
    // Perlin implementation (package-private for reuse)
    // ---------------------------------------------------------------------

    static final class Perlin2D {
        private final int[] perm = new int[512];

        Perlin2D(long seed) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;

            SplitMix64 rng = new SplitMix64(seed);
            for (int i = 255; i > 0; i--) {
                int j = (int) ((rng.nextLong() >>> 1) % (i + 1));
                int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
            }

            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        double noise(double x, double y) {
            int xi = fastFloor(x) & 255;
            int yi = fastFloor(y) & 255;

            int x0 = fastFloor(x);
            int y0 = fastFloor(y);

            double xf = x - x0;
            double yf = y - y0;

            double u = fade(xf);
            double v = fade(yf);

            int aa = perm[xi + perm[yi]];
            int ab = perm[xi + perm[yi + 1]];
            int ba = perm[xi + 1 + perm[yi]];
            int bb = perm[xi + 1 + perm[yi + 1]];

            double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
            double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
            return lerp(x1, x2, v);
        }

        private static int fastFloor(double x) {
            int xi = (int) x;
            return x < xi ? xi - 1 : xi;
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private static double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = (h < 4) ? x : y;
            double v = (h < 4) ? y : x;
            return (((h & 1) == 0) ? u : -u) + (((h & 2) == 0) ? v : -v);
        }
    }

    private static final class SplitMix64 {
        private long state;

        SplitMix64(long seed) { this.state = seed; }

        long nextLong() {
            long z = (state += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }
}