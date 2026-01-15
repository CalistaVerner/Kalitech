package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Texture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainSplatApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class TerrainSplatApiImpl extends AbstractApiModule implements TerrainSplatApi {

    private static final Logger log = LogManager.getLogger(TerrainSplatApiImpl.class);

    private EngineApiImpl engine;
    private AssetManager assets;
    private SurfaceRegistry registry;

    public TerrainSplatApiImpl() {
        super("terrainSplat", "TerrainSplat", "2.0.0");
    }

    private static Material unwrapMaterialHandle(Value maybeHandle) {
        if (maybeHandle == null || maybeHandle.isNull()) return null;
        try {
            if (maybeHandle.isHostObject()) {
                Object host = maybeHandle.asHostObject();
                if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.engine = Objects.requireNonNull(ctx.engine, "ctx.engine");
        this.assets = Objects.requireNonNull(ctx.assets, "ctx.assets");
        this.registry = ctx.engine.getSurfaceRegistry();
    }

    @Override
    public void detach() {
        this.registry = null;
        this.assets = null;
        this.engine = null;
        super.detach();
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void apply(SurfaceApi.SurfaceHandle terrainHandle, Value cfg) {
        if (terrainHandle == null) throw new IllegalArgumentException("terrainSplat.apply: handle is null");
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrainSplat.apply: cfg is null");

        final int sid = terrainHandle.id();

        final Spatial s = registry.get(sid);
        if (!(s instanceof TerrainQuad tq)) {
            throw new IllegalStateException("terrainSplat.apply: handle is not TerrainQuad id=" + sid);
        }

        Material mat = resolveMaterial(cfg);
        applyAlpha(mat, cfg);
        applyLayers(mat, cfg);

        tq.setMaterial(mat);

        if (log.isInfoEnabled()) log.info("[terrainSplat] applied terrain id={}", sid);
    }

    @HostAccess.Export
    @Override
    public Object createMaterial(Value cfg) {
        Material mat = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");

        if (cfg != null && !cfg.isNull()) {
            applyAlpha(mat, cfg);
            applyLayers(mat, cfg);
        }

        return new MaterialApiImpl.MaterialHandle(0, mat);
    }

    private Material resolveMaterial(Value cfg) {
        Value mv = member(cfg, "material");
        Material mat = unwrapMaterialHandle(mv);
        if (mat != null) return mat;
        return new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
    }

    private void applyAlpha(Material mat, Value cfg) {
        String alpha = str(cfg, "alpha", null);
        if (alpha == null || alpha.isBlank()) return;
        Texture alphaTex = assets.loadTexture(alpha.trim());
        mat.setTexture("AlphaMap", alphaTex);
    }

    private void applyLayers(Material mat, Value cfg) {
        Value layers = member(cfg, "layers");
        if (layers == null || layers.isNull() || !layers.hasArrayElements()) return;

        long n = Math.min(12, layers.getArraySize());
        for (int i = 0; i < n; i++) {
            Value layer = layers.getArrayElement(i);
            if (layer == null || layer.isNull()) continue;

            String texPath = str(layer, "tex", null);
            if (texPath == null || texPath.isBlank()) continue;

            float sc = (float) num(layer, "scale", 32.0);

            Texture t = assets.loadTexture(texPath.trim());
            mat.setTexture("DiffuseMap_" + i, t);
            mat.setFloat("DiffuseMap_" + i + "_scale", sc);
        }
    }
}