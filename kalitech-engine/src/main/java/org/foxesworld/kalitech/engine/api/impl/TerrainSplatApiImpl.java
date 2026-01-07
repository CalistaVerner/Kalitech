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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public final class TerrainSplatApiImpl extends AbstractApiModule implements TerrainSplatApi {

    private static final Logger log = LogManager.getLogger(TerrainSplatApiImpl.class);

    private EngineApiImpl engine;
    private AssetManager assets;
    private SurfaceRegistry registry;

    public TerrainSplatApiImpl() {
        super("terrainSplat", "TerrainSplat", "1.0.0");
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.engine = ctx.engine;
        this.assets = engine.getAssets();
        this.registry = engine.getSurfaceRegistry();
    }

    @HostAccess.Export
    @Override
    public void apply(SurfaceApi.SurfaceHandle terrainHandle, Value cfg) {
        if (terrainHandle == null) throw new IllegalArgumentException("terrainSplat.apply: handle is null");
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("terrainSplat.apply: cfg is null");

        Spatial s = registry.get(terrainHandle.id());
        if (!(s instanceof TerrainQuad tq)) {
            throw new IllegalStateException("terrainSplat.apply: handle is not TerrainQuad id=" + terrainHandle.id());
        }

        Material mat = tryUnwrapMaterial(member(cfg, "material"));
        if (mat == null) {
            mat = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        }

        String alpha = str(cfg, "alpha", null);
        if (alpha != null && !alpha.isBlank()) {
            Texture alphaTex = assets.loadTexture(alpha);
            mat.setTexture("AlphaMap", alphaTex);
        }

        Value layers = member(cfg, "layers");
        if (layers != null && !layers.isNull() && layers.hasArrayElements()) {
            long n = Math.min(12, layers.getArraySize());
            for (int i = 0; i < n; i++) {
                Value layer = layers.getArrayElement(i);
                if (layer == null || layer.isNull()) continue;

                String texPath = str(layer, "tex", null);
                if (texPath == null || texPath.isBlank()) continue;

                double sc = num(layer, "scale", 32.0);

                Texture t = assets.loadTexture(texPath);
                mat.setTexture("DiffuseMap_" + i, t);
                mat.setFloat("DiffuseMap_" + i + "_scale", (float) sc);
            }
        }

        tq.setMaterial(mat);
        log.info("TerrainSplat applied to terrain id={}", terrainHandle.id());
    }

    @HostAccess.Export
    @Override
    public Object createMaterial(Value cfg) {
        Material mat = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");

        if (cfg != null && !cfg.isNull()) {
            String alpha = str(cfg, "alpha", null);
            if (alpha != null && !alpha.isBlank()) {
                mat.setTexture("AlphaMap", assets.loadTexture(alpha));
            }

            Value layers = member(cfg, "layers");
            if (layers != null && !layers.isNull() && layers.hasArrayElements()) {
                long n = Math.min(12, layers.getArraySize());
                for (int i = 0; i < n; i++) {
                    Value layer = layers.getArrayElement(i);
                    if (layer == null || layer.isNull()) continue;

                    String texPath = str(layer, "tex", null);
                    if (texPath == null || texPath.isBlank()) continue;

                    double sc = num(layer, "scale", 32.0);

                    mat.setTexture("DiffuseMap_" + i, assets.loadTexture(texPath));
                    mat.setFloat("DiffuseMap_" + i + "_scale", (float) sc);
                }
            }
        }

        return new MaterialApiImpl.MaterialHandle(0, mat);
    }

    private Material tryUnwrapMaterial(Value maybeHandle) {
        if (maybeHandle == null || maybeHandle.isNull()) return null;
        try {
            if (maybeHandle.isHostObject()) {
                Object host = maybeHandle.asHostObject();
                if (host instanceof MaterialApiImpl.MaterialHandle mh) return mh.__material();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asString();
        } catch (Throwable t) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asDouble();
        } catch (Throwable t) {
            return def;
        }
    }
}