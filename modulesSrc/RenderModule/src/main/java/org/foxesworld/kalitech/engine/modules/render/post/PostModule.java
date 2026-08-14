/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetManager
 *  com.jme3.math.ColorRGBA
 *  com.jme3.post.Filter
 *  com.jme3.post.FilterPostProcessor
 *  com.jme3.post.SceneProcessor
 *  com.jme3.post.filters.BloomFilter
 *  com.jme3.post.filters.FXAAFilter
 *  com.jme3.post.filters.FogFilter
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.render.post.TonemapFilter
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.post;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.render.post.TonemapFilter;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class PostModule {
    private static final double FOG_DENSITY_MIN = 0.0;
    private static final double FOG_DENSITY_MAX = 0.03;
    private static final double FOG_DISTANCE_MIN = 25.0;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private FilterPostProcessor fpp;
    private FogFilter fog;
    private FXAAFilter fxaa;
    private BloomFilter bloom;
    private TonemapFilter tonemap;
    private double fogBaseR = 0.7;
    private double fogBaseG = 0.78;
    private double fogBaseB = 0.9;
    private double fogDensity = 0.006;
    private double fogDistance = 250.0;
    private boolean postEnabled = true;
    private float postExposure = Float.NaN;
    private float postWhitePoint = Float.NaN;
    private float postShoulder = Float.NaN;
    private float postToe = Float.NaN;
    private float postSaturation = Float.NaN;

    public PostModule(SimpleApplication app, AssetManager assets, Logger log) {
        if (app == null) {
            throw new IllegalArgumentException("app is null");
        }
        if (assets == null) {
            throw new IllegalArgumentException("assets is null");
        }
        if (log == null) {
            throw new IllegalArgumentException("log is null");
        }
        this.app = app;
        this.assets = assets;
        this.log = log;
    }

    public void ensureMainFpp(String where) {
        if (this.fpp != null) {
            return;
        }
        this.fpp = new FilterPostProcessor(this.assets);
        this.app.getViewPort().addProcessor((SceneProcessor)this.fpp);
        this.log.info("RenderApi: {} main FPP created", (Object)where);
    }

    public void fogCfg(LuaValueRef cfg) {
        this.ensureFogExists();
        double r = LuaCfg.num((LuaValueRef)cfg, (String)"r", (double)RenderCfg.numPath(cfg, "color", "r", this.fogBaseR));
        double g = LuaCfg.num((LuaValueRef)cfg, (String)"g", (double)RenderCfg.numPath(cfg, "color", "g", this.fogBaseG));
        double b = LuaCfg.num((LuaValueRef)cfg, (String)"b", (double)RenderCfg.numPath(cfg, "color", "b", this.fogBaseB));
        double density = LuaCfg.num((LuaValueRef)cfg, (String)"density", (double)this.fogDensity);
        double distance = LuaCfg.num((LuaValueRef)cfg, (String)"distance", (double)this.fogDistance);
        this.fogBaseR = r;
        this.fogBaseG = g;
        this.fogBaseB = b;
        density = Math.max(0.0, Math.min(density, 0.03));
        distance = Math.max(25.0, distance);
        this.fogDensity = density;
        this.fogDistance = distance;
        this.fog.setFogColor(new ColorRGBA((float)r, (float)g, (float)b, 1.0f));
        this.fog.setFogDensity((float)density);
        this.fog.setFogDistance((float)distance);
    }

    public void postCfg(LuaValueRef cfg) {
        this.ensureMainFpp("postCfg");
        boolean enabled = LuaCfg.bool((LuaValueRef)cfg, (String)"enabled", (boolean)true);
        if (enabled != this.postEnabled) {
            this.postEnabled = enabled;
        }
        if (!this.postEnabled) {
            if (this.tonemap != null) {
                this.fpp.removeFilter((Filter)this.tonemap);
                this.tonemap = null;
                this.log.info("RenderApi: Tonemap removed (post disabled)");
            }
            return;
        }
        this.ensureTonemapExists();
        float exposure = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"exposure", (double)1.0));
        LuaValueRef tm = LuaCfg.member((LuaValueRef)cfg, (String)"tonemap");
        float whitePoint = (float)Math.max(0.01, LuaCfg.num((LuaValueRef)tm, (String)"whitePoint", (double)11.2));
        float shoulder = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)tm, (String)"shoulder", (double)0.22));
        float toe = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)tm, (String)"toe", (double)0.08));
        float saturation = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"saturation", (double)1.0));
        if (RenderCfg.approx(exposure, this.postExposure) && RenderCfg.approx(whitePoint, this.postWhitePoint) && RenderCfg.approx(shoulder, this.postShoulder) && RenderCfg.approx(toe, this.postToe) && RenderCfg.approx(saturation, this.postSaturation)) {
            return;
        }
        this.postExposure = exposure;
        this.postWhitePoint = whitePoint;
        this.postShoulder = shoulder;
        this.postToe = toe;
        this.postSaturation = saturation;
        this.tonemap.setExposure(exposure);
        this.tonemap.setWhitePoint(whitePoint);
        this.tonemap.setShoulder(shoulder);
        this.tonemap.setToe(toe);
        this.tonemap.setSaturation(saturation);
    }

    private void ensureFogExists() {
        if (this.fog != null) {
            return;
        }
        this.ensureMainFpp("ensureFogExists");
        this.fog = new FogFilter();
        this.fog.setFogColor(new ColorRGBA((float)this.fogBaseR, (float)this.fogBaseG, (float)this.fogBaseB, 1.0f));
        this.fog.setFogDensity((float)this.fogDensity);
        this.fog.setFogDistance((float)this.fogDistance);
        this.fpp.addFilter((Filter)this.fog);
        this.log.info("RenderApi: fog filter created");
    }

    private void ensureTonemapExists() {
        if (this.tonemap != null) {
            return;
        }
        this.ensureMainFpp("ensureTonemapExists");
        this.tonemap = new TonemapFilter(this.assets);
        this.fpp.addFilter((Filter)this.tonemap);
        this.log.info("RenderApi: Tonemap created");
    }
}

