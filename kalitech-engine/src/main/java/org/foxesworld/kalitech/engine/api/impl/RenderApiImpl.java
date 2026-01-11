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
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
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

    // --- legacy skybox (still supported, but SkyDome will remove it) ---
    private Spatial skybox;
    private String skyboxAsset = "";

    // --- SkyDome (procedural) ---
    private Spatial skydome;
    private Material skydomeMat;

    private float _sdSunDx = Float.NaN, _sdSunDy = Float.NaN, _sdSunDz = Float.NaN;
    private float _sdMoonDx = Float.NaN, _sdMoonDy = Float.NaN, _sdMoonDz = Float.NaN;

    private float _sdSunR = Float.NaN, _sdSunG = Float.NaN, _sdSunB = Float.NaN, _sdSunI = Float.NaN;
    private float _sdMoonR = Float.NaN, _sdMoonG = Float.NaN, _sdMoonB = Float.NaN, _sdMoonI = Float.NaN;

    private float _sdZenR = Float.NaN, _sdZenG = Float.NaN, _sdZenB = Float.NaN;
    private float _sdHorR = Float.NaN, _sdHorG = Float.NaN, _sdHorB = Float.NaN;

    private float _sdHaze = Float.NaN;
    private float _sdSunDisk = Float.NaN;
    private float _sdMoonDisk = Float.NaN;
    private float _sdExposure = Float.NaN;

    // shadows runtime cfg (base)
    private int _shMapSize = -1;
    private int _shSplits = -1;
    private float _shLambda = Float.NaN;
    private float _shIntensity = Float.NaN;

    // shadows runtime cfg (softness/penumbra knobs - cached for future shader binding)
    private float _shSoftness = Float.NaN;
    private int _shPcfSamples = -1;
    private boolean _shPcss = false;
    private float _shLightRadius = Float.NaN;

    // post runtime cfg cache
    private boolean _postEnabled = true;
    private float _postExposure = Float.NaN;
    private float _postWhitePoint = Float.NaN;
    private float _postShoulder = Float.NaN;
    private float _postToe = Float.NaN;
    private float _postSaturation = Float.NaN;

    public RenderApiImpl() {
        super("render", "Render", "1.0.0");
    }

    // ------------------------------------------------------------
    // Strict cfg readers (NO silent try/catch)
    // ------------------------------------------------------------

    private static Value member(Value v, String key) {
        if (v == null || v.isNull()) return null;
        if (!v.hasMember(key)) return null;
        Value m = v.getMember(key);
        if (m == null || m.isNull()) return null;
        return m;
    }

    private static boolean bool(Value v, String key, boolean def) {
        Value m = member(v, key);
        if (m == null) return def;
        if (!m.isBoolean()) {
            throw new IllegalArgumentException("[render] cfg '" + key + "' must be boolean");
        }
        return m.asBoolean();
    }

    private static double num(Value v, String key, double def) {
        Value m = member(v, key);
        if (m == null) return def;
        if (!m.fitsInDouble()) {
            throw new IllegalArgumentException("[render] cfg '" + key + "' must be number");
        }
        return m.asDouble();
    }

    private static double numPath(Value cfg, String objKey, String key, double def) {
        Value o = member(cfg, objKey);
        if (o == null) return def;
        return num(o, key, def);
    }

    private static float vec3x(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("x")) {
            Value m = v.getMember("x");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 0) {
            return (float) v.getArrayElement(0).asDouble();
        }
        return def;
    }

    private static float vec3y(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("y")) {
            Value m = v.getMember("y");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 1) {
            return (float) v.getArrayElement(1).asDouble();
        }
        return def;
    }

    private static float vec3z(Value v, float def) {
        if (v == null || v.isNull()) return def;
        if (v.hasMember("z")) {
            Value m = v.getMember("z");
            if (m != null && !m.isNull()) return (float) m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 2) {
            return (float) v.getArrayElement(2).asDouble();
        }
        return def;
    }

    private static boolean approx(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        return Math.abs(a - b) <= 1e-6f;
    }

    private static boolean approx3(float ax, float ay, float az, float bx, float by, float bz) {
        if (Float.isNaN(ax) || Float.isNaN(ay) || Float.isNaN(az)) return false;
        if (Float.isNaN(bx) || Float.isNaN(by) || Float.isNaN(bz)) return false;
        return Math.abs(ax - bx) <= 1e-6f && Math.abs(ay - by) <= 1e-6f && Math.abs(az - bz) <= 1e-6f;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
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
        ViewPort main = app.getViewPort();
        ViewPort gui = app.getGuiViewPort();
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
        if ("moon".equals(primaryDirectional)) return (moon != null ? moon : sun);
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

    private void ensureFxaaExists() {
        if (fxaa != null) return;
        ensureMainFpp("ensureFxaaExists");
        fxaa = new FXAAFilter();
        fpp.addFilter(fxaa);
        log.info("RenderApi: FXAA created");
    }

    private void ensureBloomExists() {
        if (bloom != null) return;
        ensureMainFpp("ensureBloomExists");
        bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        fpp.addFilter(bloom);
        log.info("RenderApi: Bloom created");
    }

    private void ensureTonemapExists() {
        if (tonemap != null) return;
        ensureMainFpp("ensureTonemapExists");
        tonemap = new TonemapFilter(this.assets);
        fpp.addFilter(tonemap);
        log.info("RenderApi: Tonemap created");
    }

    private void ensureSkyDomeExists() {
        if (skydome != null && skydomeMat != null) return;

        // AAA policy: SkyDome and SkyBox are mutually exclusive
        if (skybox != null) {
            skybox.removeFromParent();
            skybox = null;
            skyboxAsset = "";
            log.info("RenderApi: skybox removed (switch to skydome)");
        }

        Sphere sphere = new Sphere(48, 48, 1000f, false, true); // interior view
        Geometry g = new Geometry("SkyDome", sphere);
        g.setQueueBucket(RenderQueue.Bucket.Sky);
        g.setCullHint(Spatial.CullHint.Never);
        g.setShadowMode(RenderQueue.ShadowMode.Off);

        Material m = new Material(assets, "MatDefs/Sky/SkyDome.j3md");
        g.setMaterial(m);

        app.getRootNode().attachChild(g);

        skydome = g;
        skydomeMat = m;

        log.info("RenderApi: skydome created");
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

    // ---------- skydome ----------

    @HostAccess.Export
    public void skyDomeClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("skyDomeClear");
                if (skydome != null) {
                    skydome.removeFromParent();
                    skydome = null;
                }
                skydomeMat = null;
                log.info("RenderApi: skydome cleared");
            });
        });
    }

    @HostAccess.Export
    public void skyDomeCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("skyDomeCfg");
                ensureSkyDomeExists();

                Value sunDir = member(cfg, "sunDir");
                Value moonDir = member(cfg, "moonDir");
                Value sunCol = member(cfg, "sunColor");
                Value moonCol = member(cfg, "moonColor");
                Value zen = member(cfg, "zenithColor");
                Value hor = member(cfg, "horizonColor");

                float sdx = vec3x(sunDir, -1f), sdy = vec3y(sunDir, -1f), sdz = vec3z(sunDir, -0.3f);
                float mdx = vec3x(moonDir, 1f), mdy = vec3y(moonDir, -1f), mdz = vec3z(moonDir, 0.3f);

                float sr = vec3x(sunCol, 1f), sg = vec3y(sunCol, 0.98f), sb = vec3z(sunCol, 0.90f);
                float mr = vec3x(moonCol, 0.45f), mg = vec3y(moonCol, 0.55f), mb = vec3z(moonCol, 0.85f);

                float sunI = (float) Math.max(0.0, num(cfg, "sunIntensity", 1.0));
                float moonI = (float) Math.max(0.0, num(cfg, "moonIntensity", 0.0));

                float zr = vec3x(zen, 0.10f), zg = vec3y(zen, 0.17f), zb = vec3z(zen, 0.32f);
                float hr = vec3x(hor, 0.65f), hg = vec3y(hor, 0.72f), hb = vec3z(hor, 0.82f);

                float haze = clamp01((float) num(cfg, "haze", 0.55));
                float sunDisk = clamp((float) num(cfg, "sunDisk", 45.0), 0.5f, 500f);
                float moonDisk = clamp((float) num(cfg, "moonDisk", 120.0), 0.5f, 2000f);
                float exposure = clamp((float) num(cfg, "exposure", 1.0), 0.05f, 10f);

                boolean changed =
                        !approx3(sdx, sdy, sdz, _sdSunDx, _sdSunDy, _sdSunDz) ||
                                !approx3(mdx, mdy, mdz, _sdMoonDx, _sdMoonDy, _sdMoonDz) ||
                                !approx(sr, _sdSunR) || !approx(sg, _sdSunG) || !approx(sb, _sdSunB) || !approx(sunI, _sdSunI) ||
                                !approx(mr, _sdMoonR) || !approx(mg, _sdMoonG) || !approx(mb, _sdMoonB) || !approx(moonI, _sdMoonI) ||
                                !approx(zr, _sdZenR) || !approx(zg, _sdZenG) || !approx(zb, _sdZenB) ||
                                !approx(hr, _sdHorR) || !approx(hg, _sdHorG) || !approx(hb, _sdHorB) ||
                                !approx(haze, _sdHaze) ||
                                !approx(sunDisk, _sdSunDisk) ||
                                !approx(moonDisk, _sdMoonDisk) ||
                                !approx(exposure, _sdExposure);

                if (!changed) return;

                _sdSunDx = sdx;
                _sdSunDy = sdy;
                _sdSunDz = sdz;
                _sdMoonDx = mdx;
                _sdMoonDy = mdy;
                _sdMoonDz = mdz;

                _sdSunR = sr;
                _sdSunG = sg;
                _sdSunB = sb;
                _sdSunI = sunI;
                _sdMoonR = mr;
                _sdMoonG = mg;
                _sdMoonB = mb;
                _sdMoonI = moonI;

                _sdZenR = zr;
                _sdZenG = zg;
                _sdZenB = zb;
                _sdHorR = hr;
                _sdHorG = hg;
                _sdHorB = hb;

                _sdHaze = haze;
                _sdSunDisk = sunDisk;
                _sdMoonDisk = moonDisk;
                _sdExposure = exposure;

                Vector3f sdir = new Vector3f(sdx, sdy, sdz);
                if (sdir.lengthSquared() < 1e-6f) sdir.set(-1, -1, -1);
                sdir.normalizeLocal();

                Vector3f mdir = new Vector3f(mdx, mdy, mdz);
                if (mdir.lengthSquared() < 1e-6f) mdir.set(1, -1, 0);
                mdir.normalizeLocal();

                skydomeMat.setVector3("SunDir", sdir);
                skydomeMat.setVector3("MoonDir", mdir);

                skydomeMat.setColor("SunColor", new ColorRGBA(sr, sg, sb, 1f));
                skydomeMat.setFloat("SunIntensity", sunI);

                skydomeMat.setColor("MoonColor", new ColorRGBA(mr, mg, mb, 1f));
                skydomeMat.setFloat("MoonIntensity", moonI);

                skydomeMat.setColor("ZenithColor", new ColorRGBA(zr, zg, zb, 1f));
                skydomeMat.setColor("HorizonColor", new ColorRGBA(hr, hg, hb, 1f));

                skydomeMat.setFloat("Haze", haze);
                skydomeMat.setFloat("SunDisk", sunDisk);
                skydomeMat.setFloat("MoonDisk", moonDisk);
                skydomeMat.setFloat("Exposure", exposure);
            });
        });
    }

    // ---------- skybox (legacy) ----------

    @HostAccess.Export
    public void skyboxClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("skyboxClear");
                if (skybox != null) {
                    skybox.removeFromParent();
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

                // If skydome exists - you are explicitly choosing skybox now, so remove skydome.
                if (skydome != null) {
                    skydome.removeFromParent();
                    skydome = null;
                    skydomeMat = null;
                    log.info("RenderApi: skydome removed (switch to skybox)");
                }

                if (a.equals(skyboxAsset) && skybox != null) return;

                if (skybox != null) {
                    skybox.removeFromParent();
                    skybox = null;
                }

                Texture tex = assets.loadTexture(a);
                Spatial s = SkyFactory.createSky(assets, tex, SkyFactory.EnvMapType.CubeMap);
                s.setQueueBucket(RenderQueue.Bucket.Sky);
                s.setCullHint(Spatial.CullHint.Never);

                app.getRootNode().attachChild(s);

                skybox = s;
                skyboxAsset = a;

                log.info("RenderApi: skybox set asset='{}'", a);
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
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }

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

    // ---------- moon ----------

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
                        app.getViewPort().removeProcessor(sunShadow);
                        sunShadow = null;
                    }
                    _shMapSize = 0;
                    log.info("RenderApi: shadows disabled");
                    return;
                }

                // clamp
                int ms = Math.max(256, Math.min(mapSize, 8192));
                int sp = Math.max(1, Math.min(splits, 4));
                float lam = (float) Math.max(0.0, Math.min(lambda, 1.0));
                float inten = (float) Math.max(0.0, Math.min(intensity, 1.0));

                // ✅ If renderer exists and map/splits unchanged — update only params
                if (sunShadow != null && _shMapSize == ms && _shSplits == sp) {
                    _shLambda = lam;
                    _shIntensity = inten;

                    sunShadow.setLight(primaryLight());
                    sunShadow.setLambda(lam);
                    sunShadow.setShadowIntensity(inten);
                    return;
                }

                // heavy recreate
                if (sunShadow != null) {
                    app.getViewPort().removeProcessor(sunShadow);
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
    public void skyDomeTex(String asset) {
        profiledVoid(() -> {
            ensureScene();
            if (asset == null || asset.isBlank()) {
                throw new IllegalArgumentException("[render] skyDomeTex: asset is blank");
            }
            final String a = asset.trim();

            onJme(() -> {
                ensureViewportContract("skyDomeTex");
                ensureSkyDomeExists();

                Texture t = assets.loadTexture(a);
                skydomeMat.setTexture("SkyTex", t);

                log.info("RenderApi: skydome texture set asset='{}'", a);
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("skyDomeTexClear");
                ensureSkyDomeExists();
                skydomeMat.clearParam("SkyTex");
                log.info("RenderApi: skydome texture cleared");
            });
        });
    }


    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        // base
        int map = intClampR(cfg, "mapSize", 2048, 0, 16384);
        int splits = intClampR(cfg, "splits", DEFAULT_SHADOW_SPLITS, 1, 8);
        double lambda = num(cfg, "lambda", DEFAULT_SHADOW_LAMBDA);
        double intensity = num(cfg, "intensity", DEFAULT_SHADOW_INTENSITY);

        // new knobs (accepted by contract; applied if renderer supports)
        double softness = num(cfg, "softness", 0.0);
        int pcfSamples = intClampR(cfg, "pcfSamples", 16, 1, 64);
        boolean pcss = bool(cfg, "pcss", false);
        double lightRadius = num(cfg, "lightRadius", 0.0);

        // creates/updates renderer
        sunShadowsEx(map, splits, lambda, intensity);

        if (map <= 0) return;

        profiledVoid(() -> onJme(() -> {
            if (sunShadow == null) return;

            float s = clamp01((float) softness);
            float lr = clamp((float) lightRadius, 0f, 10f);

            boolean changed =
                    !approx(s, _shSoftness) ||
                            _shPcfSamples != pcfSamples ||
                            _shPcss != pcss ||
                            !approx(lr, _shLightRadius);

            if (!changed) return;

            _shSoftness = s;
            _shPcfSamples = pcfSamples;
            _shPcss = pcss;
            _shLightRadius = lr;

            // NOTE:
            // Stock JME DirectionalLightShadowRenderer does not provide PCSS/penumbra controls out of the box.
            // We ACCEPT and CACHE these values as part of the modern contract, so you can bind them into your
            // custom shadow material/shader without changing JS side later.
            //
            // If your JME build exposes something like setEdgesThickness, you can enable it here.
            //
            // Example (only if exists in your fork/version):
            // sunShadow.setEdgesThickness(s);

            if (pcss || lr > 0f) {
                log.debug("RenderApi: shadow pcss/lightRadius requested but not implemented in DLSR (pcss={}, lightRadius={})",
                        pcss, lr);
            }
        }));
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

    // ---------- post (exposure/tonemap) ----------

    @HostAccess.Export
    @Override
    public void postCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                ensureViewportContract("postCfg");
                ensureMainFpp("postCfg");

                boolean enabled = bool(cfg, "enabled", true);
                if (enabled != _postEnabled) {
                    _postEnabled = enabled;
                }

                // Gate (strict): when disabled, remove tonemap filter deterministically
                if (!_postEnabled) {
                    if (tonemap != null) {
                        fpp.removeFilter(tonemap);
                        tonemap = null;
                        log.info("RenderApi: Tonemap removed (post disabled)");
                    }
                    return;
                }

                // Create chain (you can gate FXAA/Bloom separately later if you want)
                ensureTonemapExists();

                float exposure = (float) Math.max(0.0, num(cfg, "exposure", 1.0));

                Value tm = member(cfg, "tonemap");
                float whitePoint = (float) Math.max(0.01, num(tm, "whitePoint", 11.2));
                float shoulder = clamp01((float) num(tm, "shoulder", 0.22));
                float toe = clamp01((float) num(tm, "toe", 0.08));

                float saturation = (float) Math.max(0.0, num(cfg, "saturation", 1.0));

                if (approx(exposure, _postExposure) &&
                        approx(whitePoint, _postWhitePoint) &&
                        approx(shoulder, _postShoulder) &&
                        approx(toe, _postToe) &&
                        approx(saturation, _postSaturation)) {
                    return;
                }

                _postExposure = exposure;
                _postWhitePoint = whitePoint;
                _postShoulder = shoulder;
                _postToe = toe;
                _postSaturation = saturation;

                // TonemapFilter contract (adjust if your filter uses different method names)
                tonemap.setExposure(exposure);
                tonemap.setWhitePoint(whitePoint);
                tonemap.setShoulder(shoulder);
                tonemap.setToe(toe);
                tonemap.setSaturation(saturation);
            });
        });
    }
}