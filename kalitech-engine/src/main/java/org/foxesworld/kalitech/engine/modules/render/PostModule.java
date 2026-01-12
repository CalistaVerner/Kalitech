// FILE: org/foxesworld/kalitech/engine/modules/render/PostModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.render.post.TonemapFilter;
import org.graalvm.polyglot.Value;

public final class PostModule {

    private static final double FOG_DENSITY_MIN = 0.0;
    private static final double FOG_DENSITY_MAX = 0.03;
    private static final double FOG_DISTANCE_MIN = 25.0;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;

    private FilterPostProcessor fpp;
    private FogFilter fog;
    @SuppressWarnings("unused")
    private FXAAFilter fxaa;
    @SuppressWarnings("unused")
    private BloomFilter bloom;
    private TonemapFilter tonemap;

    // fog state
    private double fogBaseR = 0.70, fogBaseG = 0.78, fogBaseB = 0.90;
    private double fogDensity = 0.006;
    private double fogDistance = 250.0;

    // post cache
    private boolean postEnabled = true;
    private float postExposure = Float.NaN;
    private float postWhitePoint = Float.NaN;
    private float postShoulder = Float.NaN;
    private float postToe = Float.NaN;
    private float postSaturation = Float.NaN;

    public PostModule(SimpleApplication app, AssetManager assets, Logger log) {
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        this.app = app;
        this.assets = assets;
        this.log = log;
    }

    public void ensureMainFpp(String where) {
        if (fpp != null) return;
        fpp = new FilterPostProcessor(assets);
        app.getViewPort().addProcessor(fpp);
        log.info("RenderApi: {} main FPP created", where);
    }

    public void fogCfg(Value cfg) {
        ensureFogExists();

        double r = RenderCfg.num(cfg, "r", RenderCfg.numPath(cfg, "color", "r", fogBaseR));
        double g = RenderCfg.num(cfg, "g", RenderCfg.numPath(cfg, "color", "g", fogBaseG));
        double b = RenderCfg.num(cfg, "b", RenderCfg.numPath(cfg, "color", "b", fogBaseB));

        double density = RenderCfg.num(cfg, "density", fogDensity);
        double distance = RenderCfg.num(cfg, "distance", fogDistance);

        fogBaseR = r;
        fogBaseG = g;
        fogBaseB = b;

        density = Math.max(FOG_DENSITY_MIN, Math.min(density, FOG_DENSITY_MAX));
        distance = Math.max(FOG_DISTANCE_MIN, distance);

        fogDensity = density;
        fogDistance = distance;

        fog.setFogColor(new ColorRGBA((float) r, (float) g, (float) b, 1f));
        fog.setFogDensity((float) density);
        fog.setFogDistance((float) distance);
    }

    public void postCfg(Value cfg) {
        ensureMainFpp("postCfg");

        boolean enabled = RenderCfg.bool(cfg, "enabled", true);
        if (enabled != postEnabled) postEnabled = enabled;

        if (!postEnabled) {
            if (tonemap != null) {
                fpp.removeFilter(tonemap);
                tonemap = null;
                log.info("RenderApi: Tonemap removed (post disabled)");
            }
            return;
        }

        ensureTonemapExists();

        float exposure = (float) Math.max(0.0, RenderCfg.num(cfg, "exposure", 1.0));

        Value tm = RenderCfg.member(cfg, "tonemap");
        float whitePoint = (float) Math.max(0.01, RenderCfg.num(tm, "whitePoint", 11.2));
        float shoulder = RenderCfg.clamp01((float) RenderCfg.num(tm, "shoulder", 0.22));
        float toe = RenderCfg.clamp01((float) RenderCfg.num(tm, "toe", 0.08));

        float saturation = (float) Math.max(0.0, RenderCfg.num(cfg, "saturation", 1.0));

        if (RenderCfg.approx(exposure, postExposure) &&
                RenderCfg.approx(whitePoint, postWhitePoint) &&
                RenderCfg.approx(shoulder, postShoulder) &&
                RenderCfg.approx(toe, postToe) &&
                RenderCfg.approx(saturation, postSaturation)) {
            return;
        }

        postExposure = exposure;
        postWhitePoint = whitePoint;
        postShoulder = shoulder;
        postToe = toe;
        postSaturation = saturation;

        tonemap.setExposure(exposure);
        tonemap.setWhitePoint(whitePoint);
        tonemap.setShoulder(shoulder);
        tonemap.setToe(toe);
        tonemap.setSaturation(saturation);
    }

    private void ensureFogExists() {
        if (fog != null) return;
        ensureMainFpp("ensureFogExists");
        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA((float) fogBaseR, (float) fogBaseG, (float) fogBaseB, 1f));
        fog.setFogDensity((float) fogDensity);
        fog.setFogDistance((float) fogDistance);
        fpp.addFilter(fog);
        log.info("RenderApi: fog filter created");
    }

    private void ensureTonemapExists() {
        if (tonemap != null) return;
        ensureMainFpp("ensureTonemapExists");
        tonemap = new TonemapFilter(this.assets);
        fpp.addFilter(tonemap);
        log.info("RenderApi: Tonemap created");
    }
}