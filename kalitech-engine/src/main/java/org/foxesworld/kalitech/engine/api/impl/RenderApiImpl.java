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
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
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
import org.foxesworld.kalitech.engine.modules.render.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.modules.render.ShadowModule;
import org.foxesworld.kalitech.engine.modules.render.ViewportContract;
import org.foxesworld.kalitech.engine.render.post.TonemapFilter;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;

public final class RenderApiImpl extends AbstractApiModule implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    private static final int   DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;

    private static final double FOG_DENSITY_MIN = 0.0;
    private static final double FOG_DENSITY_MAX = 0.03;
    private static final double FOG_DISTANCE_MIN = 25.0;

    private double fogBaseR = 0.70;
    private double fogBaseG = 0.78;
    private double fogBaseB = 0.90;

    private double fogDensity = 0.006;
    private double fogDistance = 250.0;

    private SimpleApplication app;
    private AssetManager assets;
    @SuppressWarnings("unused")
    private EcsWorld ecs;

    private volatile boolean sceneReady = false;

    // Modules
    private ViewportContract viewport;
    private LightRigModule lights;
    private ShadowModule shadows;

    // Post/Fog (оставил здесь, но можно так же вынести в PostModule по аналогии)
    private FilterPostProcessor fpp;
    private FogFilter fog;
    private FXAAFilter fxaa;
    private BloomFilter bloom;
    private TonemapFilter tonemap;

    // Caches (минимально)
    private float ambR = Float.NaN, ambG = Float.NaN, ambB = Float.NaN, ambI = Float.NaN;
    private float sunDx = Float.NaN, sunDy = Float.NaN, sunDz = Float.NaN;
    private float sunR = Float.NaN, sunG = Float.NaN, sunB = Float.NaN, sunI = Float.NaN;
    private float moonDx = Float.NaN, moonDy = Float.NaN, moonDz = Float.NaN;
    private float moonR = Float.NaN, moonG = Float.NaN, moonB = Float.NaN, moonI = Float.NaN;

    // Legacy skybox
    private Spatial skybox;
    private String skyboxAsset = "";

    // SkyDome (procedural)
    private Spatial skydome;
    private Material skydomeMat;
    private Boolean sdUseCube = null;

    // SkyDome cached params (минимум)
    private float sdSunDx = Float.NaN, sdSunDy = Float.NaN, sdSunDz = Float.NaN;
    private float sdMoonDx = Float.NaN, sdMoonDy = Float.NaN, sdMoonDz = Float.NaN;
    private float sdSunR = Float.NaN, sdSunG = Float.NaN, sdSunB = Float.NaN, sdSunI = Float.NaN;
    private float sdMoonR = Float.NaN, sdMoonG = Float.NaN, sdMoonB = Float.NaN, sdMoonI = Float.NaN;
    private float sdZenR = Float.NaN, sdZenG = Float.NaN, sdZenB = Float.NaN;
    private float sdHorR = Float.NaN, sdHorG = Float.NaN, sdHorB = Float.NaN;
    private float sdHaze = Float.NaN, sdSunDisk = Float.NaN, sdMoonDisk = Float.NaN, sdExposure = Float.NaN;

    // Post runtime cfg cache
    private boolean postEnabled = true;
    private float postExposure = Float.NaN;
    private float postWhitePoint = Float.NaN;
    private float postShoulder = Float.NaN;
    private float postToe = Float.NaN;
    private float postSaturation = Float.NaN;

    public RenderApiImpl() {
        super("render", "Render", "1.0.0");
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.ecs = ctx.ecs;

        this.viewport = new ViewportContract(app, log);
        this.lights = new LightRigModule(app.getRootNode(), log);
        this.shadows = new ShadowModule(app, assets, log, viewport, lights);
    }

    private void onJme(Runnable r) {
        if (engine.isJmeThread()) r.run();
        else app.enqueue(() -> {
            r.run();
            return null;
        });
    }

    private void ensureMainFpp(String where) {
        if (fpp != null) return;
        fpp = new FilterPostProcessor(assets);
        app.getViewPort().addProcessor(fpp);
        log.info("RenderApi: {} main FPP created", where);
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

    private void ensureSkyDomeExists() {
        if (skydome != null && skydomeMat != null) return;

        if (skybox != null) {
            skybox.removeFromParent();
            skybox = null;
            skyboxAsset = "";
            log.info("RenderApi: skybox removed (switch to skydome)");
        }

        Sphere sphere = new Sphere(48, 48, 1000f, false, true);
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
                viewport.ensure("ensureScene");
                lights.ensure();
                ensureMainFpp("ensureScene");
                log.info("RenderApi: scene ensured");
            });
        });
    }

    // --------------------- sky dome ---------------------

    @HostAccess.Export
    public void skyDomeClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("skyDomeClear");
                if (skydome != null) {
                    skydome.removeFromParent();
                    skydome = null;
                }
                skydomeMat = null;
                sdUseCube = null;
                log.info("RenderApi: skydome cleared");
            });
        });
    }

    @HostAccess.Export
    public void skyDomeCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("skyDomeCfg");
                ensureSkyDomeExists();

                Value sunDir = RenderCfg.member(cfg, "sunDir");
                Value moonDir = RenderCfg.member(cfg, "moonDir");
                Value sunCol = RenderCfg.member(cfg, "sunColor");
                Value moonCol = RenderCfg.member(cfg, "moonColor");
                Value zen = RenderCfg.member(cfg, "zenithColor");
                Value hor = RenderCfg.member(cfg, "horizonColor");

                float sdx = RenderCfg.vec3x(sunDir, -1f), sdy = RenderCfg.vec3y(sunDir, -1f), sdz = RenderCfg.vec3z(sunDir, -0.3f);
                float mdx = RenderCfg.vec3x(moonDir, 1f), mdy = RenderCfg.vec3y(moonDir, -1f), mdz = RenderCfg.vec3z(moonDir, 0.3f);

                float sr = RenderCfg.vec3x(sunCol, 1f), sg = RenderCfg.vec3y(sunCol, 0.98f), sb = RenderCfg.vec3z(sunCol, 0.90f);
                float mr = RenderCfg.vec3x(moonCol, 0.45f), mg = RenderCfg.vec3y(moonCol, 0.55f), mb = RenderCfg.vec3z(moonCol, 0.85f);

                float sunInt = (float) Math.max(0.0, RenderCfg.num(cfg, "sunIntensity", 1.0));
                float moonInt = (float) Math.max(0.0, RenderCfg.num(cfg, "moonIntensity", 0.0));

                float zr = RenderCfg.vec3x(zen, 0.10f), zg = RenderCfg.vec3y(zen, 0.17f), zb = RenderCfg.vec3z(zen, 0.32f);
                float hr = RenderCfg.vec3x(hor, 0.65f), hg = RenderCfg.vec3y(hor, 0.72f), hb = RenderCfg.vec3z(hor, 0.82f);

                float haze = RenderCfg.clamp01((float) RenderCfg.num(cfg, "haze", 0.55));
                float sunDisk = RenderCfg.clamp((float) RenderCfg.num(cfg, "sunDisk", 45.0), 0.5f, 500f);
                float moonDisk = RenderCfg.clamp((float) RenderCfg.num(cfg, "moonDisk", 120.0), 0.5f, 2000f);
                float exposure = RenderCfg.clamp((float) RenderCfg.num(cfg, "exposure", 1.0), 0.05f, 10f);
                float texBlend = RenderCfg.clamp01((float) RenderCfg.num(cfg, "texBlend", 0.0));
                float texExposure = RenderCfg.clamp((float) RenderCfg.num(cfg, "texExposure", 8.0), 0.001f, 100.0f);

                boolean hasTex =
                        skydomeMat.getParam("SkyTex") != null ||
                                skydomeMat.getParam("SkyCube") != null ||
                                skydomeMat.getParam("SkyTexA") != null ||
                                skydomeMat.getParam("SkyTexB") != null ||
                                skydomeMat.getParam("SkyCubeA") != null ||
                                skydomeMat.getParam("SkyCubeB") != null;

                if (!hasTex) texBlend = 0.0f;

                skydomeMat.setFloat("TexBlend", texBlend);
                skydomeMat.setFloat("TexExposure", texExposure);

                boolean changed =
                        !RenderCfg.approx3(sdx, sdy, sdz, sdSunDx, sdSunDy, sdSunDz) ||
                                !RenderCfg.approx3(mdx, mdy, mdz, sdMoonDx, sdMoonDy, sdMoonDz) ||
                                !RenderCfg.approx(sr, sdSunR) || !RenderCfg.approx(sg, sdSunG) || !RenderCfg.approx(sb, sdSunB) || !RenderCfg.approx(sunInt, sdSunI) ||
                                !RenderCfg.approx(mr, sdMoonR) || !RenderCfg.approx(mg, sdMoonG) || !RenderCfg.approx(mb, sdMoonB) || !RenderCfg.approx(moonInt, sdMoonI) ||
                                !RenderCfg.approx(zr, sdZenR) || !RenderCfg.approx(zg, sdZenG) || !RenderCfg.approx(zb, sdZenB) ||
                                !RenderCfg.approx(hr, sdHorR) || !RenderCfg.approx(hg, sdHorG) || !RenderCfg.approx(hb, sdHorB) ||
                                !RenderCfg.approx(haze, sdHaze) ||
                                !RenderCfg.approx(sunDisk, sdSunDisk) ||
                                !RenderCfg.approx(moonDisk, sdMoonDisk) ||
                                !RenderCfg.approx(exposure, sdExposure);

                if (!changed) return;

                sdSunDx = sdx;
                sdSunDy = sdy;
                sdSunDz = sdz;
                sdMoonDx = mdx;
                sdMoonDy = mdy;
                sdMoonDz = mdz;

                sdSunR = sr;
                sdSunG = sg;
                sdSunB = sb;
                sdSunI = sunInt;
                sdMoonR = mr;
                sdMoonG = mg;
                sdMoonB = mb;
                sdMoonI = moonInt;

                sdZenR = zr;
                sdZenG = zg;
                sdZenB = zb;
                sdHorR = hr;
                sdHorG = hg;
                sdHorB = hb;

                sdHaze = haze;
                sdSunDisk = sunDisk;
                sdMoonDisk = moonDisk;
                sdExposure = exposure;

                Vector3f sdir = new Vector3f(sdx, sdy, sdz);
                if (sdir.lengthSquared() < 1e-6f) sdir.set(-1, -1, -1);
                sdir.normalizeLocal();

                Vector3f mdir = new Vector3f(mdx, mdy, mdz);
                if (mdir.lengthSquared() < 1e-6f) mdir.set(1, -1, 0);
                mdir.normalizeLocal();

                skydomeMat.setVector3("SunDir", sdir);
                skydomeMat.setVector3("MoonDir", mdir);

                skydomeMat.setColor("SunColor", new ColorRGBA(sr, sg, sb, 1f));
                skydomeMat.setFloat("SunIntensity", sunInt);

                skydomeMat.setColor("MoonColor", new ColorRGBA(mr, mg, mb, 1f));
                skydomeMat.setFloat("MoonIntensity", moonInt);

                skydomeMat.setColor("ZenithColor", new ColorRGBA(zr, zg, zb, 1f));
                skydomeMat.setColor("HorizonColor", new ColorRGBA(hr, hg, hb, 1f));

                skydomeMat.setFloat("Haze", haze);
                skydomeMat.setFloat("SunDisk", sunDisk);
                skydomeMat.setFloat("MoonDisk", moonDisk);
                skydomeMat.setFloat("Exposure", exposure);
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexA(String asset) {
        profiledVoid(() -> {
            ensureScene();
            if (asset == null || asset.isBlank())
                throw new IllegalArgumentException("[render] skyDomeTexA: asset is blank");
            final String a = asset.trim();

            onJme(() -> {
                viewport.ensure("skyDomeTexA");
                ensureSkyDomeExists();

                Texture t = assets.loadTexture(a);
                final boolean useCube = (t instanceof com.jme3.texture.TextureCubeMap);

                if (sdUseCube == null) sdUseCube = useCube;
                if (sdUseCube.booleanValue() != useCube) {
                    throw new IllegalStateException("[render] SkyDome A/B type mismatch: A is " +
                            (useCube ? "CUBE" : "2D") + " but existing mode is " + (sdUseCube ? "CUBE" : "2D"));
                }

                if (useCube) {
                    skydomeMat.setTexture("SkyCubeA", t);
                    skydomeMat.setBoolean("UseCube", true);
                    skydomeMat.clearParam("SkyTexA");
                    return;
                }

                skydomeMat.setTexture("SkyTexA", t);
                skydomeMat.setBoolean("UseCube", false);
                skydomeMat.clearParam("SkyCubeA");
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexB(String asset) {
        profiledVoid(() -> {
            ensureScene();
            if (asset == null || asset.isBlank())
                throw new IllegalArgumentException("[render] skyDomeTexB: asset is blank");
            final String a = asset.trim();

            onJme(() -> {
                viewport.ensure("skyDomeTexB");
                ensureSkyDomeExists();

                Texture t = assets.loadTexture(a);
                final boolean useCube = (t instanceof com.jme3.texture.TextureCubeMap);

                if (sdUseCube == null) sdUseCube = useCube;
                if (sdUseCube.booleanValue() != useCube) {
                    throw new IllegalStateException("[render] SkyDome A/B type mismatch: B is " +
                            (useCube ? "CUBE" : "2D") + " but existing mode is " + (sdUseCube ? "CUBE" : "2D"));
                }

                if (useCube) {
                    skydomeMat.setTexture("SkyCubeB", t);
                    skydomeMat.setBoolean("UseCube", true);
                    skydomeMat.clearParam("SkyTexB");
                    return;
                }

                skydomeMat.setTexture("SkyTexB", t);
                skydomeMat.setBoolean("UseCube", false);
                skydomeMat.clearParam("SkyCubeB");
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("skyDomeTexClear");
                ensureSkyDomeExists();

                skydomeMat.clearParam("SkyTex");
                skydomeMat.clearParam("SkyCube");
                skydomeMat.clearParam("SkyTexA");
                skydomeMat.clearParam("SkyTexB");
                skydomeMat.clearParam("SkyCubeA");
                skydomeMat.clearParam("SkyCubeB");
                sdUseCube = null;

                log.info("RenderApi: skydome texture cleared");
            });
        });
    }

    // --------------------- skybox (legacy) ---------------------

    @HostAccess.Export
    public void skyboxClear() {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("skyboxClear");
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
                viewport.ensure("skyboxCube");

                if (skydome != null) {
                    skydome.removeFromParent();
                    skydome = null;
                    skydomeMat = null;
                    sdUseCube = null;
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

    // --------------------- ambient ---------------------

    @HostAccess.Export
    @Override
    public void ambientCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("ambientCfg");
                lights.ensureAmbient();

                double r = RenderCfg.num(cfg, "r", RenderCfg.numPath(cfg, "color", "r", 0.25));
                double g = RenderCfg.num(cfg, "g", RenderCfg.numPath(cfg, "color", "g", 0.28));
                double b = RenderCfg.num(cfg, "b", RenderCfg.numPath(cfg, "color", "b", 0.35));
                double intensity = RenderCfg.num(cfg, "intensity", 1.0);

                float fr = (float) r, fg = (float) g, fb = (float) b;
                float fi = (float) Math.max(0.0, intensity);

                if (RenderCfg.approx(fr, ambR) && RenderCfg.approx(fg, ambG) && RenderCfg.approx(fb, ambB) && RenderCfg.approx(fi, ambI))
                    return;
                ambR = fr;
                ambG = fg;
                ambB = fb;
                ambI = fi;

                AmbientLight a = lights.ambient();
                a.setColor(new ColorRGBA(fr, fg, fb, 1f).mult(fi));
            });
        });
    }

    // --------------------- primary directional ---------------------

    @HostAccess.Export
    public void setPrimaryDirectional(String which) {
        final String w = (which == null ? "" : which.trim().toLowerCase());
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }

        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("setPrimaryDirectional");
                lights.ensure();
                if (w.equals(lights.primaryDirectional())) return;

                lights.setPrimaryDirectional(w);
                shadows.refreshPrimaryLightBinding();

                log.info("RenderApi: primaryDirectional={}", w);
            });
        });
    }

    // --------------------- sun / moon ---------------------

    @HostAccess.Export
    @Override
    public void sunCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("sunCfg");
                lights.ensureSun();

                Value dir = RenderCfg.member(cfg, "dir");
                Value col = RenderCfg.member(cfg, "color");

                float dx = RenderCfg.vec3x(dir, -1f);
                float dy = RenderCfg.vec3y(dir, -1f);
                float dz = RenderCfg.vec3z(dir, -0.3f);

                float r = RenderCfg.vec3x(col, 1f);
                float g = RenderCfg.vec3y(col, 0.98f);
                float b = RenderCfg.vec3z(col, 0.9f);

                float intensity = (float) Math.max(0.0, RenderCfg.num(cfg, "intensity", 1.2));

                if (RenderCfg.approx(dx, sunDx) && RenderCfg.approx(dy, sunDy) && RenderCfg.approx(dz, sunDz) &&
                        RenderCfg.approx(r, sunR) && RenderCfg.approx(g, sunG) && RenderCfg.approx(b, sunB) && RenderCfg.approx(intensity, sunI)) {
                    return;
                }

                sunDx = dx;
                sunDy = dy;
                sunDz = dz;
                sunR = r;
                sunG = g;
                sunB = b;
                sunI = intensity;

                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1e-6f) v.set(-1, -1, -1);
                v.normalizeLocal();

                DirectionalLight sun = lights.sun();
                sun.setDirection(v);
                sun.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));

                if ("sun".equals(lights.primaryDirectional())) shadows.refreshPrimaryLightBinding();
            });
        });
    }

    @HostAccess.Export
    public void moonCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("moonCfg");
                lights.ensureMoon();

                Value dir = RenderCfg.member(cfg, "dir");
                Value col = RenderCfg.member(cfg, "color");

                float dx = RenderCfg.vec3x(dir, 1f);
                float dy = RenderCfg.vec3y(dir, -1f);
                float dz = RenderCfg.vec3z(dir, 0.3f);

                float r = RenderCfg.vec3x(col, 0.45f);
                float g = RenderCfg.vec3y(col, 0.55f);
                float b = RenderCfg.vec3z(col, 0.85f);

                float intensity = (float) Math.max(0.0, RenderCfg.num(cfg, "intensity", 0.0));

                if (RenderCfg.approx(dx, moonDx) && RenderCfg.approx(dy, moonDy) && RenderCfg.approx(dz, moonDz) &&
                        RenderCfg.approx(r, moonR) && RenderCfg.approx(g, moonG) && RenderCfg.approx(b, moonB) && RenderCfg.approx(intensity, moonI)) {
                    return;
                }

                moonDx = dx;
                moonDy = dy;
                moonDz = dz;
                moonR = r;
                moonG = g;
                moonB = b;
                moonI = intensity;

                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1e-6f) v.set(1, -1, 0);
                v.normalizeLocal();

                DirectionalLight moon = lights.moon();
                moon.setDirection(v);
                moon.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));

                if ("moon".equals(lights.primaryDirectional())) shadows.refreshPrimaryLightBinding();
            });
        });
    }

    // --------------------- shadows ---------------------

    @HostAccess.Export
    @Override
    public void sunShadows(int mapSize) {
        sunShadowsEx(mapSize, DEFAULT_SHADOW_SPLITS, DEFAULT_SHADOW_LAMBDA, DEFAULT_SHADOW_INTENSITY);
    }

    @HostAccess.Export
    public void sunShadowsEx(int mapSize, int splits, double lambda, double intensity) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("sunShadowsEx");
                lights.ensure();
                shadows.enable(mapSize, splits, lambda, intensity);
            });
        });
    }

    @HostAccess.Export
    @Override
    public void sunShadowsCfg(Value cfg) {
        int map = intClampR(cfg, "mapSize", 2048, 0, 16384);
        int splits = intClampR(cfg, "splits", DEFAULT_SHADOW_SPLITS, 1, 8);
        double lambda = RenderCfg.num(cfg, "lambda", DEFAULT_SHADOW_LAMBDA);
        double intensity = RenderCfg.num(cfg, "intensity", DEFAULT_SHADOW_INTENSITY);

        boolean snap = RenderCfg.bool(cfg, "snap", true);

        // cache-only knobs for future (оставляем контракт)
        RenderCfg.num(cfg, "softness", 0.0);
        intClampR(cfg, "pcfSamples", 16, 1, 64);
        RenderCfg.bool(cfg, "pcss", false);
        RenderCfg.num(cfg, "lightRadius", 0.0);

        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("sunShadowsCfg");
                lights.ensure();

                shadows.setSnapEnabled(snap);
                shadows.enable(map, splits, lambda, intensity);

                DirectionalLightShadowRenderer r = shadows.renderer();
                if (r != null) r.setLight(lights.primaryLight());
            });
        });
    }

    // --------------------- fog ---------------------

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("fogCfg");
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
            });
        });
    }

    // --------------------- post ---------------------

    @HostAccess.Export
    @Override
    public void postCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJme(() -> {
                viewport.ensure("postCfg");
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
            });
        });
    }
}