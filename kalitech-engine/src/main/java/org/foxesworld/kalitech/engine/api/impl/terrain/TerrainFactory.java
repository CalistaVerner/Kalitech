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
        if (heightsV == null || heightsV.isNull() || !heightsV.hasArrayElements()) {
            throw new IllegalArgumentException("terrain.terrainHeights: cfg.heights array is required");
        }

        int size = clampInt(num(cfg, "size", 0), 33, 8193);
        if (size <= 0) throw new IllegalArgumentException("terrain.terrainHeights: cfg.size is required");

        long expected = (long) size * (long) size;
        long n = heightsV.getArraySize();
        if (n != expected) {
            throw new IllegalArgumentException("terrain.terrainHeights: heights length must be size*size (" + expected + "), got " + n);
        }

        int patchSize = clampInt(num(cfg, "patchSize", TerrainDefaults.PATCH_SIZE), 17, 257);
        float xzScale = (float) num(cfg, "xzScale", TerrainDefaults.XZ_SCALE);
        float yScale  = (float) num(cfg, "yScale", TerrainDefaults.Y_SCALE);

        float[] heights = new float[(int) expected];
        for (int i = 0; i < expected; i++) {
            Value el = heightsV.getArrayElement(i);
            heights[i] = (el != null && !el.isNull() && el.isNumber()) ? (float) el.asDouble() : 0f;
        }

        TerrainQuad tq = new TerrainQuad(str(cfg, "name", TerrainDefaults.NAME_TERRAIN), patchSize, size, heights);
        tq.setLocalScale(xzScale, yScale, xzScale);

        boolean shadows = bool(cfg, "shadows", TerrainDefaults.SHADOWS_BOOL_DEFAULT);
        tq.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);

        mat.applyTerrainDefault(tq, cfg);
        return tq;
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