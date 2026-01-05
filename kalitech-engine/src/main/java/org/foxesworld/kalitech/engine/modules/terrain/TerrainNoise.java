package org.foxesworld.kalitech.engine.modules.terrain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Procedural noise for terrain:
 *  - Perlin FBM
 *  - Ridged multifractal
 *  - Domain warp
 *  - Biome masks (temperature/moisture + derived biome weights)
 *
 * Debug:
 *  - cfg.debug: boolean
 *  - cfg.debugStepPct: int (default 1 for noise, 2 for biomes)
 *
 * Stage progress (absolute):
 *  - init ~10%
 *  - warp setup ~20%
 *  - main generation ~90%
 *  - finalize ~100%
 */
public final class TerrainNoise {

    private static final Logger log = LogManager.getLogger(TerrainNoise.class);

    public enum Type { PERLIN, RIDGED }

    // ---------------------------------------------------------------------
    // Debug progress helper (throttled)
    // ---------------------------------------------------------------------

    static final class Progress {
        final boolean enabled;
        final int stepPct;     // 1 => log each 1%
        final String name;
        int lastBucket = Integer.MIN_VALUE;

        Progress(boolean enabled, int stepPct, String name) {
            this.enabled = enabled;
            this.stepPct = Math.max(1, stepPct);
            this.name = name;
        }

        void start(String msg) {
            if (enabled) log.debug("[terrain] {}: {}", name, msg);
        }

        /** log absolute progress 0..100 */
        void abs(int pct) {
            if (!enabled) return;
            int p = (pct < 0) ? 0 : (pct > 100) ? 100 : pct;
            int bucket = p / stepPct;
            if (bucket != lastBucket) {
                lastBucket = bucket;
                log.debug("[terrain] {}: {}%", name, p);
            }
        }

        /** map a 0..total progress into a [from..to] stage range */
        void stageRange(int fromPct, int toPct, int done, int total) {
            if (!enabled || total <= 0) return;
            int lo = clampInt(fromPct, 0, 100);
            int hi = clampInt(toPct,   0, 100);
            if (hi < lo) { int t = lo; lo = hi; hi = t; }

            int span = hi - lo;
            int frac = (int) ((done * 100L) / (long) total); // 0..100
            int p = lo + (span * frac) / 100;
            abs(p);
        }

        void stage(String label, int pct) {
            if (!enabled) return;
            log.debug("[terrain] {}: stage={} ({}%)", name, label, clampInt(pct, 0, 100));
            abs(pct);
        }

        void done(String msg) {
            if (enabled) log.debug("[terrain] {}: {} (100%)", name, msg);
            abs(100);
        }

        private static int clampInt(int v, int lo, int hi) {
            return (v < lo) ? lo : (v > hi) ? hi : v;
        }
    }

    // ---------------------------------------------------------------------
    // Public API (Value-based for Graal)
    // ---------------------------------------------------------------------

    public float[] perlinHeights(Value cfg) { return heights(Type.PERLIN, cfg); }
    public float[] ridgedHeights(Value cfg) { return heights(Type.RIDGED, cfg); }

    public float[] perlinWarpHeights(Value cfg) { return heights(Type.PERLIN, cfg, true); }
    public float[] ridgedWarpHeights(Value cfg) { return heights(Type.RIDGED, cfg, true); }

    /**
     * Generate biome masks.
     * cfg:
     *  - size: int (required)
     *  - seed?: int
     *  - elevation: noise cfg OR pass heights directly as cfg.heights
     *  - temp/moist: perlin cfg (subcfg)
     *  - tempLapse?: double (default 0.6)
     *  - seaLevel?: double (default 0.35)
     *  - debug?: bool
     *  - debugStepPct?: int
     */
    public Map<String, float[]> biomeMasks(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("biome cfg is null");
        int size = i32(cfg, "size", 0);
        if (size <= 1) throw new IllegalArgumentException("biome.size must be > 1");

        final boolean debug = bool(cfg, "debug", false);
        final int debugStep = Math.max(1, i32(cfg, "debugStepPct", 2));
        final Progress bio = new Progress(debug, debugStep, "biomes/size=" + size);

        bio.start("start");
        bio.stage("init", 5);

        // Elevation field
        float[] elev;
        Value heightsV = member(cfg, "heights");
        if (heightsV != null && !heightsV.isNull() && heightsV.hasArrayElements()) {
            bio.stage("elevation-read", 10);
            elev = readFloatArray(heightsV);
        } else {
            Value elevCfg = member(cfg, "elevation");
            if (elevCfg == null || elevCfg.isNull()) elevCfg = cfg;
            bio.stage("elevation-generate", 10);
            elev = heights(Type.PERLIN, elevCfg);
        }
        if (elev.length != size * size) {
            throw new IllegalArgumentException("biome: elevation/heightmap length must be size*size");
        }

        int seed = i32(cfg, "seed", 0);
        double tempLapse = clamp(num(cfg, "tempLapse", 0.6), 0.0, 2.0);
        double seaLevel = clamp(num(cfg, "seaLevel", 0.35), 0.0, 1.0);

        bio.stage("temp-field", 20);
        float[] temp = fieldFrom(cfg, "temp", size, seed ^ 0xA2C2_1B3D, 256.0, debug, debugStep, "temp");

        bio.stage("moist-field", 30);
        float[] moist = fieldFrom(cfg, "moist", size, seed ^ 0x77D1_9F21, 256.0, debug, debugStep, "moist");

        // lapse: 30..50
        bio.stage("lapse", 30);
        for (int i = 0; i < temp.length; i++) {
            double e = elev[i];
            double t = temp[i];
            t = t - (e * tempLapse);
            temp[i] = (float) clamp01(t);
            if ((i & 8191) == 0) bio.stageRange(30, 50, i, temp.length);
        }
        bio.abs(50);

        // masks build: 50..100
        bio.stage("masks", 50);

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

            double oce = smoothstep(seaLevel, seaLevel + 0.06, seaLevel - e);
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

            if ((i & 8191) == 0) bio.stageRange(50, 100, i, temp.length);
        }

        bio.done("masks built");

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

        double amplitude = num(cfg, "amplitude", 1.0);
        double base = num(cfg, "base", 0.0);

        final boolean debug = bool(cfg, "debug", false);
        final int debugStep = Math.max(1, i32(cfg, "debugStepPct", 1));

        // Domain warp
        Value warpV = member(cfg, "warp");
        boolean warpEnabled = forceWarp || (warpV != null && !warpV.isNull() && bool(warpV, "enabled", true));

        WarpParams warp = null;
        if (warpEnabled) {
            int wSeed = (warpV != null && !warpV.isNull())
                    ? i32(warpV, "seed", seed ^ 0x9E37_79B9)
                    : (seed ^ 0x9E37_79B9);

            warp = new WarpParams(
                    wSeed,
                    Math.max(0.0001, (warpV != null && !warpV.isNull()) ? num(warpV, "scale", 32.0) : 32.0),
                    Math.max(0.0,    (warpV != null && !warpV.isNull()) ? num(warpV, "amp", 12.0)   : 12.0),
                    Math.max(1,      (warpV != null && !warpV.isNull()) ? i32(warpV, "octaves", 3) : 3),
                    (warpV != null && !warpV.isNull()) ? num(warpV, "persistence", 0.5) : 0.5,
                    (warpV != null && !warpV.isNull()) ? num(warpV, "lacunarity", 2.0)  : 2.0
            );
        }

        Progress prog = new Progress(
                debug,
                debugStep,
                "noise/" + type + (warp != null ? "+warp" : "") + "/size=" + size
        );

        return generate(type, size, seed, scale, octaves, persistence, lacunarity, ox, oz,
                normalize, amplitude, base, warp, prog);
    }

    // ---------------------------------------------------------------------
    // Helpers: biome fields + math
    // ---------------------------------------------------------------------

    private static float[] fieldFrom(Value cfg, String key, int size, int defaultSeed, double defaultScale,
                                     boolean debug, int debugStepPct, String name) {
        Value sub = member(cfg, key);

        if (sub == null || sub.isNull()) {
            Progress p = new Progress(debug, debugStepPct, "noise/" + name + "/default");
            return generate(Type.PERLIN, size, defaultSeed, defaultScale, 4, 0.5, 2.0,
                    0.0, 0.0, true, 1.0, 0.0, null, p);
        }

        Value s = sub;
        int seed = i32(s, "seed", defaultSeed);
        double scale = Math.max(0.0001, num(s, "scale", defaultScale));
        int oct = Math.max(1, i32(s, "octaves", 4));
        double pers = num(s, "persistence", 0.5);
        double lac = num(s, "lacunarity", 2.0);
        double ox = num(s, "offsetX", 0.0);
        double oz = num(s, "offsetZ", 0.0);

        Progress p = new Progress(debug, debugStepPct, "noise/" + name);
        return generate(Type.PERLIN, size, seed, scale, oct, pers, lac, ox, oz,
                true, 1.0, 0.0, null, p);
    }

    // ---------------------------------------------------------------------
    // Generator (with stage progress)
    // ---------------------------------------------------------------------

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
                            WarpParams warp,
                            Progress prog) {

        // Stage plan:
        //  0..10  init & precompute
        // 10..20  warp setup (or skip fast to 20)
        // 20..90  main generation (rows progress)
        // 90..100 finalize
        if (prog != null) {
            prog.start("start seed=" + seed
                    + " scale=" + scale
                    + " oct=" + octaves
                    + " pers=" + persistence
                    + " lac=" + lacunarity
                    + " normalize=" + normalize
                    + " amp=" + amplitude
                    + " base=" + base
                    + (warp != null ? (" warp{seed=" + warp.seed + " scale=" + warp.scale + " amp=" + warp.amp
                    + " oct=" + warp.octaves + " pers=" + warp.persistence + " lac=" + warp.lacunarity + "}") : ""));
            prog.stage("init", 0);
        }

        float[] out = new float[size * size];

        // init precompute (0..10)
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

        if (prog != null) prog.stage("init-done", 10);

        // warp setup (10..20)
        Perlin2D warpPerlinX = null;
        Perlin2D warpPerlinZ = null;
        if (warp != null) {
            if (prog != null) prog.stage("warp-setup", 12);
            warpPerlinX = new Perlin2D(warp.seed ^ 0x1234ABCD);
            warpPerlinZ = new Perlin2D(warp.seed ^ 0xCDEF5678);
            if (prog != null) prog.stage("warp-ready", 20);
        } else {
            if (prog != null) prog.stage("no-warp", 20);
        }

        // main generation (20..90), per-row progress mapped into stage range
        if (prog != null) prog.stage("generate", 20);

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {

                double wx = x;
                double wz = z;

                if (warp != null && warp.amp > 0.0) {
                    double dx = fbm(warpPerlinX,
                            (wx / warp.scale) + ox, (wz / warp.scale) + oz,
                            warp.octaves, warp.lacunarity, warp.persistence);
                    double dz = fbm(warpPerlinZ,
                            (wx / warp.scale) - ox, (wz / warp.scale) - oz,
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

            if (prog != null) prog.stageRange(20, 90, z + 1, size);
        }

        // finalize (90..100)
        if (prog != null) prog.stage("finalize", 90);

        // (сейчас finalize почти пустой — оставлено место под future: remap/curve/erosion post-pass)
        if (prog != null) prog.abs(100);
        if (prog != null) prog.done("generated");

        return out;
    }

    // ---------------------------------------------------------------------
    // Math
    // ---------------------------------------------------------------------

    private static double clamp01(double v) { return (v < 0.0) ? 0.0 : (v > 1.0) ? 1.0 : v; }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double band(double x, double a, double b) {
        double t = (x - a) / (b - a);
        return clamp01(1.0 - Math.abs(t * 2.0 - 1.0));
    }

    private static double fbm(Perlin2D perlin, double x, double y, int oct, double lac, double pers) {
        Objects.requireNonNull(perlin, "perlin");
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
    // Perlin implementation
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