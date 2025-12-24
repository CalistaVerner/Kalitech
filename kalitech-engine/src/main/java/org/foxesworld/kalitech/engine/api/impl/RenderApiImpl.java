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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.numClamp;

public final class RenderApiImpl implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    // log missing params only once per materialdef+param
    private final Set<String> missingMatParamsLogged =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

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

        // default flat green until splat applied
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
        int sp = intClampR(cfg, "splits", 3, 1, 4);
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
    // Terrain splat (3 layers) - FIXED
    // ------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void terrainSplat3(
            String alphaMapAsset,
            String tex1, double scale1,
            String tex2, double scale2,
            String tex3, double scale3
    ) {
        ensureScene();

        if (terrain == null) {
            throw new IllegalStateException("terrainSplat3: terrain not created");
        }

        // TerrainLighting supports splat + lighting
        Material mat = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");

        Texture alpha = assets.loadTexture(alphaMapAsset);
        alpha.setWrap(Texture.WrapMode.Clamp);
        mat.setTexture("AlphaMap", alpha);

        // load textures
        Texture t1 = assets.loadTexture(tex1);
        Texture t2 = assets.loadTexture(tex2);
        Texture t3 = assets.loadTexture(tex3);

        // wrap repeat for tiling
        t1.setWrap(Texture.WrapMode.Repeat);
        t2.setWrap(Texture.WrapMode.Repeat);
        t3.setWrap(Texture.WrapMode.Repeat);

        // Apply layers safely; if some params are missing we won't crash
        setLayer(mat, 0, t1, (float) scale1);
        setLayer(mat, 1, t2, (float) scale2);
        setLayer(mat, 2, t3, (float) scale3);

        terrain.setMaterial(mat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        log.info("RenderApi: terrain splat applied (alpha={}, {}, {}, {})",
                alphaMapAsset, tex1, tex2, tex3);
    }

    /**
     * Robust layer setter:
     * Supports TerrainLighting.j3md naming:
     * - DiffuseMap, DiffuseMap_1, DiffuseMap_2
     * - DiffuseMap_0_scale, DiffuseMap_1_scale, DiffuseMap_2_scale
     *
     * Also tries classic Terrain.j3md scale names:
     * - Tex1Scale, Tex2Scale, Tex3Scale
     */
    private void setLayer(Material mat, int layerIndex, Texture tex, float scale) {
        // 1) texture param names (TerrainLighting.j3md)
        String texParamA = (layerIndex == 0) ? "DiffuseMap" : ("DiffuseMap_" + layerIndex);
        // 2) alternative texture param names (some matdefs use Tex1/Tex2/Tex3)
        String texParamB = "Tex" + (layerIndex + 1);

        boolean texOk = trySetTexture(mat, texParamA, tex) || trySetTexture(mat, texParamB, tex);
        if (!texOk) {
            warnMissingOnce(mat, "layerTexture#" + layerIndex + " (" + texParamA + " / " + texParamB + ")");
        }

        // scale param names
        // TerrainLighting: DiffuseMap_0_scale ... (NOTE: index starts at 0)
        String scaleA = "DiffuseMap_" + layerIndex + "_scale";
        // Terrain classic: Tex1Scale.. (NOTE: index starts at 1)
        String scaleB = "Tex" + (layerIndex + 1) + "Scale";
        // Some variants
        String scaleC = "Tex" + (layerIndex + 1) + "_Scale";
        String scaleD = "DiffuseMap_" + (layerIndex + 1) + "_scale";
        String scaleE = "DiffuseMap_" + (layerIndex + 1) + "Scale";

        boolean scaleOk =
                trySetFloat(mat, scaleA, scale) ||
                        trySetFloat(mat, scaleB, scale) ||
                        trySetFloat(mat, scaleC, scale) ||
                        trySetFloat(mat, scaleD, scale) ||
                        trySetFloat(mat, scaleE, scale);

        if (!scaleOk) {
            // Not fatal: material just doesn't support per-layer scale
            warnMissingOnce(mat, "layerScale#" + layerIndex + " (" + scaleA + " / " + scaleB + ")");
        }
    }

    // ------------------------------------------------------------------
    // Safe material param setters
    // ------------------------------------------------------------------

    private boolean trySetFloat(Material mat, String name, float value) {
        try {
            if (mat.getParam(name) == null) return false;
            mat.setFloat(name, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean trySetTexture(Material mat, String name, Texture tex) {
        try {
            if (mat.getParam(name) == null) return false;
            mat.setTexture(name, tex);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void warnMissingOnce(Material mat, String param) {
        String key = mat.getMaterialDef().getName() + "::" + param;
        if (missingMatParamsLogged.add(key)) {
            log.warn("Material '{}' does not define param {}. Feature will fallback/skip.",
                    mat.getMaterialDef().getName(), param);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return m != null && !m.isNull() ? m.asDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double numPath(Value v, String k1, String k2, double def) {
        try {
            Value a = member(v, k1);
            if (a == null || a.isNull()) return def;
            if (!a.hasMember(k2)) return def;
            Value b = a.getMember(k2);
            return b != null && !b.isNull() ? b.asDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static float vec3x(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(0).asDouble();
            return (float) v.getMember("x").asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static float vec3y(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(1).asDouble();
            return (float) v.getMember("y").asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static float vec3z(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(2).asDouble();
            return (float) v.getMember("z").asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    @HostAccess.Export
    @Override
    public void terrainCfg(Value cfg) {
        ensureScene();
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("render.terrain(cfg): cfg is null");
        }

        // heightmap
        String heightmap = str(cfg, "heightmap", null);
        if (heightmap == null || heightmap.isBlank()) {
            throw new IllegalArgumentException("render.terrain({heightmap}) heightmap is required");
        }

        // size/scale
        int patchSize = intClampR(cfg, "patchSize", 65, 17, 257);   // typical: 33/65/129
        int size      = intClampR(cfg, "size", 513, 33, 8193);      // typical: 513/1025
        double heightScale = num(cfg, "heightScale", 2.0);
        double xzScale     = num(cfg, "xzScale", 2.0);

        // Build base terrain
        terrainFromHeightmap(heightmap, patchSize, size, heightScale, xzScale);

        // optional: splat
        String alpha = str(cfg, "alpha", null);
        Value layers = member(cfg, "layers");

        if (alpha != null && !alpha.isBlank() && layers != null && layers.hasArrayElements()) {
            // We support exactly 3 layers for now (terrainSplat3).
            // If fewer provided — we fill with last; if more — we take first 3.
            Layer L0 = readLayer(layers, 0);
            Layer L1 = readLayer(layers, 1);
            Layer L2 = readLayer(layers, 2);

            if (L0.tex == null) throw new IllegalArgumentException("render.terrain: layers[0].tex is required");
            if (L1.tex == null) L1 = L0;
            if (L2.tex == null) L2 = L1;

            terrainSplat3(alpha, L0.tex, L0.scale, L1.tex, L1.scale, L2.tex, L2.scale);
        }

        // optional: shadows toggle
        boolean shadows = bool(cfg, "shadows", true);
        if (terrain != null) {
            terrain.setShadowMode(shadows ? RenderQueue.ShadowMode.CastAndReceive : RenderQueue.ShadowMode.Receive);
        }

        log.info("RenderApi: terrain(cfg) applied heightmap={} alpha={} layers={}",
                heightmap, alpha, (layers != null && layers.hasArrayElements()) ? layers.getArraySize() : 0);
    }

    private static final class Layer {
        final String tex;
        final double scale;
        Layer(String tex, double scale) { this.tex = tex; this.scale = scale; }
    }

    private Layer readLayer(Value layers, int idx) {
        try {
            if (layers == null || !layers.hasArrayElements()) return new Layer(null, 32.0);
            long n = layers.getArraySize();
            if (n <= 0) return new Layer(null, 32.0);

            int safe = (int) Math.min(Math.max(idx, 0), n - 1);
            Value l = layers.getArrayElement(safe);
            if (l == null || l.isNull()) return new Layer(null, 32.0);

            String tex = null;
            double scale = 32.0;

            // allow shorthand: layers: ["Textures/grass.jpg", ...]
            if (l.isString()) {
                tex = l.asString();
                return new Layer(tex, scale);
            }

            // object: { tex, scale }
            tex = str(l, "tex", null);
            scale = num(l, "scale", 32.0);

            // clamp scale (avoid insane values)
            if (scale < 0.001) scale = 0.001;
            if (scale > 4096) scale = 4096;

            return new Layer(tex, scale);
        } catch (Exception e) {
            return new Layer(null, 32.0);
        }
    }

// ---- small helpers (add if you don't have them) ----

    private static String str(Value v, String k, String def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            return m.asString();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            return m.asBoolean();
        } catch (Exception e) {
            return def;
        }
    }


    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}