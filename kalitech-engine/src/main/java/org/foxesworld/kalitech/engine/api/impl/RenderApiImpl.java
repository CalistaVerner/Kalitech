package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.numClamp;

public final class RenderApiImpl implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    private final SimpleApplication app;
    private final AssetManager assets;

    private boolean sceneReady = false;

    private TerrainQuad terrain;
    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLightShadowRenderer sunShadow;

    private Spatial sky;
    private FilterPostProcessor fpp;
    private FogFilter fog;

    public RenderApiImpl(SimpleApplication app, AssetManager assets) {
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    // ------------------------------------------------------------------
    // Scene bootstrap
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ensureScene() {
        if (sceneReady) return;
        sceneReady = true;
        log.info("RenderApi: scene ready");
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void terrainFromHeightmap(
            String heightmapAsset,
            double patchSizeD,
            double sizeD,
            double heightScaleD,
            double xzScaleD
    ) {
        ensureScene();

        int patchSize = Math.max(17, (int) Math.round(patchSizeD));

        if (terrain != null) {
            terrain.removeFromParent();
            terrain = null;
        }

        Texture tex = assets.loadTexture(heightmapAsset);
        AbstractHeightMap hm = new ImageBasedHeightMap(tex.getImage(), (float) heightScaleD);
        hm.load();

        int size = Math.max(33, (int) Math.round(sizeD));

        terrain = new TerrainQuad("terrain", patchSize, size, hm.getHeightMap());
        terrain.setLocalScale((float) xzScaleD, 1f, (float) xzScaleD);
        terrain.setShadowMode(RenderQueue.ShadowMode.Receive);

        Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.25f, 0.7f, 0.3f, 1f));
        terrain.setMaterial(mat);

        app.getRootNode().attachChild(terrain);

        log.info("RenderApi: terrain created ({}, size={}, scale={})",
                heightmapAsset, size, xzScaleD);
    }

    // ------------------------------------------------------------------
    // Lighting (old style)
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ambient(double r, double g, double b, double intensity) {
        ensureScene();
        if (ambient == null) {
            ambient = new AmbientLight();
            app.getRootNode().addLight(ambient);
        }
        ambient.setColor(new ColorRGBA(
                (float) r, (float) g, (float) b, 1f
        ).mult((float) Math.max(0.0, intensity)));
    }

    @HostAccess.Export
    @Override
    public void sun(double dx, double dy, double dz,
                    double r, double g, double b,
                    double intensity) {
        ensureScene();
        if (sun == null) {
            sun = new DirectionalLight();
            app.getRootNode().addLight(sun);
        }

        Vector3f dir = new Vector3f((float) dx, (float) dy, (float) dz);
        if (dir.lengthSquared() < 1e-6f) dir.set(-1, -1, -1);
        dir.normalizeLocal();

        sun.setDirection(dir);
        sun.setColor(new ColorRGBA(
                (float) r, (float) g, (float) b, 1f
        ).mult((float) Math.max(0.0, intensity)));

        if (terrain != null) {
            terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        }
    }

    @HostAccess.Export
    @Override
    public void sunShadows(double mapSizeD, double splitsD, double lambdaD) {
        ensureScene();

        if (sun == null) {
            sun(-1, -1, -1, 1, 1, 1, 1);
        }

        int ms = clamp((int) Math.round(mapSizeD), 256, 8192);
        int sp = clamp((int) Math.round(splitsD), 1, 4);
        float lambda = (float) clamp(lambdaD, 0.0, 1.0);

        if (sunShadow != null) {
            app.getViewPort().removeProcessor(sunShadow);
        }

        sunShadow = new DirectionalLightShadowRenderer(assets, ms, sp);
        sunShadow.setLight(sun);
        sunShadow.setLambda(lambda);

        app.getViewPort().addProcessor(sunShadow);

        log.info("RenderApi: sun shadows enabled ({}px, splits={}, λ={})", ms, sp, lambda);
    }

    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        int map = intClampR(cfg, "mapSize", 2048, 256, 8192);
        int sp  = intClampR(cfg, "splits", 3, 1, 4);
        float l = (float) numClamp(cfg, "lambda", 0.65, 0.0, 1.0);
        sunShadows(map, sp, l);
    }

    // ------------------------------------------------------------------
    // Lighting (cfg style)
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ambientCfg(Value cfg) {
        ambient(
                num(cfg, "r", numPath(cfg, "color", "r", 0.25)),
                num(cfg, "g", numPath(cfg, "color", "g", 0.28)),
                num(cfg, "b", numPath(cfg, "color", "b", 0.35)),
                num(cfg, "intensity", 1.0)
        );
    }

    @HostAccess.Export
    @Override
    public void sunCfg(Value cfg) {
        Value dir = member(cfg, "dir");
        Value col = member(cfg, "color");

        sun(
                vec3x(dir, -1), vec3y(dir, -1), vec3z(dir, -0.3f),
                vec3x(col, 1), vec3y(col, 0.98f), vec3z(col, 0.9f),
                num(cfg, "intensity", 1.2)
        );
    }

    // ------------------------------------------------------------------
    // Skybox
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void skyboxCube(String cubeMapAsset) {
        ensureScene();

        if (sky != null) {
            sky.removeFromParent();
            sky = null;
        }

        sky = SkyFactory.createSky(assets, cubeMapAsset, SkyFactory.EnvMapType.CubeMap);
        app.getRootNode().attachChild(sky);

        log.info("RenderApi: skybox set {}", cubeMapAsset);
    }

    // ------------------------------------------------------------------
    // Fog
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        ensureScene();

        double r = num(cfg, "r", numPath(cfg, "color", "r", 0.7));
        double g = num(cfg, "g", numPath(cfg, "color", "g", 0.75));
        double b = num(cfg, "b", numPath(cfg, "color", "b", 0.85));

        double density = num(cfg, "density", 1.2);
        double distance = num(cfg, "distance", 300.0);

        if (fpp == null) {
            fpp = new FilterPostProcessor(assets);
            app.getViewPort().addProcessor(fpp);
        }

        if (fog != null) {
            fpp.removeFilter(fog);
        }

        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA((float) r, (float) g, (float) b, 1f));
        fog.setFogDensity((float) density);
        fog.setFogDistance((float) distance);

        fpp.addFilter(fog);

        log.info("RenderApi: fog enabled (density={}, distance={})", density, distance);
    }

    // ------------------------------------------------------------------
    // Terrain splat (3 layers)
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void terrainSplat3(
            String alphaMapAsset,
            String tex1, double scale1,
            String tex2, double scale2,
            String tex3, double scale3
    ) {
        if (terrain == null) {
            throw new IllegalStateException("terrainSplat3: terrain not created");
        }

        Material mat = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");

        Texture alpha = assets.loadTexture(alphaMapAsset);
        mat.setTexture("AlphaMap", alpha);

        setLayer(mat, "DiffuseMap", tex1, scale1);
        setLayer(mat, "DiffuseMap_1", tex2, scale2);
        setLayer(mat, "DiffuseMap_2", tex3, scale3);

        terrain.setMaterial(mat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        log.info("RenderApi: terrain splat applied");
    }

    private void setLayer(Material mat, String key, String tex, double scale) {
        Texture t = assets.loadTexture(tex);
        t.setWrap(Texture.WrapMode.Repeat);
        mat.setTexture(key, t);
        mat.setFloat(key + "_scale", (float) scale);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static double num(Value v, String k, double def) {
        try { return member(v, k) != null ? member(v, k).asDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static double numPath(Value v, String k1, String k2, double def) {
        try {
            Value a = member(v, k1);
            return a != null && a.hasMember(k2) ? a.getMember(k2).asDouble() : def;
        } catch (Exception e) { return def; }
    }

    private static float vec3x(Value v, float def) {
        try {
            if (v == null) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(0).asDouble();
            return (float) v.getMember("x").asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3y(Value v, float def) {
        try {
            if (v == null) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(1).asDouble();
            return (float) v.getMember("y").asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3z(Value v, float def) {
        try {
            if (v == null) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(2).asDouble();
            return (float) v.getMember("z").asDouble();
        } catch (Exception e) { return def; }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}