/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.scene.Spatial
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainNoise;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainChunkStreamer {
    private final EngineApiImpl engine;
    private final SurfaceRegistry registry;
    private final TerrainNoise noise;
    private final String name;
    private final int patchSize;
    private final float xzScale;
    private final float yScale;
    private final NoiseCfg noiseCfg;
    private final Ring[] rings;
    private final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();

    public TerrainChunkStreamer(EngineApiImpl engine, SurfaceRegistry registry, LuaValueRef cfg) {
        this.engine = engine;
        this.registry = registry;
        this.noise = new TerrainNoise();
        this.name = LuaCfg.str((LuaValueRef)cfg, (String)"name", (String)"terrain_stream");
        this.patchSize = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)cfg, (String)"patchSize", (double)65.0), (int)17, (int)257);
        this.xzScale = (float)LuaCfg.num((LuaValueRef)cfg, (String)"xzScale", (double)2.0);
        this.yScale = (float)LuaCfg.num((LuaValueRef)cfg, (String)"yScale", (double)1.0);
        this.noiseCfg = NoiseCfg.from(cfg);
        this.rings = Ring.parseRings(cfg);
    }

    private static long key(int cx, int cz, int size) {
        long x = cx & 0x1FFFFF;
        long z = cz & 0x1FFFFF;
        long s = size & 0x3FF;
        return s << 42 | x << 21 | z;
    }

    @LuaExport
    public void update(LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("streamer.update: cfg is null");
        }
        double fx = LuaCfg.num((LuaValueRef)cfg, (String)"x", (double)Double.NaN);
        double fz = LuaCfg.num((LuaValueRef)cfg, (String)"z", (double)Double.NaN);
        if (!Double.isFinite(fx) || !Double.isFinite(fz)) {
            throw new IllegalArgumentException("streamer.update: x/z required");
        }
        int baseSize = this.rings[0].size;
        double chunkWorld = (float)(baseSize - 1) * this.xzScale;
        int cx = (int)Math.floor(fx / chunkWorld);
        int cz = (int)Math.floor(fz / chunkWorld);
        HashSet<Long> want = new HashSet<Long>();
        for (Ring ring : this.rings) {
            int r = ring.radiusChunks;
            for (int dz = -r; dz <= r; ++dz) {
                for (int dx = -r; dx <= r; ++dx) {
                    int mx = cx + dx;
                    int mz = cz + dz;
                    if (ring.circular && dx * dx + dz * dz > r * r) continue;
                    long key = TerrainChunkStreamer.key(mx, mz, ring.size);
                    want.add(key);
                    if (this.chunks.containsKey(key)) continue;
                    this.createChunk(mx, mz, ring);
                }
            }
        }
        this.chunks.keySet().removeIf(k -> {
            if (want.contains(k)) {
                return false;
            }
            Chunk c = this.chunks.get(k);
            if (c != null) {
                this.destroyChunk(c);
            }
            return true;
        });
    }

    @LuaExport
    public int loadedChunks() {
        return this.chunks.size();
    }

    @LuaExport
    public void destroy() {
        for (Chunk c : this.chunks.values()) {
            this.destroyChunk(c);
        }
        this.chunks.clear();
    }

    private void createChunk(int cx, int cz, Ring ring) {
        int size = ring.size;
        int baseSize = this.rings[0].size;
        int sampleStride = baseSize - 1;
        double offX = (double)cx * (double)sampleStride;
        double offZ = (double)cz * (double)sampleStride;
        float[] heights = this.noiseCfg.generate(this.noise, ring.type, size, offX, offZ);
        TerrainQuad tq = new TerrainQuad(this.name + "_" + cx + "_" + cz + "_" + size, this.patchSize, size, heights);
        tq.setLocalScale(this.xzScale, this.yScale, this.xzScale);
        tq.setLocalTranslation((float)(cx * (baseSize - 1)) * this.xzScale, 0.0f, (float)(cz * (baseSize - 1)) * this.xzScale);
        SurfaceApi.SurfaceHandle h = this.registry.register((Spatial)tq, "terrain_chunk", this.engine.surface());
        this.registry.attachToRoot(h.id());
        long key = TerrainChunkStreamer.key(cx, cz, size);
        this.chunks.put(key, new Chunk(cx, cz, size, h));
    }

    private void destroyChunk(Chunk c) {
        Spatial s = this.registry.get(c.handle.id());
        if (s != null) {
            this.registry.detachFromParent(c.handle.id());
        }
        this.registry.destroy(c.handle.id());
    }

    private static final class NoiseCfg {
        final int seed;
        final double scale;
        final int octaves;
        final double persistence;
        final double lacunarity;
        final boolean normalize;
        final TerrainNoise.WarpParams warp;

        private NoiseCfg(int seed, double scale, int oct, double pers, double lac, boolean norm, TerrainNoise.WarpParams warp) {
            this.seed = seed;
            this.scale = scale;
            this.octaves = oct;
            this.persistence = pers;
            this.lacunarity = lac;
            this.normalize = norm;
            this.warp = warp;
        }

        static NoiseCfg from(LuaValueRef cfg) {
            LuaValueRef gen = LuaCfg.member((LuaValueRef)cfg, (String)"gen");
            LuaValueRef g = gen != null && !gen.isNull() ? gen : cfg;
            int seed = LuaCfg.i32((LuaValueRef)g, (String)"seed", (int)0);
            double scale = Math.max(1.0E-4, LuaCfg.num((LuaValueRef)g, (String)"scale", (double)256.0));
            int oct = Math.max(1, LuaCfg.i32((LuaValueRef)g, (String)"octaves", (int)6));
            double pers = LuaCfg.num((LuaValueRef)g, (String)"persistence", (double)0.5);
            double lac = LuaCfg.num((LuaValueRef)g, (String)"lacunarity", (double)2.0);
            boolean normalize = LuaCfg.bool((LuaValueRef)g, (String)"normalize", (boolean)true);
            TerrainNoise.WarpParams warp = null;
            LuaValueRef warpV = LuaCfg.member((LuaValueRef)g, (String)"warp");
            if (warpV != null && !warpV.isNull() && LuaCfg.bool((LuaValueRef)warpV, (String)"enabled", (boolean)true)) {
                int wSeed = LuaCfg.i32((LuaValueRef)warpV, (String)"seed", (int)(seed ^ 0x9E3779B9));
                double wScale = Math.max(1.0E-4, LuaCfg.num((LuaValueRef)warpV, (String)"scale", (double)32.0));
                double wAmp = Math.max(0.0, LuaCfg.num((LuaValueRef)warpV, (String)"amp", (double)12.0));
                int wOct = Math.max(1, LuaCfg.i32((LuaValueRef)warpV, (String)"octaves", (int)3));
                double wPers = LuaCfg.num((LuaValueRef)warpV, (String)"persistence", (double)0.5);
                double wLac = LuaCfg.num((LuaValueRef)warpV, (String)"lacunarity", (double)2.0);
                warp = new TerrainNoise.WarpParams(wSeed, wScale, wAmp, wOct, wPers, wLac);
            }
            return new NoiseCfg(seed, scale, oct, pers, lac, normalize, warp);
        }

        float[] generate(TerrainNoise noise, TerrainNoise.Type type, int size, double sampleOffsetX, double sampleOffsetZ) {
            return TerrainNoise.generate(type, size, this.seed, this.scale, this.octaves, this.persistence, this.lacunarity, sampleOffsetX, sampleOffsetZ, this.normalize, 1.0, 0.0, this.warp, new TerrainNoise.Progress(true, 10, "noise/" + String.valueOf((Object)type) + (this.warp != null ? "+warp" : "") + "/size=" + size));
        }
    }

    private static final class Ring {
        final int radiusChunks;
        final int size;
        final TerrainNoise.Type type;
        final boolean circular;

        Ring(int radiusChunks, int size, TerrainNoise.Type type, boolean circular) {
            this.radiusChunks = radiusChunks;
            this.size = size;
            this.type = type;
            this.circular = circular;
        }

        static Ring[] parseRings(LuaValueRef cfg) {
            LuaValueRef v = LuaCfg.member((LuaValueRef)cfg, (String)"rings");
            if (v == null || v.isNull() || !v.hasArrayElements() || v.getArraySize() == 0L) {
                return new Ring[]{new Ring(1, 129, TerrainNoise.Type.RIDGED, true), new Ring(3, 65, TerrainNoise.Type.PERLIN, true)};
            }
            int n = (int)Math.min(v.getArraySize(), 8L);
            Ring[] out = new Ring[n];
            for (int i = 0; i < n; ++i) {
                LuaValueRef r = v.getArrayElement((long)i);
                int rad = Math.max(0, LuaCfg.i32((LuaValueRef)r, (String)"radius", (int)1));
                int size = Math.max(33, LuaCfg.i32((LuaValueRef)r, (String)"size", (int)129));
                String t = LuaCfg.str((LuaValueRef)r, (String)"type", (String)"perlin");
                TerrainNoise.Type type = t != null && t.equalsIgnoreCase("ridged") ? TerrainNoise.Type.RIDGED : TerrainNoise.Type.PERLIN;
                boolean circular = LuaCfg.bool((LuaValueRef)r, (String)"circular", (boolean)true);
                out[i] = new Ring(rad, size, type, circular);
            }
            return out;
        }
    }

    private record Chunk(int cx, int cz, int size, SurfaceApi.SurfaceHandle handle) {
    }
}

