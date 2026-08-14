/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.math.Quaternion
 *  com.jme3.renderer.queue.RenderQueue$ShadowMode
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Mesh
 *  com.jme3.scene.shape.Quad
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  com.jme3.terrain.heightmap.ImageBasedHeightMap
 *  com.jme3.texture.Texture
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.math.Quaternion;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Quad;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import java.lang.reflect.Array;
import java.util.Arrays;
import org.foxesworld.kalitech.engine.modules.terrain.TerrainMaterial;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainFactory {
    private final AssetManager assets;
    private final TerrainMaterial mat;

    public TerrainFactory(AssetManager assets) {
        this.assets = assets;
        this.mat = new TerrainMaterial(assets);
    }

    private static int heightsLength(LuaValueRef v) {
        if (v.hasArrayElements()) {
            long sz = v.getArraySize();
            if (sz > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("heights too large: " + sz);
            }
            return (int)sz;
        }
        if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o == null) {
                return -1;
            }
            if (o instanceof float[]) {
                float[] a = (float[])o;
                return a.length;
            }
            if (o instanceof double[]) {
                double[] a = (double[])o;
                return a.length;
            }
            if (o.getClass().isArray()) {
                return Array.getLength(o);
            }
        }
        return -1;
    }

    private static float[] readHeightsToFloatArray(LuaValueRef v, int expected) {
        if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o instanceof float[]) {
                float[] a = (float[])o;
                return Arrays.copyOf(a, expected);
            }
            if (o instanceof double[]) {
                double[] a = (double[])o;
                float[] out = new float[expected];
                for (int i = 0; i < expected; ++i) {
                    out[i] = (float)a[i];
                }
                return out;
            }
            if (o != null && o.getClass().isArray()) {
                float[] out = new float[expected];
                for (int i = 0; i < expected; ++i) {
                    float f;
                    Object el = Array.get(o, i);
                    if (el instanceof Number) {
                        Number n = (Number)el;
                        f = n.floatValue();
                    } else {
                        f = 0.0f;
                    }
                    out[i] = f;
                }
                return out;
            }
        }
        if (!v.hasArrayElements()) {
            throw new IllegalArgumentException("cfg.heights must be a Lua array or host float[]");
        }
        float[] out = new float[expected];
        for (int i = 0; i < expected; ++i) {
            LuaValueRef el = v.getArrayElement((long)i);
            out[i] = el != null && !el.isNull() && el.isNumber() ? (float)el.asDouble() : 0.0f;
        }
        return out;
    }

    public TerrainQuad createTerrainFromHeightmap(LuaValueRef cfg) {
        String heightmap = LuaCfg.str((LuaValueRef)cfg, (String)"heightmap", null);
        if (heightmap == null || heightmap.isBlank()) {
            throw new IllegalArgumentException("terrain.terrain: heightmap is required");
        }
        int patchSize = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)cfg, (String)"patchSize", (double)65.0), (int)17, (int)257);
        int size = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)cfg, (String)"size", (double)513.0), (int)33, (int)8193);
        float heightScale = (float)LuaCfg.num((LuaValueRef)cfg, (String)"heightScale", (double)2.0);
        float xzScale = (float)LuaCfg.num((LuaValueRef)cfg, (String)"xzScale", (double)2.0);
        Texture tex = this.assets.loadTexture(heightmap);
        ImageBasedHeightMap hm = new ImageBasedHeightMap(tex.getImage(), heightScale);
        hm.load();
        TerrainQuad tq = new TerrainQuad(LuaCfg.str((LuaValueRef)cfg, (String)"name", (String)"terrain"), patchSize, size, hm.getHeightMap());
        tq.setLocalScale(xzScale, 1.0f, xzScale);
        boolean shadows = LuaCfg.bool((LuaValueRef)cfg, (String)"shadows", (boolean)true);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);
        this.mat.applyTerrainDefault(tq, cfg);
        return tq;
    }

    public TerrainQuad createTerrainFromHeights(LuaValueRef cfg) {
        boolean autoCenter;
        int s;
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("terrain.terrainHeights: cfg is null");
        }
        LuaValueRef heightsV = LuaCfg.member((LuaValueRef)cfg, (String)"heights");
        if (heightsV == null || heightsV.isNull()) {
            throw new IllegalArgumentException("terrain.terrainHeights: cfg.heights is required");
        }
        float[] heights = LuaCfg.readFloatArray((LuaValueRef)heightsV);
        if (heights.length <= 0) {
            throw new IllegalArgumentException("terrain.terrainHeights: heights is empty");
        }
        int size = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)cfg, (String)"size", (double)0.0), (int)0, (int)8193);
        LuaValueRef terr = LuaCfg.member((LuaValueRef)cfg, (String)"terrain");
        if (size <= 0 && terr != null && !terr.isNull()) {
            size = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)terr, (String)"size", (double)0.0), (int)0, (int)8193);
        }
        if (size <= 0 && (s = (int)Math.round(Math.sqrt(heights.length))) > 0 && s * s == heights.length) {
            size = s;
        }
        if (size <= 1) {
            throw new IllegalArgumentException("terrain.terrainHeights: cannot infer size; pass terrain.size or size");
        }
        int expected = size * size;
        if (heights.length != expected) {
            throw new IllegalArgumentException("terrain.terrainHeights: heights length must be size*size (" + expected + "), got " + heights.length);
        }
        int patchSize = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)cfg, (String)"patchSize", (double)65.0), (int)17, (int)257);
        if (terr != null && !terr.isNull() && !LuaCfg.has((LuaValueRef)cfg, (String)"patchSize")) {
            patchSize = LuaCfg.clampInt((double)LuaCfg.num((LuaValueRef)terr, (String)"patchSize", (double)patchSize), (int)17, (int)257);
        }
        if (autoCenter = LuaCfg.bool((LuaValueRef)cfg, (String)"autoCenter", (boolean)true)) {
            int i;
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            int step = Math.max(1, heights.length / 4096);
            for (i = 0; i < heights.length; i += step) {
                float v = heights[i];
                if (v < min) {
                    min = v;
                }
                if (!(v > max)) continue;
                max = v;
            }
            if (min >= -1.0E-4f && max <= 1.0001f && max - min > 1.0E-4f) {
                i = 0;
                while (i < heights.length) {
                    int n = i++;
                    heights[n] = heights[n] - 0.5f;
                }
            }
        }
        String name = LuaCfg.str((LuaValueRef)cfg, (String)"name", (String)"terrain");
        TerrainQuad tq = new TerrainQuad(name, patchSize, size, heights);
        tq.setLocalScale(1.0f, 1.0f, 1.0f);
        boolean shadows = LuaCfg.bool((LuaValueRef)cfg, (String)"shadows", (boolean)true);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);
        this.mat.applyTerrainDefault(tq, cfg);
        return tq;
    }

    public Geometry createQuad(LuaValueRef cfg) {
        String name = LuaCfg.str((LuaValueRef)cfg, (String)"name", (String)"quad");
        float w = (float)LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"w", (double)1.0), (double)1.0E-4, (double)1000000.0);
        float h = (float)LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"h", (double)1.0), (double)1.0E-4, (double)1000000.0);
        Geometry g = new Geometry(name, (Mesh)new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);
        this.mat.applyGeometryDefault(g, cfg);
        return g;
    }

    public Geometry createPlane(LuaValueRef cfg) {
        String name = LuaCfg.str((LuaValueRef)cfg, (String)"name", (String)"plane");
        float w = (float)LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"w", (double)1.0), (double)1.0E-4, (double)1000000.0);
        float h = (float)LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"h", (double)1.0), (double)1.0E-4, (double)1000000.0);
        Geometry g = new Geometry(name, (Mesh)new Quad(w, h));
        g.setShadowMode(RenderQueue.ShadowMode.Receive);
        g.setLocalRotation(new Quaternion().fromAngles(-1.5707964f, 0.0f, 0.0f));
        this.mat.applyGeometryDefault(g, cfg);
        return g;
    }
}

