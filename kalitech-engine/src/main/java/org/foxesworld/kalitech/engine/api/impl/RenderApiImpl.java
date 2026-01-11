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
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.render.post.TonemapFilter;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;

public final class RenderApiImpl extends AbstractApiModule implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    private static final int   DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;

    // --- Fog safety bounds ---
    private static final double FOG_DENSITY_MIN = 0.0;
    private static final double FOG_DENSITY_MAX = 0.03;
    private static final double FOG_DISTANCE_MIN = 25.0;

    private double _fogBaseR = 0.70;
    private double _fogBaseG = 0.78;
    private double _fogBaseB = 0.90;

    private double _fogDensity = 0.006;
    private double _fogDistance = 250.0;

    private SimpleApplication app;
    private AssetManager assets;
    @SuppressWarnings("unused")
    private EcsWorld ecs;

    private volatile boolean sceneReady = false;

    private AmbientLight ambient;

    // --- two directionals: sun + moon ---
    private DirectionalLight sun;
    private DirectionalLight moon;

    // --- which one casts shadows right now ---
    private String primaryDirectional = "sun"; // "sun" | "moon"

    private DirectionalLightShadowRenderer sunShadow;

    private FilterPostProcessor fpp;
    private FogFilter fog;
    private FXAAFilter fxaa;
    private BloomFilter bloom;
    private TonemapFilter tonemap;

    private float _sunDx = Float.NaN, _sunDy = Float.NaN, _sunDz = Float.NaN;
    private float _sunR = Float.NaN, _sunG = Float.NaN, _sunB = Float.NaN, _sunI = Float.NaN;

    private float _moonDx = Float.NaN, _moonDy = Float.NaN, _moonDz = Float.NaN;
    private float _moonR = Float.NaN, _moonG = Float.NaN, _moonB = Float.NaN, _moonI = Float.NaN;

    private float _ambR = Float.NaN, _ambG = Float.NaN, _ambB = Float.NaN, _ambI = Float.NaN;

    private Spatial skybox;
    private String skyboxAsset = "";

    // shadows runtime cfg
    private int _shMapSize = -1;
    private int _shSplits = -1;
    private float _shLambda = Float.NaN;
    private float _shIntensity = Float.NaN;

    public RenderApiImpl() {
        super("render", "Render", "1.0.0");
    }

    private static Value member(Value v, String key) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return null;
            return v.getMember(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean bool(Value v, String key, boolean def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return def;
            Value m = v.getMember(key);
            if (m == null || m.isNull()) return def;
            return m.asBoolean();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static double num(Value v, String key, double def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return def;
            Value m = v.getMember(key);
            if (m == null || m.isNull()) return def;
            return m.asDouble();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static double numPath(Value cfg, String objKey, String key, double def) {
        try {
            Value o = member(cfg, objKey);
            if (o == null || o.isNull()) return def;
            return num(o, key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static float vec3x(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasMember("x")) return (float) v.getMember("x").asDouble();
            if (v.hasArrayElements() && v.getArraySize() > 0) return (float) v.getArrayElement(0).asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static float vec3y(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasMember("y")) return (float) v.getMember("y").asDouble();
            if (v.hasArrayElements() && v.getArraySize() > 1) return (float) v.getArrayElement(1).asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static float vec3z(Value v, float def) {
        try {
            if (v == null || v.isNull()) return def;
            if (v.hasMember("z")) return (float) v.getMember("z").asDouble();
            if (v.hasArrayElements() && v.getArraySize() > 2) return (float) v.getArrayElement(2).asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static boolean approx(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        return Math.abs(a - b) <= 1e-6f;
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.ecs = ctx.ecs;
    }

    private void onJme(Runnable r) {
        if (engine.isJmeThread()) r.run();
        else app.enqueue(() -> {
            r.run();
            return null;
        });
    }

    private void ensureViewportContract(String where) {
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

    private void ensureMoonExists() {
        if (moon != null) return;
        moon = new DirectionalLight();
        moon.setDirection(new Vector3f(1, -1, 0.3f).normalizeLocal());
        moon.setColor(new ColorRGBA(0.45f, 0.55f, 0.85f, 1f).mult(0.0f)); // start off
        app.getRootNode().addLight(moon);
        log.info("RenderApi: moon created");
    }

    private DirectionalLight primaryLight() {
        if ("moon".equals(primaryDirectional)) return moon != null ? moon : sun;
        return sun;
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

    @HostAccess.Export
    @Override
    public void ensureScene() {
        profiledVoid(() -> {
            if (sceneReady) return;
            sceneReady = true;

            onJme(() -> {
                ensureViewportContract("ensureScene");
                ensureAmbientExists();
                ensureSunExists();
                ensureMoonExists();
                ensureMainFpp("ensureScene");
                log.info("RenderApi: scene ensured");
            });
        });
    }

    // ---------- skybox ----------

    @HostAccess.Export
    public void skyboxClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("skyboxClear");
                if (skybox != null) {
                    try {
                        skybox.removeFromParent();
                    } catch (Throwable ignored) {
                    }
                    skybox = null;
                }
                skyboxAsset = "";
                log.info("RenderApi: skybox cleared");
            });
        });
    }

    @HostAccess.Export
    public void skyboxCube(String asset) {
        profiledVoid(() -> {
            ensureScene();
            if (asset == null || asset.isBlank()) {
                skyboxClear();
                return;
            }
            final String a = asset.trim();
            onJme(() -> {
                ensureViewportContract("skyboxCube");

                if (a.equals(skyboxAsset) && skybox != null) return;

                if (skybox != null) {
                    try {
                        skybox.removeFromParent();
                    } catch (Throwable ignored) {
                    }
                    skybox = null;
                }

                try {
                    Texture tex = assets.loadTexture(a);
                    Spatial s = SkyFactory.createSky(assets, tex, SkyFactory.EnvMapType.CubeMap);
                    s.setQueueBucket(RenderQueue.Bucket.Sky);
                    s.setCullHint(Spatial.CullHint.Never);

                    app.getRootNode().attachChild(s);

                    skybox = s;
                    skyboxAsset = a;

                    log.info("RenderApi: skybox set asset='{}'", a);
                } catch (Throwable t) {
                    skybox = null;
                    skyboxAsset = "";
                    log.error("RenderApi: skyboxCube failed asset='{}'", a, t);
                }
            });
        });
    }

    // ---------- ambient ----------

    @HostAccess.Export
    @Override
    public void ambientCfg(Value cfg) {
        profiledVoid(() -> {
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
                _ambR = fr;
                _ambG = fg;
                _ambB = fb;
                _ambI = fi;

                ambient.setColor(new ColorRGBA(fr, fg, fb, 1f).mult(fi));
            });
        });
    }

    // ---------- primary directional selector ----------

    @HostAccess.Export
    public void setPrimaryDirectional(String which) {
        final String w = (which == null ? "" : which.trim().toLowerCase());
        if (!w.equals("sun") && !w.equals("moon")) return;

        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("setPrimaryDirectional");
                ensureSunExists();
                ensureMoonExists();

                if (w.equals(primaryDirectional)) return;
                primaryDirectional = w;

                if (sunShadow != null) {
                    sunShadow.setLight(primaryLight());
                }

                log.info("RenderApi: primaryDirectional={}", primaryDirectional);
            });
        });
    }

    // ---------- sun ----------

    @HostAccess.Export
    @Override
    public void sunCfg(Value cfg) {
        profiledVoid(() -> {
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
                _sunDx = dx;
                _sunDy = dy;
                _sunDz = dz;
                _sunR = r;
                _sunG = g;
                _sunB = b;
                _sunI = intensity;

                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1e-6f) v.set(-1, -1, -1);
                v.normalizeLocal();

                sun.setDirection(v);
                sun.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));

                if (sunShadow != null && "sun".equals(primaryDirectional)) {
                    sunShadow.setLight(sun);
                }
            });
        });
    }

    // ---------- moon (NEW) ----------

    @HostAccess.Export
    public void moonCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("moonCfg");
                ensureMoonExists();

                Value dir = member(cfg, "dir");
                Value col = member(cfg, "color");

                float dx = vec3x(dir, 1f);
                float dy = vec3y(dir, -1f);
                float dz = vec3z(dir, 0.3f);

                float r = vec3x(col, 0.45f);
                float g = vec3y(col, 0.55f);
                float b = vec3z(col, 0.85f);

                float intensity = (float) Math.max(0.0, num(cfg, "intensity", 0.0));

                if (approx(dx, _moonDx) && approx(dy, _moonDy) && approx(dz, _moonDz) &&
                        approx(r, _moonR) && approx(g, _moonG) && approx(b, _moonB) && approx(intensity, _moonI)) {
                    return;
                }
                _moonDx = dx;
                _moonDy = dy;
                _moonDz = dz;
                _moonR = r;
                _moonG = g;
                _moonB = b;
                _moonI = intensity;

                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1e-6f) v.set(1, -1, 0);
                v.normalizeLocal();

                moon.setDirection(v);
                moon.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));

                if (sunShadow != null && "moon".equals(primaryDirectional)) {
                    sunShadow.setLight(moon);
                }
            });
        });
    }

    // ---------- shadows ----------

    @HostAccess.Export
    @Override
    public void sunShadows(int mapSize) {
        // backward-compat: keep signature, but default splits/lambda/intensity
        sunShadowsEx(mapSize, DEFAULT_SHADOW_SPLITS, DEFAULT_SHADOW_LAMBDA, DEFAULT_SHADOW_INTENSITY);
    }

    @HostAccess.Export
    public void sunShadowsEx(int mapSize, int splits, double lambda, double intensity) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("sunShadowsEx");
                ensureSunExists();
                ensureMoonExists();

                // 0 = выключить тени
                if (mapSize <= 0) {
                    if (sunShadow != null) {
                        try {
                            app.getViewPort().removeProcessor(sunShadow);
                        } catch (Throwable ignored) {
                        }
                        sunShadow = null;
                    }
                    _shMapSize = 0;
                    log.info("RenderApi: shadows disabled");
                    return;
                }

                // 🔥 КРИТИЧЕСКИЙ CLAMP: 16384 почти всегда overkill и часто убивает FPS
                // Для AAA-стартового пресета лучше 4096/8192 максимум.
                int ms = Math.max(256, Math.min(mapSize, 8192));
                int sp = Math.max(1, Math.min(splits, 4));
                float lam = (float) Math.max(0.0, Math.min(lambda, 1.0));
                float inten = (float) Math.max(0.0, Math.min(intensity, 1.0));

                // ✅ Если renderer уже есть и параметры карты не менялись — НЕ пересоздаём!
                if (sunShadow != null && _shMapSize == ms && _shSplits == sp) {
                    _shLambda = lam;
                    _shIntensity = inten;

                    sunShadow.setLight(primaryLight());
                    sunShadow.setLambda(lam);
                    sunShadow.setShadowIntensity(inten);

                    // лог спамить не надо
                    return;
                }

                // иначе — пересоздаём (это тяжёлая операция)
                if (sunShadow != null) {
                    try {
                        app.getViewPort().removeProcessor(sunShadow);
                    } catch (Throwable ignored) {
                    }
                    sunShadow = null;
                }

                _shMapSize = ms;
                _shSplits = sp;
                _shLambda = lam;
                _shIntensity = inten;

                sunShadow = new DirectionalLightShadowRenderer(assets, ms, sp);
                sunShadow.setLight(primaryLight());
                sunShadow.setLambda(lam);
                sunShadow.setShadowIntensity(inten);

                app.getViewPort().addProcessor(sunShadow);

                log.info("RenderApi: shadows enabled mapSize={} splits={} lambda={} intensity={} primary={}",
                        ms, sp, lam, inten, primaryDirectional);
            });
        });
    }


    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        int map = intClampR(cfg, "mapSize", 2048, 0, 16384);

        int splits = intClampR(cfg, "splits", DEFAULT_SHADOW_SPLITS, 1, 8);
        double lambda = num(cfg, "lambda", DEFAULT_SHADOW_LAMBDA);
        double intensity = num(cfg, "intensity", DEFAULT_SHADOW_INTENSITY);

        sunShadowsEx(map, splits, lambda, intensity);
    }

    // ---------- fog ----------

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("fogCfg");
                ensureFogExists();

                double r = num(cfg, "r", numPath(cfg, "color", "r", _fogBaseR));
                double g = num(cfg, "g", numPath(cfg, "color", "g", _fogBaseG));
                double b = num(cfg, "b", numPath(cfg, "color", "b", _fogBaseB));

                double density = num(cfg, "density", _fogDensity);
                double distance = num(cfg, "distance", _fogDistance);

                _fogBaseR = r;
                _fogBaseG = g;
                _fogBaseB = b;

                density = Math.max(FOG_DENSITY_MIN, Math.min(density, FOG_DENSITY_MAX));
                distance = Math.max(FOG_DISTANCE_MIN, distance);

                _fogDensity = density;
                _fogDistance = distance;

                fog.setFogColor(new ColorRGBA((float) r, (float) g, (float) b, 1f));
                fog.setFogDensity((float) density);
                fog.setFogDistance((float) distance);
            });
        });
    }

    @Override
    public void postCfg(Value cfg) {

    }
}