/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.scene.Spatial
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  com.jme3.texture.Texture
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.impl.MaterialApiImpl$MaterialHandle
 *  org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi$SurfaceHandle
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Texture;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.types.MaterialHandle;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.TerrainSplatApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainSplatApiImpl
extends AbstractApiModule
implements TerrainSplatApi {
    private static final Logger log = LogManager.getLogger(TerrainSplatApiImpl.class);
    private EngineApiImpl engine;
    private AssetManager assets;
    private SurfaceRegistry registry;

    public TerrainSplatApiImpl() {
        super("terrainSplat", "TerrainSplat", "2.0.0");
    }

    private Material unwrapMaterialHandle(LuaValueRef maybeHandle) {
        if (maybeHandle == null || maybeHandle.isNull()) {
            return null;
        }
        try {
            Object host;
            if (maybeHandle.isHostObject() && (host = maybeHandle.asHostObject()) instanceof MaterialHandle) {
                MaterialHandle mh = (MaterialHandle) host;
                Material direct = mh.__material();
                return direct != null ? direct : this.engine.material().material(mh);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.engine = Objects.requireNonNull(ctx.engine, "ctx.engine");
        this.assets = Objects.requireNonNull(ctx.assets, "ctx.assets");
        this.registry = ctx.engine.getSurfaceRegistry();
    }

    public void detach() {
        this.registry = null;
        this.assets = null;
        this.engine = null;
        super.detach();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void apply(SurfaceApi.SurfaceHandle terrainHandle, LuaValueRef cfg) {
        if (terrainHandle == null) {
            throw new IllegalArgumentException("terrainSplat.apply: handle is null");
        }
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("terrainSplat.apply: cfg is null");
        }
        int sid = terrainHandle.id();
        Spatial s = this.registry.get(sid);
        if (!(s instanceof TerrainQuad)) {
            throw new IllegalStateException("terrainSplat.apply: handle is not TerrainQuad id=" + sid);
        }
        TerrainQuad tq = (TerrainQuad)s;
        Material mat = this.resolveMaterial(cfg);
        this.applyAlpha(mat, cfg);
        this.applyLayers(mat, cfg);
        tq.setMaterial(mat);
        if (log.isInfoEnabled()) {
            log.info("[terrainSplat] applied terrain id={}", (Object)sid);
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object createMaterial(LuaValueRef cfg) {
        Material mat = new Material(this.assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        if (cfg != null && !cfg.isNull()) {
            this.applyAlpha(mat, cfg);
            this.applyLayers(mat, cfg);
        }
        return new MaterialHandle(0, mat);
    }

    private Material resolveMaterial(LuaValueRef cfg) {
        LuaValueRef mv = LuaCfg.member((LuaValueRef)cfg, (String)"material");
        Material mat = this.unwrapMaterialHandle(mv);
        if (mat != null) {
            return mat;
        }
        return new Material(this.assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
    }

    private void applyAlpha(Material mat, LuaValueRef cfg) {
        String alpha = LuaCfg.str((LuaValueRef)cfg, (String)"alpha", null);
        if (alpha == null || alpha.isBlank()) {
            return;
        }
        Texture alphaTex = this.assets.loadTexture(alpha.trim());
        mat.setTexture("AlphaMap", alphaTex);
    }

    private void applyLayers(Material mat, LuaValueRef cfg) {
        LuaValueRef layers = LuaCfg.member((LuaValueRef)cfg, (String)"layers");
        if (layers == null || layers.isNull() || !layers.hasArrayElements()) {
            return;
        }
        long n = Math.min(12L, layers.getArraySize());
        int i = 0;
        while ((long)i < n) {
            String texPath;
            LuaValueRef layer = layers.getArrayElement((long)i);
            if (layer != null && !layer.isNull() && (texPath = LuaCfg.str((LuaValueRef)layer, (String)"tex", null)) != null && !texPath.isBlank()) {
                float sc = (float)LuaCfg.num((LuaValueRef)layer, (String)"scale", (double)32.0);
                Texture t = this.assets.loadTexture(texPath.trim());
                mat.setTexture("DiffuseMap_" + i, t);
                mat.setFloat("DiffuseMap_" + i + "_scale", sc);
            }
            ++i;
        }
    }
}

