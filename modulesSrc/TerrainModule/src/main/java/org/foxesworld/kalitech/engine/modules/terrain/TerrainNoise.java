/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainNoise {
    private static final Logger log = LogManager.getLogger(TerrainNoise.class);

    private static float[] fieldFrom(LuaValueRef cfg, String key, int size, int defaultSeed, double defaultScale, boolean debug, int debugStepPct, String name) {
        LuaValueRef sub = LuaCfg.member((LuaValueRef)cfg, (String)key);
        if (sub == null || sub.isNull()) {
            Progress p = new Progress(debug, debugStepPct, "noise/" + name + "/default");
            return TerrainNoise.generate(Type.PERLIN, size, defaultSeed, defaultScale, 4, 0.5, 2.0, 0.0, 0.0, true, 1.0, 0.0, null, p);
        }
        LuaValueRef s = sub;
        int seed = LuaCfg.i32((LuaValueRef)s, (String)"seed", (int)defaultSeed);
        double scale = Math.max(1.0E-4, LuaCfg.num((LuaValueRef)s, (String)"scale", (double)defaultScale));
        int oct = Math.max(1, LuaCfg.i32((LuaValueRef)s, (String)"octaves", (int)4));
        double pers = LuaCfg.num((LuaValueRef)s, (String)"persistence", (double)0.5);
        double lac = LuaCfg.num((LuaValueRef)s, (String)"lacunarity", (double)2.0);
        double ox = LuaCfg.num((LuaValueRef)s, (String)"offsetX", (double)0.0);
        double oz = LuaCfg.num((LuaValueRef)s, (String)"offsetZ", (double)0.0);
        Progress p = new Progress(debug, debugStepPct, "noise/" + name);
        return TerrainNoise.generate(Type.PERLIN, size, seed, scale, oct, pers, lac, ox, oz, true, 1.0, 0.0, null, p);
    }

    static float[] generate(Type type, int size, int seed, double scale, int octaves, double persistence, double lacunarity, double ox, double oz, boolean normalize, double amplitude, double base, WarpParams warp, Progress prog) {
        if (prog != null) {
            prog.start("start seed=" + seed + " scale=" + scale + " oct=" + octaves + " pers=" + persistence + " lac=" + lacunarity + " normalize=" + normalize + " amp=" + amplitude + " base=" + base + (String)(warp != null ? " warp{seed=" + warp.seed + " scale=" + warp.scale + " amp=" + warp.amp + " oct=" + warp.octaves + " pers=" + warp.persistence + " lac=" + warp.lacunarity + "}" : ""));
            prog.stage("init", 0);
        }
        float[] out = new float[size * size];
        double maxAmp = 0.0;
        double amp = 1.0;
        for (int o = 0; o < octaves; ++o) {
            maxAmp += amp;
            amp *= persistence;
        }
        if (maxAmp < 1.0E-9) {
            maxAmp = 1.0;
        }
        Perlin2D basePerlin = new Perlin2D(seed);
        if (prog != null) {
            prog.stage("init-done", 10);
        }
        Perlin2D warpPerlinX = null;
        Perlin2D warpPerlinZ = null;
        if (warp != null) {
            if (prog != null) {
                prog.stage("warp-setup", 12);
            }
            warpPerlinX = new Perlin2D(warp.seed ^ 0x1234ABCD);
            warpPerlinZ = new Perlin2D(warp.seed ^ 0xCDEF5678);
            if (prog != null) {
                prog.stage("warp-ready", 20);
            }
        } else if (prog != null) {
            prog.stage("no-warp", 20);
        }
        if (prog != null) {
            prog.stage("generate", 20);
        }
        for (int z = 0; z < size; ++z) {
            for (int x = 0; x < size; ++x) {
                double v;
                double wx = x;
                double wz = z;
                if (warp != null && warp.amp > 0.0) {
                    double dx = TerrainNoise.fbm(warpPerlinX, wx / warp.scale + ox, wz / warp.scale + oz, warp.octaves, warp.lacunarity, warp.persistence);
                    double dz = TerrainNoise.fbm(warpPerlinZ, wx / warp.scale - ox, wz / warp.scale - oz, warp.octaves, warp.lacunarity, warp.persistence);
                    wx += dx * warp.amp;
                    wz += dz * warp.amp;
                }
                double amp2 = 1.0;
                double freq = 1.0;
                double sum = 0.0;
                for (int o = 0; o < octaves; ++o) {
                    double nx = wx / scale * freq + ox;
                    double nz = wz / scale * freq + oz;
                    double n = basePerlin.noise(nx, nz);
                    if (type == Type.RIDGED) {
                        double r = 1.0 - Math.abs(n);
                        n = r * r;
                    }
                    sum += n * amp2;
                    amp2 *= persistence;
                    freq *= lacunarity;
                }
                if (normalize) {
                    v = type == Type.PERLIN ? (sum / maxAmp + 1.0) * 0.5 : sum / maxAmp;
                    v = TerrainNoise.clamp01(v);
                } else {
                    v = sum;
                }
                v = base + v * amplitude;
                out[z * size + x] = (float)v;
            }
            if (prog == null) continue;
            prog.stageRange(20, 90, z + 1, size);
        }
        if (prog != null) {
            prog.stage("finalize", 90);
        }
        if (prog != null) {
            prog.abs(100);
        }
        if (prog != null) {
            prog.done("generated");
        }
        return out;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = LuaCfg.clamp((double)t, (double)0.0, (double)1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double band(double x, double a, double b) {
        double t = (x - a) / (b - a);
        return TerrainNoise.clamp01(1.0 - Math.abs(t * 2.0 - 1.0));
    }

    private static double fbm(Perlin2D perlin, double x, double y, int oct, double lac, double pers) {
        Objects.requireNonNull(perlin, "perlin");
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        for (int o = 0; o < oct; ++o) {
            sum += perlin.noise(x * freq, y * freq) * amp;
            amp *= pers;
            freq *= lac;
        }
        return sum;
    }

    public float[] perlinHeights(LuaValueRef cfg) {
        return this.heights(Type.PERLIN, cfg);
    }

    public float[] ridgedHeights(LuaValueRef cfg) {
        return this.heights(Type.RIDGED, cfg);
    }

    public float[] perlinWarpHeights(LuaValueRef cfg) {
        return this.heights(Type.PERLIN, cfg, true);
    }

    public float[] ridgedWarpHeights(LuaValueRef cfg) {
        return this.heights(Type.RIDGED, cfg, true);
    }

    public Map<String, float[]> biomeMasks(LuaValueRef cfg) {
        float[] elev;
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("biome cfg is null");
        }
        int size = LuaCfg.i32((LuaValueRef)cfg, (String)"size", (int)0);
        if (size <= 1) {
            throw new IllegalArgumentException("biome.size must be > 1");
        }
        boolean debug = LuaCfg.bool((LuaValueRef)cfg, (String)"debug", (boolean)false);
        int debugStep = Math.max(1, LuaCfg.i32((LuaValueRef)cfg, (String)"debugStepPct", (int)2));
        Progress bio = new Progress(debug, debugStep, "biomes/size=" + size);
        bio.start("start");
        bio.stage("init", 5);
        LuaValueRef heightsV = LuaCfg.member((LuaValueRef)cfg, (String)"heights");
        if (heightsV != null && !heightsV.isNull() && heightsV.hasArrayElements()) {
            bio.stage("elevation-read", 10);
            elev = LuaCfg.readFloatArray((LuaValueRef)heightsV);
        } else {
            LuaValueRef elevCfg = LuaCfg.member((LuaValueRef)cfg, (String)"elevation");
            if (elevCfg == null || elevCfg.isNull()) {
                elevCfg = cfg;
            }
            bio.stage("elevation-generate", 10);
            elev = this.heights(Type.PERLIN, elevCfg);
        }
        if (elev.length != size * size) {
            throw new IllegalArgumentException("biome: elevation/heightmap length must be size*size");
        }
        int seed = LuaCfg.i32((LuaValueRef)cfg, (String)"seed", (int)0);
        double tempLapse = LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"tempLapse", (double)0.6), (double)0.0, (double)2.0);
        double seaLevel = LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"seaLevel", (double)0.35), (double)0.0, (double)1.0);
        bio.stage("temp-field", 20);
        float[] temp = TerrainNoise.fieldFrom(cfg, "temp", size, seed ^ 0xA2C21B3D, 256.0, debug, debugStep, "temp");
        bio.stage("moist-field", 30);
        float[] moist = TerrainNoise.fieldFrom(cfg, "moist", size, seed ^ 0x77D19F21, 256.0, debug, debugStep, "moist");
        bio.stage("lapse", 30);
        for (int i = 0; i < temp.length; ++i) {
            double e = elev[i];
            double t = temp[i];
            temp[i] = (float)TerrainNoise.clamp01(t -= e * tempLapse);
            if ((i & 0x1FFF) != 0) continue;
            bio.stageRange(30, 50, i, temp.length);
        }
        bio.abs(50);
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
        for (int i = 0; i < temp.length; ++i) {
            double e = elev[i];
            double t = temp[i];
            double m = moist[i];
            double oce = TerrainNoise.smoothstep(seaLevel, seaLevel + 0.06, seaLevel - e);
            oce = TerrainNoise.clamp01(oce);
            double bch = (1.0 - oce) * TerrainNoise.band(e, seaLevel, seaLevel + 0.06);
            double mtn = TerrainNoise.smoothstep(0.72, 0.9, e);
            double snw = TerrainNoise.smoothstep(0.68, 0.82, 1.0 - t) * mtn;
            double tnd = TerrainNoise.smoothstep(0.55, 0.75, 1.0 - t) * (1.0 - mtn);
            double dst = (1.0 - oce) * TerrainNoise.smoothstep(0.55, 0.85, t) * TerrainNoise.smoothstep(0.65, 0.95, 1.0 - m);
            double swp = (1.0 - oce) * TerrainNoise.band(e, seaLevel + 0.02, seaLevel + 0.12) * TerrainNoise.smoothstep(0.55, 0.95, m);
            double frs = (1.0 - oce) * TerrainNoise.smoothstep(0.35, 0.75, m) * TerrainNoise.smoothstep(0.2, 0.7, t) * (1.0 - mtn);
            double grs = (1.0 - oce) * TerrainNoise.smoothstep(0.25, 0.65, m) * TerrainNoise.smoothstep(0.35, 0.85, t) * (1.0 - frs) * (1.0 - dst);
            ocean[i] = (float)oce;
            beach[i] = (float)TerrainNoise.clamp01(bch);
            desert[i] = (float)TerrainNoise.clamp01(dst);
            grass[i] = (float)TerrainNoise.clamp01(grs);
            forest[i] = (float)TerrainNoise.clamp01(frs);
            swamp[i] = (float)TerrainNoise.clamp01(swp);
            tundra[i] = (float)TerrainNoise.clamp01(tnd);
            snow[i] = (float)TerrainNoise.clamp01(snw);
            mountain[i] = (float)TerrainNoise.clamp01(mtn);
            if ((i & 0x1FFF) != 0) continue;
            bio.stageRange(50, 100, i, temp.length);
        }
        bio.done("masks built");
        HashMap<String, float[]> out = new HashMap<String, float[]>();
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

    public float[] heights(Type type, LuaValueRef cfg) {
        return this.heights(type, cfg, false);
    }

    private float[] heights(Type type, LuaValueRef cfg, boolean forceWarp) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("noise cfg is null");
        }
        int size = LuaCfg.i32((LuaValueRef)cfg, (String)"size", (int)0);
        if (size <= 1) {
            throw new IllegalArgumentException("noise.size must be > 1");
        }
        int seed = LuaCfg.i32((LuaValueRef)cfg, (String)"seed", (int)0);
        double scale = Math.max(1.0E-4, LuaCfg.num((LuaValueRef)cfg, (String)"scale", (double)64.0));
        int octaves = Math.max(1, LuaCfg.i32((LuaValueRef)cfg, (String)"octaves", (int)5));
        double persistence = LuaCfg.num((LuaValueRef)cfg, (String)"persistence", (double)0.5);
        double lacunarity = LuaCfg.num((LuaValueRef)cfg, (String)"lacunarity", (double)2.0);
        double ox = LuaCfg.num((LuaValueRef)cfg, (String)"offsetX", (double)0.0);
        double oz = LuaCfg.num((LuaValueRef)cfg, (String)"offsetZ", (double)0.0);
        boolean normalize = LuaCfg.bool((LuaValueRef)cfg, (String)"normalize", (boolean)true);
        double amplitude = LuaCfg.num((LuaValueRef)cfg, (String)"amplitude", (double)1.0);
        double base = LuaCfg.num((LuaValueRef)cfg, (String)"base", (double)0.0);
        boolean debug = LuaCfg.bool((LuaValueRef)cfg, (String)"debug", (boolean)false);
        int debugStep = Math.max(1, LuaCfg.i32((LuaValueRef)cfg, (String)"debugStepPct", (int)1));
        LuaValueRef warpV = LuaCfg.member((LuaValueRef)cfg, (String)"warp");
        boolean warpEnabled = forceWarp || warpV != null && !warpV.isNull() && LuaCfg.bool((LuaValueRef)warpV, (String)"enabled", (boolean)true);
        WarpParams warp = null;
        if (warpEnabled) {
            int wSeed = warpV != null && !warpV.isNull() ? LuaCfg.i32((LuaValueRef)warpV, (String)"seed", (int)(seed ^ 0x9E3779B9)) : seed ^ 0x9E3779B9;
            warp = new WarpParams(wSeed, Math.max(1.0E-4, warpV != null && !warpV.isNull() ? LuaCfg.num((LuaValueRef)warpV, (String)"scale", (double)32.0) : 32.0), Math.max(0.0, warpV != null && !warpV.isNull() ? LuaCfg.num((LuaValueRef)warpV, (String)"amp", (double)12.0) : 12.0), Math.max(1, warpV != null && !warpV.isNull() ? LuaCfg.i32((LuaValueRef)warpV, (String)"octaves", (int)3) : 3), warpV != null && !warpV.isNull() ? LuaCfg.num((LuaValueRef)warpV, (String)"persistence", (double)0.5) : 0.5, warpV != null && !warpV.isNull() ? LuaCfg.num((LuaValueRef)warpV, (String)"lacunarity", (double)2.0) : 2.0);
        }
        Progress prog = new Progress(debug, debugStep, "noise/" + String.valueOf((Object)type) + (warp != null ? "+warp" : "") + "/size=" + size);
        return TerrainNoise.generate(type, size, seed, scale, octaves, persistence, lacunarity, ox, oz, normalize, amplitude, base, warp, prog);
    }

    static final class Progress {
        final boolean enabled;
        final int stepPct;
        final String name;
        int lastBucket = Integer.MIN_VALUE;

        Progress(boolean enabled, int stepPct, String name) {
            this.enabled = enabled;
            this.stepPct = Math.max(1, stepPct);
            this.name = name;
        }

        private static int clampInt(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        void start(String msg) {
            if (this.enabled) {
                log.debug("[terrain] {}: {}", (Object)this.name, (Object)msg);
            }
        }

        void abs(int pct) {
            if (!this.enabled) {
                return;
            }
            int p = pct < 0 ? 0 : (pct > 100 ? 100 : pct);
            int bucket = p / this.stepPct;
            if (bucket != this.lastBucket) {
                this.lastBucket = bucket;
                log.debug("[terrain] {}: {}%", (Object)this.name, (Object)p);
            }
        }

        void stageRange(int fromPct, int toPct, int done, int total) {
            if (!this.enabled || total <= 0) {
                return;
            }
            int lo = Progress.clampInt(fromPct, 0, 100);
            int hi = Progress.clampInt(toPct, 0, 100);
            if (hi < lo) {
                int t = lo;
                lo = hi;
                hi = t;
            }
            int span = hi - lo;
            int frac = (int)((long)done * 100L / (long)total);
            int p = lo + span * frac / 100;
            this.abs(p);
        }

        void stage(String label, int pct) {
            if (!this.enabled) {
                return;
            }
            log.debug("[terrain] {}: stage={} ({}%)", (Object)this.name, (Object)label, (Object)Progress.clampInt(pct, 0, 100));
            this.abs(pct);
        }

        void done(String msg) {
            if (this.enabled) {
                log.debug("[terrain] {}: {} (100%)", (Object)this.name, (Object)msg);
            }
            this.abs(100);
        }
    }

    public static enum Type {
        PERLIN,
        RIDGED;

    }

    record WarpParams(int seed, double scale, double amp, int octaves, double persistence, double lacunarity) {
    }

    static final class Perlin2D {
        private final int[] perm = new int[512];

        Perlin2D(long seed) {
            int i;
            int[] p = new int[256];
            for (int i2 = 0; i2 < 256; ++i2) {
                p[i2] = i2;
            }
            SplitMix64 rng = new SplitMix64(seed);
            for (i = 255; i > 0; --i) {
                int j = (int)((rng.nextLong() >>> 1) % (long)(i + 1));
                int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
            }
            for (i = 0; i < 512; ++i) {
                this.perm[i] = p[i & 0xFF];
            }
        }

        private static int fastFloor(double x) {
            int xi = (int)x;
            return x < (double)xi ? xi - 1 : xi;
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private static double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = h < 4 ? x : y;
            double v = h < 4 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        double noise(double x, double y) {
            int xi = Perlin2D.fastFloor(x) & 0xFF;
            int yi = Perlin2D.fastFloor(y) & 0xFF;
            int x0 = Perlin2D.fastFloor(x);
            int y0 = Perlin2D.fastFloor(y);
            double xf = x - (double)x0;
            double yf = y - (double)y0;
            double u = Perlin2D.fade(xf);
            double v = Perlin2D.fade(yf);
            int aa = this.perm[xi + this.perm[yi]];
            int ab = this.perm[xi + this.perm[yi + 1]];
            int ba = this.perm[xi + 1 + this.perm[yi]];
            int bb = this.perm[xi + 1 + this.perm[yi + 1]];
            double x1 = Perlin2D.lerp(Perlin2D.grad(aa, xf, yf), Perlin2D.grad(ba, xf - 1.0, yf), u);
            double x2 = Perlin2D.lerp(Perlin2D.grad(ab, xf, yf - 1.0), Perlin2D.grad(bb, xf - 1.0, yf - 1.0), u);
            return Perlin2D.lerp(x1, x2, v);
        }
    }

    private static final class SplitMix64 {
        private long state;

        SplitMix64(long seed) {
            this.state = seed;
        }

        long nextLong() {
            long z = this.state += -7046029254386353131L;
            z = (z ^ z >>> 30) * -4658895280553007687L;
            z = (z ^ z >>> 27) * -7723592293110705685L;
            return z ^ z >>> 31;
        }
    }
}

