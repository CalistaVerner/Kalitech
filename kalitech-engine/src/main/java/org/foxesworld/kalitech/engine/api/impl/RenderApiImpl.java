// FILE: org/foxesworld/kalitech/engine/api/impl/RenderApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;

public final class RenderApiImpl implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    // minimal shadow policy (since API now passes only mapSize)
    private static final int   DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;

    // Fog state cache (prevents spam + avoids redundant updates)
    private double _fogBaseR = 0.70;
    private double _fogBaseG = 0.78;
    private double _fogBaseB = 0.90;
    private double _fogDensity = 1.2;
    private double _fogDistance = 250.0;

    private final EngineApiImpl engineApi;
    private final SimpleApplication app;
    private final AssetManager assets;
    @SuppressWarnings("unused")
    private final EcsWorld ecs;

    private volatile boolean sceneReady = false;

    // lights/shadows
    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLightShadowRenderer sunShadow;

    // post stack
    private FilterPostProcessor fpp;
    private FogFilter fog;
    private FXAAFilter fxaa;
    private BloomFilter bloom;
    private ToneMapFilter tonemap;

    // Optional SSAO filter (kept as Object + reflection so the build doesn't require the module)
    private Object ssao;

    // caches to avoid redundant light updates
    private float _sunDx = Float.NaN, _sunDy = Float.NaN, _sunDz = Float.NaN;
    private float _sunR = Float.NaN, _sunG = Float.NaN, _sunB = Float.NaN, _sunI = Float.NaN;
    private float _ambR = Float.NaN, _ambG = Float.NaN, _ambB = Float.NaN, _ambI = Float.NaN;

    // post switches cache
    private boolean _fxaaEnabled = false;
    private boolean _bloomEnabled = false;
    private boolean _tonemapEnabled = false;
    private boolean _ssaoEnabled = false;

    public RenderApiImpl(EngineApiImpl engineApi) {
        this.engineApi = engineApi;
        this.app = engineApi.getApp();
        this.assets = engineApi.getAssets();
        this.ecs = engineApi.getEcs();
    }

    // ------------------------------------------------------------
    // Threading
    // ------------------------------------------------------------

    private void onJme(Runnable r) {
        if (engineApi.isJmeThread()) r.run();
        else app.enqueue(() -> { r.run(); return null; });
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
            ensureAmbientExists();
            ensureSunExists();
            ensureMainFpp("ensureScene");
            ensureFogExists();
            log.info("RenderApi: scene ensured");
        });
    }

    private void ensureViewportContract(String where) {
        // Ensure MAIN has rootNode, GUI has guiNode. Prevent scene corruption.
        try {
            ViewPort main = app.getViewPort();
            ViewPort gui  = app.getGuiViewPort();
            if (main == null || gui == null) return;

            Node root = app.getRootNode();
            Node guiNode = app.getGuiNode();

            if (!main.getScenes().contains(root)) {
                main.attachScene(root);
                log.info("RenderApi: {} attach rootNode to MAIN", where);
            }
            if (main.getScenes().contains(guiNode)) {
                main.detachScene(guiNode);
                log.warn("RenderApi: {} detached guiNode from MAIN (fix)", where);
            }

            if (!gui.getScenes().contains(guiNode)) {
                gui.attachScene(guiNode);
                log.info("RenderApi: {} attach guiNode to GUI", where);
            }
            if (gui.getScenes().contains(root)) {
                gui.detachScene(root);
                log.warn("RenderApi: {} detached rootNode from GUI (fix)", where);
            }
        } catch (Throwable t) {
            log.warn("RenderApi: ensureViewportContract failed: {}", t.toString());
        }
    }

    private void ensureMainFpp(String where) {
        if (fpp != null) return;
        fpp = new FilterPostProcessor(assets);
        app.getViewPort().addProcessor(fpp);
        log.info("RenderApi: {} main FPP created", where);
    }

    private void ensureAmbientExists() {
        if (ambient != null) return;
        ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.25f, 0.28f, 0.35f, 1f));
        app.getRootNode().addLight(ambient);
        log.info("RenderApi: ambient created");
    }

    private void ensureSunExists() {
        if (sun != null) return;
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f, 0.98f, 0.90f, 1f).mult(1.2f));
        app.getRootNode().addLight(sun);
        log.info("RenderApi: sun created");
    }

    private void ensureFogExists() {
        if (fog != null) return;
        ensureMainFpp("ensureFogExists");
        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA((float) _fogBaseR, (float) _fogBaseG, (float) _fogBaseB, 1f));
        fog.setFogDensity((float) _fogDensity);
        fog.setFogDistance((float) _fogDistance);
        fpp.addFilter(fog);
        log.info("RenderApi: fog filter created");
    }

    // ------------------------------------------------------------
    // Lighting (config style only)
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ambientCfg(Value cfg) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("ambientCfg");
            ensureAmbientExists();

            double r = num(cfg, "r", numPath(cfg, "color", "r", 0.25));
            double g = num(cfg, "g", numPath(cfg, "color", "g", 0.28));
            double b = num(cfg, "b", numPath(cfg, "color", "b", 0.35));
            double intensity = num(cfg, "intensity", 1.0);

            float fr = (float) r, fg = (float) g, fb = (float) b;
            float fi = (float) Math.max(0.0, intensity);

            if (approx(fr, _ambR) && approx(fg, _ambG) && approx(fb, _ambB) && approx(fi, _ambI)) return;
            _ambR = fr; _ambG = fg; _ambB = fb; _ambI = fi;

            ambient.setColor(new ColorRGBA(fr, fg, fb, 1f).mult(fi));
        });
    }

    @HostAccess.Export
    @Override
    public void sunCfg(Value cfg) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("sunCfg");
            ensureSunExists();

            Value dir = member(cfg, "dir");
            Value col = member(cfg, "color");

            float dx = vec3x(dir, -1f);
            float dy = vec3y(dir, -1f);
            float dz = vec3z(dir, -0.3f);

            float r = vec3x(col, 1f);
            float g = vec3y(col, 0.98f);
            float b = vec3z(col, 0.9f);

            float intensity = (float) Math.max(0.0, num(cfg, "intensity", 1.2));

            if (approx(dx, _sunDx) && approx(dy, _sunDy) && approx(dz, _sunDz) &&
                    approx(r, _sunR) && approx(g, _sunG) && approx(b, _sunB) && approx(intensity, _sunI)) {
                return;
            }
            _sunDx = dx; _sunDy = dy; _sunDz = dz;
            _sunR = r; _sunG = g; _sunB = b; _sunI = intensity;

            Vector3f v = new Vector3f(dx, dy, dz);
            if (v.lengthSquared() < 1e-6f) v.set(-1, -1, -1);
            v.normalizeLocal();

            sun.setDirection(v);
            sun.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));

            if (sunShadow != null) sunShadow.setLight(sun);
        });
    }

    // ------------------------------------------------------------
    // Shadows (minimal: only map size)
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void sunShadows(int mapSize) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("sunShadows");
            ensureSunExists();

            // remove old
            if (sunShadow != null) {
                try { app.getViewPort().removeProcessor(sunShadow); } catch (Throwable ignored) {}
                sunShadow = null;
            }

            if (mapSize <= 0) {
                log.info("RenderApi: shadows disabled");
                return;
            }

            int ms = Math.max(256, Math.min(mapSize, 8192));

            sunShadow = new DirectionalLightShadowRenderer(assets, ms, DEFAULT_SHADOW_SPLITS);
            sunShadow.setLight(sun);
            sunShadow.setLambda(DEFAULT_SHADOW_LAMBDA);
            sunShadow.setShadowIntensity(0.65f);

            app.getViewPort().addProcessor(sunShadow);
            log.info("RenderApi: shadows enabled mapSize={} splits={} lambda={}", ms, DEFAULT_SHADOW_SPLITS, DEFAULT_SHADOW_LAMBDA);
        });
    }

    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        int map = intClampR(cfg, "mapSize", 2048, 0, 8192); // 0 disables
        sunShadows(map);
    }

    // ------------------------------------------------------------
    // Fog
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("fogCfg");

            double r = num(cfg, "r", numPath(cfg, "color", "r", _fogBaseR));
            double g = num(cfg, "g", numPath(cfg, "color", "g", _fogBaseG));
            double b = num(cfg, "b", numPath(cfg, "color", "b", _fogBaseB));

            double density  = num(cfg, "density", _fogDensity);
            double distance = num(cfg, "distance", _fogDistance);

            // disable fog by density<=0 or distance<=0
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

            ensureFogExists();

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
    // Post-processing (no exposure API)
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void postCfg(Value cfg) {
        ensureScene();
        onJme(() -> {
            ensureViewportContract("postCfg");
            ensureMainFpp("postCfg");

            boolean fx = bool(cfg, "fxaa", false);
            boolean bl = bool(cfg, "bloom", false);
            boolean tm = bool(cfg, "tonemap", false);
            boolean ao = bool(cfg, "ssao", false);

            // FXAA
            if (fx != _fxaaEnabled) {
                _fxaaEnabled = fx;
                if (fx) {
                    if (fxaa == null) fxaa = new FXAAFilter();
                    addFilterOnce(fxaa);
                    log.info("RenderApi: FXAA enabled");
                } else {
                    removeFilterSafe(fxaa);
                    log.info("RenderApi: FXAA disabled");
                }
            }

            // Bloom
            if (bl != _bloomEnabled) {
                _bloomEnabled = bl;
                if (bl) {
                    if (bloom == null) bloom = new BloomFilter();
                    bloom.setBloomIntensity((float) num(cfg, "bloomIntensity", 1.2));
                    bloom.setExposurePower((float) num(cfg, "bloomExposure", 2.0));
                    addFilterOnce(bloom);
                    log.info("RenderApi: Bloom enabled");
                } else {
                    removeFilterSafe(bloom);
                    log.info("RenderApi: Bloom disabled");
                }
            } else if (bl && bloom != null) {
                bloom.setBloomIntensity((float) num(cfg, "bloomIntensity", bloom.getBloomIntensity()));
                bloom.setExposurePower((float) num(cfg, "bloomExposure", bloom.getExposurePower()));
            }

            // Tonemap (no setExposure in your build → only enable/disable)
            if (tm != _tonemapEnabled) {
                _tonemapEnabled = tm;
                if (tm) {
                    if (tonemap == null) tonemap = new ToneMapFilter();
                    addFilterOnce(tonemap);
                    log.info("RenderApi: ToneMap enabled");
                } else {
                    removeFilterSafe(tonemap);
                    log.info("RenderApi: ToneMap disabled");
                }
            }

            // SSAO (optional, reflection)
            if (ao != _ssaoEnabled) {
                _ssaoEnabled = ao;
                if (ao) {
                    if (ssao == null) ssao = createSsaoIfAvailable();
                    if (ssao != null) {
                        addFilterOnce(ssao);
                        log.info("RenderApi: SSAO enabled");
                    } else {
                        log.warn("RenderApi: SSAO requested but filter not available on classpath");
                    }
                } else {
                    removeFilterSafe(ssao);
                    log.info("RenderApi: SSAO disabled");
                }
            }
        });
    }

    // ------------------------------------------------------------
    // Post helpers
    // ------------------------------------------------------------

    private void addFilterOnce(Object filter) {
        if (filter == null) return;
        ensureMainFpp("addFilterOnce");

        if (filter instanceof com.jme3.post.Filter f) {
            if (!fpp.getFilterList().contains(f)) fpp.addFilter(f);
            return;
        }
        try {
            var m = fpp.getClass().getMethod("addFilter", com.jme3.post.Filter.class);
            m.invoke(fpp, filter);
        } catch (Throwable ignored) {}
    }

    private void removeFilterSafe(Object filter) {
        if (filter == null || fpp == null) return;

        if (filter instanceof com.jme3.post.Filter f) {
            try { fpp.removeFilter(f); } catch (Throwable ignored) {}
            return;
        }
        try {
            var m = fpp.getClass().getMethod("removeFilter", com.jme3.post.Filter.class);
            m.invoke(fpp, filter);
        } catch (Throwable ignored) {}
    }

    private Object createSsaoIfAvailable() {
        try {
            Class<?> c = Class.forName("com.jme3.post.ssao.SSAOFilter");
            return c.getConstructor().newInstance();
        } catch (Throwable t) {
            if (log.isDebugEnabled()) log.debug("RenderApi: SSAOFilter not available: {}", t.toString());
            return null;
        }
    }

    // ------------------------------------------------------------
    // Value parsing helpers
    // ------------------------------------------------------------

    private static boolean approx(float a, float b) {
        return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) < 1e-4f;
    }

    private static Value member(Value v, String k) {
        return (v != null && v.hasMember(k)) ? v.getMember(k) : null;
    }

    private static boolean bool(Value v, String k, boolean def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            if (m.isBoolean()) return m.asBoolean();
            if (m.isNumber()) return m.asDouble() != 0.0;
            if (m.isString()) {
                String s = m.asString().trim().toLowerCase();
                if (s.isEmpty()) return def;
                return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
            }
            return def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double num(Value v, String k, double def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            if (m.isNumber()) return m.asDouble();
            if (m.isString()) return Double.parseDouble(m.asString());
            return def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double numPath(Value v, String k, String kk, double def) {
        try {
            Value m = member(v, k);
            if (m == null || m.isNull()) return def;
            return num(m, kk, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static float vec3x(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(0).asDouble();
            Value x = member(v, "x");
            return (x == null || x.isNull()) ? def : (float) x.asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3y(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(1).asDouble();
            Value y = member(v, "y");
            return (y == null || y.isNull()) ? def : (float) y.asDouble();
        } catch (Exception e) { return def; }
    }

    private static float vec3z(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasArrayElements()) return (float) v.getArrayElement(2).asDouble();
            Value z = member(v, "z");
            return (z == null || z.isNull()) ? def : (float) z.asDouble();
        } catch (Exception e) { return def; }
    }
}