// FILE: org/foxesworld/kalitech/engine/api/impl/RenderApiImpl.java
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
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.numClamp;

public final class RenderApiImpl implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    // Fog state cache (prevents spam + avoids redundant updates)
    private double _fogBaseR = 0.70;
    private double _fogBaseG = 0.78;
    private double _fogBaseB = 0.90;
    private double _fogDensity = 1.2;
    private double _fogDistance = 250.0;

    private final Set<String> missingMatParamsLogged =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final EngineApiImpl engineApi;
    private final SimpleApplication app;
    private final AssetManager assets;
    @SuppressWarnings("unused")
    private final EcsWorld ecs;

    private volatile boolean sceneReady = false;

    private TerrainQuad terrain;
    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLightShadowRenderer sunShadow;

    private Spatial sky;
    private FilterPostProcessor fpp;
    private FogFilter fog;

    // ---------------------------
    // Spatial handles (JS-facing)
    // ---------------------------

    public static final class SpatialHandle {
        private final int id;
        private SpatialHandle(int id) { this.id = id; }

        @HostAccess.Export
        public int id() { return id; }

        @Override
        public String toString() { return "SpatialHandle(" + id + ")"; }
    }

    private final AtomicInteger spatialIds = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> spatials = new ConcurrentHashMap<>();

    // ✅ stable root handle (no leaking new handles on every root() call)
    private final SpatialHandle rootHandle;

    public RenderApiImpl(EngineApiImpl engineApi) {
        this.engineApi = engineApi;
        this.app = engineApi.getApp();
        this.assets = engineApi.getAssets();
        this.ecs = engineApi.getEcs();

        // register root once
        this.rootHandle = registerSpatial(app.getRootNode());
    }

    private void onJme(Runnable r) {
        if (engineApi.isJmeThread()) r.run();
        else app.enqueue(() -> { r.run(); return null; });
    }

    private SpatialHandle registerSpatial(Spatial s) {
        int id = spatialIds.getAndIncrement();
        spatials.put(id, s);
        return new SpatialHandle(id);
    }

    private Spatial requireSpatial(Object handle, String where) {
        if (handle == null) throw new IllegalArgumentException(where + ": handle is null");

        final int id;
        if (handle instanceof SpatialHandle sh) id = sh.id;
        else if (handle instanceof Number n) id = n.intValue();
        else throw new IllegalArgumentException(where + ": invalid handle type: " + handle.getClass().getName());

        Spatial s = spatials.get(id);
        if (s == null) throw new IllegalArgumentException(where + ": spatial not found id=" + id);
        return s;
    }

    private int handleId(Object handle, String where) {
        if (handle instanceof SpatialHandle sh) return sh.id;
        if (handle instanceof Number n) return n.intValue();
        throw new IllegalArgumentException(where + ": invalid handle type: " + handle.getClass().getName());
    }

    // ------------------------------------------------------------
    // AAA viewport hygiene (self-healing contract)
    // ------------------------------------------------------------

    /** Enforces viewport contract (AAA hygiene). Call on JME thread only. */
    private void ensureViewportContract(String where) {
        try {
            // MAIN viewport must render rootNode
            ViewPort main = app.getViewPort();
            if (main != null) {
                boolean hasRoot = false;
                for (Spatial s : main.getScenes()) {
                    if (s == app.getRootNode()) { hasRoot = true; break; }
                }
                if (!hasRoot) {
                    try { main.clearScenes(); } catch (Throwable ignored) {}
                    main.attachScene(app.getRootNode());
                    log.warn("RenderApi: {} fixed MAIN viewport scenes -> rootNode attached", where);
                }
            }

            // GUI viewport must render guiNode and never clear
            ViewPort gui = app.getGuiViewPort();
            if (gui != null) {
                if (!gui.isEnabled()) {
                    gui.setEnabled(true);
                    log.warn("RenderApi: {} GUI viewport was disabled -> enabled", where);
                }

                boolean hasGui = false;
                for (Spatial s : gui.getScenes()) {
                    if (s == app.getGuiNode()) { hasGui = true; break; }
                }
                if (!hasGui) {
                    try { gui.clearScenes(); } catch (Throwable ignored) {}
                    gui.attachScene(app.getGuiNode());
                    log.warn("RenderApi: {} fixed GUI viewport scenes -> guiNode attached", where);
                }

                // GUI draws on top; must not clear buffers
                gui.setClearFlags(false, false, false);

                // Ensure guiNode is in GUI render path
                Node guiNode = app.getGuiNode();
                guiNode.setQueueBucket(RenderQueue.Bucket.Gui);
                guiNode.setCullHint(Spatial.CullHint.Never);
            }
        } catch (Throwable t) {
            log.warn("RenderApi: ensureViewportContract({}) failed: {}", where, t.toString());
        }
    }

    /** Ensures main FPP exists and is attached to MAIN viewport only. Call on JME thread only. */
    private void ensureMainFpp(String where) {
        if (fpp != null) return;
        fpp = new FilterPostProcessor(assets);
        app.getViewPort().addProcessor(fpp);
        log.info("RenderApi: {} main FPP created", where);
    }

    @HostAccess.Export
    public void debugViewports() {
        onJme(() -> {
            try {
                ViewPort main = app.getViewPort();
                ViewPort gui  = app.getGuiViewPort();

                log.info("VP MAIN enabled={} scenes={} procs={}",
                        main != null && main.isEnabled(),
                        main == null ? -1 : main.getScenes().size(),
                        main == null ? -1 : main.getProcessors().size());

                log.info("VP GUI  enabled={} scenes={} procs={} clearFlags=(false,false,false expected)",
                        gui != null && gui.isEnabled(),
                        gui == null ? -1 : gui.getScenes().size(),
                        gui == null ? -1 : gui.getProcessors().size());
            } catch (Throwable t) {
                log.warn("debugViewports failed: {}", t.toString());
            }
        });
    }

    // ------------------------------------------------------------
    // Core lifecycle
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ensureScene() {
        if (sceneReady) return;
        sceneReady = true;

        onJme(() -> {
            ensureViewportContract("ensureScene");
            log.info("RenderApi: scene ready");
        });
    }

    private void ensureSunExists() {
        // must be called on JME thread
        if (sun == null) {
            sun = new DirectionalLight();
            app.getRootNode().addLight(sun);
        }
        // keep shadows bound to same light
        if (sunShadow != null) sunShadow.setLight(sun);
    }

    private void ensureAmbientExists() {
        // must be called on JME thread
        if (ambient == null) {
            ambient = new AmbientLight();
            app.getRootNode().addLight(ambient);
        }
    }

    // ------------------------------------------------------------
    // Lighting
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ambient(double r, double g, double b, double intensity) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("ambient");
            ensureAmbientExists();
            ambient.setColor(new ColorRGBA((float) r, (float) g, (float) b, 1f)
                    .mult((float) Math.max(0.0, intensity)));
        });
    }

    @HostAccess.Export
    @Override
    public void sun(double dx, double dy, double dz,
                    double r, double g, double b,
                    double intensity) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("sun");
            ensureSunExists();

            Vector3f dir = new Vector3f((float) dx, (float) dy, (float) dz);
            if (dir.lengthSquared() < 1e-6f) dir.set(-1, -1, -1);
            dir.normalizeLocal();

            sun.setDirection(dir);
            sun.setColor(new ColorRGBA((float) r, (float) g, (float) b, 1f)
                    .mult((float) Math.max(0.0, intensity)));

            if (terrain != null) terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

            if (sunShadow != null) sunShadow.setLight(sun);
        });
    }

    @HostAccess.Export
    @Override
    public void sunShadows(double mapSizeD, double splitsD, double lambdaD) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("sunShadows");
            ensureSunExists();

            int ms = clamp((int) Math.round(mapSizeD), 256, 8192);
            int sp = clamp((int) Math.round(splitsD), 1, 4);
            float lambda = (float) clamp(lambdaD, 0.0, 1.0);

            if (sunShadow != null) {
                try { app.getViewPort().removeProcessor(sunShadow); } catch (Exception ignored) {}
                sunShadow = null;
            }

            sunShadow = new DirectionalLightShadowRenderer(assets, ms, sp);
            sunShadow.setLight(sun);
            sunShadow.setLambda(lambda);
            app.getViewPort().addProcessor(sunShadow);

            log.info("RenderApi: sun shadows enabled ({}px, splits={}, lambda={})", ms, sp, lambda);
        });
    }

    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        int map = intClampR(cfg, "mapSize", 2048, 256, 8192);
        int sp = intClampR(cfg, "splits", 3, 1, 4);
        float l = (float) numClamp(cfg, "lambda", 0.65, 0.0, 1.0);
        sunShadows(map, sp, l);
    }

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

    // ------------------------------------------------------------
    // Sky / Fog
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void skyboxCube(String cubeMapAsset) {
        ensureScene();
        if (cubeMapAsset == null || cubeMapAsset.isBlank()) {
            throw new IllegalArgumentException("skyboxCube: cubeMapAsset is empty");
        }
        onJme(() -> {
            ensureViewportContract("skyboxCube");

            if (sky != null) {
                sky.removeFromParent();
                sky = null;
            }
            sky = SkyFactory.createSky(assets, cubeMapAsset.trim(), SkyFactory.EnvMapType.CubeMap);
            app.getRootNode().attachChild(sky);

            log.info("RenderApi: skybox set {}", cubeMapAsset);
        });
    }

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("fogCfg");

            double r = num(cfg, "r", numPath(cfg, "color", "r", _fogBaseR));
            double g = num(cfg, "g", numPath(cfg, "color", "g", _fogBaseG));
            double b = num(cfg, "b", numPath(cfg, "color", "b", _fogBaseB));

            double density = num(cfg, "density", _fogDensity);
            double distance = num(cfg, "distance", _fogDistance);

            // AAA: allow disabling fog by setting density<=0 or distance<=0
            if (density <= 0.0 || distance <= 0.0) {
                if (fog != null && fpp != null) {
                    try { fpp.removeFilter(fog); } catch (Throwable ignored) {}
                    fog = null;
                    log.info("RenderApi: fog disabled");
                }
                _fogDensity = density;
                _fogDistance = distance;
                _fogBaseR = r; _fogBaseG = g; _fogBaseB = b;
                return;
            }

            ensureMainFpp("fogCfg");

            if (fog == null) {
                fog = new FogFilter();
                fog.setFogColor(new ColorRGBA((float) r, (float) g, (float) b, 1f));
                fog.setFogDensity((float) density);
                fog.setFogDistance((float) distance);
                fpp.addFilter(fog);

                _fogBaseR = r; _fogBaseG = g; _fogBaseB = b;
                _fogDensity = density;
                _fogDistance = distance;

                log.info("RenderApi: fog enabled (density={}, distance={})", density, distance);
                return;
            }

            boolean changed = false;

            if (Math.abs(r - _fogBaseR) > 1e-4 || Math.abs(g - _fogBaseG) > 1e-4 || Math.abs(b - _fogBaseB) > 1e-4) {
                fog.setFogColor(new ColorRGBA((float) r, (float) g, (float) b, 1f));
                _fogBaseR = r; _fogBaseG = g; _fogBaseB = b;
                changed = true;
            }
            if (Math.abs(density - _fogDensity) > 1e-4) {
                fog.setFogDensity((float) density);
                _fogDensity = density;
                changed = true;
            }
            if (Math.abs(distance - _fogDistance) > 1e-3) {
                fog.setFogDistance((float) distance);
                _fogDistance = distance;
                changed = true;
            }

            if (changed && log.isDebugEnabled()) {
                log.debug("RenderApi: fog updated (density={}, distance={})", _fogDensity, _fogDistance);
            }
        });
    }

    // ------------------------------------------------------------
    // Material helpers (terrain compatibility)
    // ------------------------------------------------------------

    private void setLayer(Material mat, int layerIndex, Texture tex, float scale) {
        String texParamA = (layerIndex == 0) ? "DiffuseMap" : ("DiffuseMap_" + layerIndex);
        String texParamB = "Tex" + (layerIndex + 1);

        boolean texOk = trySetTexture(mat, texParamA, tex) || trySetTexture(mat, texParamB, tex);
        if (!texOk) warnMissingOnce(mat, "layerTexture#" + layerIndex + " (" + texParamA + " / " + texParamB + ")");

        String scaleA = "DiffuseMap_" + layerIndex + "_scale";
        String scaleB = "Tex" + (layerIndex + 1) + "Scale";
        String scaleC = "Tex" + (layerIndex + 1) + "_Scale";
        String scaleD = "DiffuseMap_" + (layerIndex + 1) + "_scale";
        String scaleE = "DiffuseMap_" + (layerIndex + 1) + "Scale";

        boolean scaleOk =
                trySetFloat(mat, scaleA, scale) ||
                        trySetFloat(mat, scaleB, scale) ||
                        trySetFloat(mat, scaleC, scale) ||
                        trySetFloat(mat, scaleD, scale) ||
                        trySetFloat(mat, scaleE, scale);

        if (!scaleOk) warnMissingOnce(mat, "layerScale#" + layerIndex + " (" + scaleA + " / " + scaleB + ")");
    }

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

    // ------------------------------------------------------------
    // Terrain cfg (compat + JS-first)
    // ------------------------------------------------------------

    private boolean applyTerrainMaterialHandle(Value cfg) {
        if (terrain == null) return false;

        try {
            Value m = member(cfg, "material");
            if (m == null || m.isNull()) return false;

            Object host = null;
            if (m.isHostObject()) host = m.asHostObject();

            if (host instanceof MaterialApiImpl.MaterialHandle mh && mh.__material() != null) {
                onJme(() -> {
                    terrain.setMaterial(mh.__material());
                    terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                });
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("render.terrain: failed to apply cfg.material handle: {}", e.toString());
            return false;
        }
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

            if (l.isString()) return new Layer(l.asString(), 32.0);

            String tex = str(l, "tex", null);
            double scale = num(l, "scale", 32.0);

            if (scale < 0.001) scale = 0.001;
            if (scale > 4096) scale = 4096;

            return new Layer(tex, scale);
        } catch (Exception e) {
            return new Layer(null, 32.0);
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static String str(Value v, String k, String def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asString();
        } catch (Exception e) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            return (m == null || m.isNull()) ? def : m.asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static double numPath(Value v, String k1, String k2, double def) {
        try {
            Value a = member(v, k1);
            if (a == null || a.isNull() || !a.hasMember(k2)) return def;
            Value b = a.getMember(k2);
            return (b == null || b.isNull()) ? def : b.asDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static float vec3x(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(0).asDouble();
            return (float) v.getMember("x").asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3y(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(1).asDouble();
            return (float) v.getMember("y").asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3z(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
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

    // ------------------------------------------------------------
    // Internal bridge for other APIs (EditorLines, etc.)
    // ------------------------------------------------------------

    EditorLinesBridge __editorLinesBridge() {
        return new EditorLinesBridge();
    }

    final class EditorLinesBridge {
        RenderApiImpl.SpatialHandle register(Spatial s) { return registerSpatial(s); }
        Spatial require(Object handle, String where) { return requireSpatial(handle, where); }
        int handleId(Object handle, String where) { return RenderApiImpl.this.handleId(handle, where); }
        void remove(int id) { spatials.remove(id); }
        SimpleApplication app() { return app; }
        AssetManager assets() { return assets; }
    }
}