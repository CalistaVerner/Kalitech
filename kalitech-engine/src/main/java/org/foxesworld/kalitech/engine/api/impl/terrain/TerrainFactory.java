package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.math.Quaternion;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Array;
import java.util.Arrays;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.*;

public final class TerrainFactory {

    private final AssetManager assets;
    private final TerrainMaterial mat;

    public TerrainFactory(AssetManager assets) {
        this.assets = assets;
        this.mat = new TerrainMaterial(assets);
    }

    public TerrainQuad createTerrainFromHeightmap(Value cfg) {
        String heightmap = str(cfg, "heightmap", null);
        if (heightmap == null || heightmap.isBlank()) {
            throw new IllegalArgumentException("terrain.terrain: heightmap is required");
        }

        int patchSize = clampInt(num(cfg, "patchSize", TerrainDefaults.PATCH_SIZE), 17, 257);
        int size      = clampInt(num(cfg, "size", TerrainDefaults.SIZE), 33, 8193);
        float heightScale = (float) num(cfg, "heightScale", TerrainDefaults.HEIGHT_SCALE);
        float xzScale     = (float) num(cfg, "xzScale", TerrainDefaults.XZ_SCALE);

        Texture tex = assets.loadTexture(heightmap);
        AbstractHeightMap hm = new ImageBasedHeightMap(tex.getImage(), heightScale);
        hm.load();

        TerrainQuad tq = new TerrainQuad(str(cfg, "name", TerrainDefaults.NAME_TERRAIN), patchSize, size, hm.getHeightMap());
        tq.setLocalScale(xzScale, 1f, xzScale);

        boolean shadows = bool(cfg, "shadows", TerrainDefaults.SHADOWS_BOOL_DEFAULT);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        mat.applyTerrainDefault(tq, cfg);
        return tq;
    }

    public TerrainQuad createTerrainFromHeights(Value cfg) {
        Value heightsV = member(cfg, "heights");
        if (heightsV == null || heightsV.isNull()) {
            throw new IllegalArgumentException("terrain.terrainHeights: cfg.heights is required");
        }

        int size = clampInt(num(cfg, "size", 0), 33, 8193);
        if (size <= 0) throw new IllegalArgumentException("terrain.terrainHeights: cfg.size is required");

        long expectedL = (long) size * (long) size;
        if (expectedL > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("terrain.terrainHeights: size*size too large: " + expectedL);
        }
        int expected = (int) expectedL;

        // --- Read heights length safely (JS array OR host float[])
        int n = heightsLength(heightsV);
        if (n != expected) {
            //throw new IllegalArgumentException("terrain.terrainHeights: heights length must be size*size (" + expected + "), got " + n);
        }

        int patchSize = clampInt(num(cfg, "patchSize", TerrainDefaults.PATCH_SIZE), 17, 257);
        float xzScale = (float) num(cfg, "xzScale", TerrainDefaults.XZ_SCALE);
        float yScale  = (float) num(cfg, "yScale", TerrainDefaults.Y_SCALE);

        // --- Convert heights to float[] efficiently
        float[] heights = readHeightsToFloatArray(heightsV, expected);

        TerrainQuad tq = new TerrainQuad(str(cfg, "name", TerrainDefaults.NAME_TERRAIN), patchSize, size, heights);
        tq.setLocalScale(xzScale, yScale, xzScale);

        boolean shadows = bool(cfg, "shadows", TerrainDefaults.SHADOWS_BOOL_DEFAULT);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        mat.applyTerrainDefault(tq, cfg);
        return tq;
    }

    private static int heightsLength(Value v) {
        // JS Array / TypedArray path
        if (v.hasArrayElements()) {
            long sz = v.getArraySize();
            if (sz > Integer.MAX_VALUE) throw new IllegalArgumentException("heights too large: " + sz);
            return (int) sz;
        }

        // Host object path (Java float[] or other arrays)
        if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o == null) return -1;
            if (o instanceof float[] a) return a.length;
            if (o instanceof double[] a) return a.length;
            if (o.getClass().isArray()) return Array.getLength(o);
        }

        return -1;
    }

    private static float[] readHeightsToFloatArray(Value v, int expected) {
        // If it's already a Java float[] coming from host — fast path
        if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o instanceof float[] a) {
                // jME TerrainQuad may keep reference; safer to copy if source is reused/mutable elsewhere
                return Arrays.copyOf(a, expected);
            }
            if (o instanceof double[] a) {
                float[] out = new float[expected];
                for (int i = 0; i < expected; i++) out[i] = (float) a[i];
                return out;
            }
            if (o != null && o.getClass().isArray()) {
                float[] out = new float[expected];
                for (int i = 0; i < expected; i++) {
                    Object el = Array.get(o, i);
                    out[i] = (el instanceof Number n) ? n.floatValue() : 0f;
                }
                return out;
            }
        }

        // JS Array / TypedArray path
        if (!v.hasArrayElements()) {
            throw new IllegalArgumentException("cfg.heights must be JS array/typed array or host float[]");
        }

        float[] out = new float[expected];
        for (int i = 0; i < expected; i++) {
            Value el = v.getArrayElement(i);
            out[i] = (el != null && !el.isNull() && el.isNumber()) ? (float) el.asDouble() : 0f;
        }
        return out;
    }

    public Geometry createQuad(Value cfg) {
        String name = str(cfg, "name", TerrainDefaults.NAME_QUAD);
        float w = (float) clamp(num(cfg, "w", TerrainDefaults.PLANE_W), 0.0001, 1_000_000);
        float h = (float) clamp(num(cfg, "h", TerrainDefaults.PLANE_H), 0.0001, 1_000_000);

        Geometry g = new Geometry(name, new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);

        mat.applyGeometryDefault(g, cfg);
        return g;
    }

    public Geometry createPlane(Value cfg) {
        String name = str(cfg, "name", TerrainDefaults.NAME_PLANE);
        float w = (float) clamp(num(cfg, "w", TerrainDefaults.PLANE_W), 0.0001, 1_000_000);
        float h = (float) clamp(num(cfg, "h", TerrainDefaults.PLANE_H), 0.0001, 1_000_000);

        Geometry g = new Geometry(name, new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);
        g.setLocalRotation(new Quaternion().fromAngles(-(float) (Math.PI * 0.5), 0f, 0f));

        mat.applyGeometryDefault(g, cfg);
        return g;
    }
}