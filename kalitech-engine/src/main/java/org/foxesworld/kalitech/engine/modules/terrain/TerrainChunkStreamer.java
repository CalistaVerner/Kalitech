package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.SurfaceRegistry;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Chunked terrain streamer with LOD rings.
 * <p>
 * This is an engine-side utility: it owns chunk surfaces and can be ticked from JS or Java.
 * <p>
 * Design:
 * - World is divided into (size-1) grid chunks in heightmap space.
 * - Each ring can specify its own chunk size (resolution) to keep far terrain cheaper.
 * - Seamless sampling is achieved by using noise offsets based on global sample coordinates.
 * <p>
 * NOTE:
 * - This is not a full open-world streaming system (no paging IO), but a solid base.
 */
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

    // Active chunks by key
    private final Map<Long, Chunk> chunks = new HashMap<>();

    public TerrainChunkStreamer(EngineApiImpl engine, SurfaceRegistry registry, Value cfg) {
        this.engine = engine;
        this.registry = registry;
        this.noise = new TerrainNoise();

        this.name = str(cfg, "name", "terrain_stream");
        this.patchSize = clampInt(num(cfg, "patchSize", TerrainDefaults.PATCH_SIZE), 17, 257);
        this.xzScale = (float) num(cfg, "xzScale", TerrainDefaults.XZ_SCALE);
        this.yScale = (float) num(cfg, "yScale", TerrainDefaults.Y_SCALE);

        this.noiseCfg = NoiseCfg.from(cfg);
        this.rings = Ring.parseRings(cfg);
    }

    private static long key(int cx, int cz, int size) {
        // pack: 21 bits each coord + 10 bits size (enough for <=8193)
        long x = (cx & 0x1FFFFF);
        long z = (cz & 0x1FFFFF);
        long s = (size & 0x3FF);
        return (s << 42) | (x << 21) | z;
    }

    /**
     * Update streamer around a focus point.
     * cfg:
     * - x: world x
     * - z: world z
     */
    @HostAccess.Export
    public void update(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("streamer.update: cfg is null");
        double fx = num(cfg, "x", Double.NaN);
        double fz = num(cfg, "z", Double.NaN);
        if (!Double.isFinite(fx) || !Double.isFinite(fz))
            throw new IllegalArgumentException("streamer.update: x/z required");

        // Determine base chunk index in world space.
        // Each chunk spans (size-1) samples * xzScale.
        int baseSize = rings[0].size;
        double chunkWorld = (baseSize - 1) * xzScale;

        int cx = (int) Math.floor(fx / chunkWorld);
        int cz = (int) Math.floor(fz / chunkWorld);

        Set<Long> want = new HashSet<>();

        for (Ring ring : rings) {
            int r = ring.radiusChunks;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    int mx = cx + dx;
                    int mz = cz + dz;
                    // ring is square; optional circular filter
                    if (ring.circular) {
                        if ((dx * dx + dz * dz) > (r * r)) continue;
                    }
                    long key = key(mx, mz, ring.size);
                    want.add(key);
                    if (!chunks.containsKey(key)) {
                        createChunk(mx, mz, ring);
                    }
                }
            }
        }

        // Unload chunks not needed
        chunks.keySet().removeIf(k -> {
            if (want.contains(k)) return false;
            Chunk c = chunks.get(k);
            if (c != null) destroyChunk(c);
            return true;
        });
    }

    @HostAccess.Export
    public int loadedChunks() {
        return chunks.size();
    }

    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void destroy() {
        for (Chunk c : chunks.values()) destroyChunk(c);
        chunks.clear();
    }

    private void createChunk(int cx, int cz, Ring ring) {
        int size = ring.size;

        // Global sample offset in heightmap space so tiles stitch.
        // Using base ring's size for world-to-sample mapping keeps continuity.
        int baseSize = rings[0].size;
        int sampleStride = (baseSize - 1);

        double offX = (double) cx * sampleStride;
        double offZ = (double) cz * sampleStride;

        float[] heights = noiseCfg.generate(noise, ring.type, size, offX, offZ);
        TerrainQuad tq = new TerrainQuad(name + "_" + cx + "_" + cz + "_" + size, patchSize, size, heights);
        tq.setLocalScale(xzScale, yScale, xzScale);
        tq.setLocalTranslation((float) (cx * (baseSize - 1) * xzScale), 0f, (float) (cz * (baseSize - 1) * xzScale));

        SurfaceApi.SurfaceHandle h = registry.register(tq, "terrain_chunk", engine.surface());
        registry.attachToRoot(h.id());

        long key = key(cx, cz, size);
        chunks.put(key, new Chunk(cx, cz, size, h));
    }

    private void destroyChunk(Chunk c) {
        Spatial s = registry.get(c.handle.id());
        if (s != null) {
            // detach then unregister
            registry.detachFromParent(c.handle.id());
        }
        registry.destroy(c.handle.id());
    }

    private record Chunk(int cx, int cz, int size, SurfaceApi.SurfaceHandle handle) {
    }

    // ---------------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------------

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

        static NoiseCfg from(Value cfg) {
            Value gen = member(cfg, "gen");
            Value g = (gen != null && !gen.isNull()) ? gen : cfg;
            int seed = i32(g, "seed", 0);
            double scale = Math.max(0.0001, num(g, "scale", 256.0));
            int oct = Math.max(1, i32(g, "octaves", 6));
            double pers = num(g, "persistence", 0.5);
            double lac = num(g, "lacunarity", 2.0);
            boolean normalize = bool(g, "normalize", true);
            TerrainNoise.WarpParams warp = null;
            Value warpV = member(g, "warp");
            if (warpV != null && !warpV.isNull() && bool(warpV, "enabled", true)) {
                int wSeed = i32(warpV, "seed", seed ^ 0x9E37_79B9);
                double wScale = Math.max(0.0001, num(warpV, "scale", 32.0));
                double wAmp = Math.max(0.0, num(warpV, "amp", 12.0));
                int wOct = Math.max(1, i32(warpV, "octaves", 3));
                double wPers = num(warpV, "persistence", 0.5);
                double wLac = num(warpV, "lacunarity", 2.0);
                warp = new TerrainNoise.WarpParams(wSeed, wScale, wAmp, wOct, wPers, wLac);
            }
            return new NoiseCfg(seed, scale, oct, pers, lac, normalize, warp);
        }

        float[] generate(TerrainNoise noise, TerrainNoise.Type type, int size, double sampleOffsetX, double sampleOffsetZ) {
            // Offsets shift the noise sampling so chunks stitch.
            // We keep offsets in noise sample space (not world space), consistent with TerrainNoise.
            return TerrainNoise.generate(
                    type,
                    size,
                    seed,
                    scale,
                    octaves,
                    persistence,
                    lacunarity,
                    sampleOffsetX,
                    sampleOffsetZ,
                    normalize,
                    1.0,
                    0.0,
                    warp,
                    new TerrainNoise.Progress(
                            true,
                            10,
                            "noise/" + type + (warp != null ? "+warp" : "") + "/size=" + size
                    )
            );
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

        static Ring[] parseRings(Value cfg) {
            Value v = member(cfg, "rings");
            if (v == null || v.isNull() || !v.hasArrayElements() || v.getArraySize() == 0) {
                // default 2 rings
                return new Ring[]{
                        new Ring(1, 129, TerrainNoise.Type.RIDGED, true),
                        new Ring(3, 65, TerrainNoise.Type.PERLIN, true)
                };
            }

            int n = (int) Math.min(v.getArraySize(), 8);
            Ring[] out = new Ring[n];
            for (int i = 0; i < n; i++) {
                Value r = v.getArrayElement(i);
                int rad = Math.max(0, i32(r, "radius", 1));
                int size = Math.max(33, i32(r, "size", 129));
                String t = str(r, "type", "perlin");
                TerrainNoise.Type type = (t != null && t.equalsIgnoreCase("ridged")) ? TerrainNoise.Type.RIDGED : TerrainNoise.Type.PERLIN;
                boolean circular = bool(r, "circular", true);
                out[i] = new Ring(rad, size, type, circular);
            }
            return out;
        }
    }
}