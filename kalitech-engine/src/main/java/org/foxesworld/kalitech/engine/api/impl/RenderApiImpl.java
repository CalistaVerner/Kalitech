package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.modules.render.*;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowSystemConfig;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.intClampR;

/**
 * Render API (script-facing).
 *
 * <p>Threading:
 * all JME scene/view modifications are executed on the JME thread via {@code onJme*()} helpers.
 */
public final class RenderApiImpl extends AbstractApiModule implements RenderApi {

    private static final int DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;

    private SimpleApplication app;
    private AssetManager assets;
    @SuppressWarnings("unused")
    private EcsWorld ecs;

    private volatile boolean sceneReady = false;

    private ViewportContract viewport;
    private LightRigModule lights;
    private ShadowModule shadows;

    private SkyModule sky;
    private PostModule post;

    private volatile float ambR = Float.NaN, ambG = Float.NaN, ambB = Float.NaN, ambI = Float.NaN;
    private volatile float sunDx = Float.NaN, sunDy = Float.NaN, sunDz = Float.NaN;
    private volatile float sunR = Float.NaN, sunG = Float.NaN, sunB = Float.NaN, sunI = Float.NaN;
    private volatile float moonDx = Float.NaN, moonDy = Float.NaN, moonDz = Float.NaN;
    private volatile float moonR = Float.NaN, moonG = Float.NaN, moonB = Float.NaN, moonI = Float.NaN;

    public RenderApiImpl() {
        super("render", "Render", "1.0.0");
    }

    private static DirLightConfig parseSun(Value cfg) {
        Value dir = RenderCfg.member(cfg, "dir");
        Value col = RenderCfg.member(cfg, "color");

        float dx = RenderCfg.vec3x(dir, -1f);
        float dy = RenderCfg.vec3y(dir, -1f);
        float dz = RenderCfg.vec3z(dir, -0.3f);

        float r = RenderCfg.vec3x(col, 1f);
        float g = RenderCfg.vec3y(col, 0.98f);
        float b = RenderCfg.vec3z(col, 0.9f);

        float i = (float) Math.max(0.0, RenderCfg.num(cfg, "intensity", 1.2));
        return new DirLightConfig(dx, dy, dz, r, g, b, i);
    }

    private static DirLightConfig parseMoon(Value cfg) {
        Value dir = RenderCfg.member(cfg, "dir");
        Value col = RenderCfg.member(cfg, "color");

        float dx = RenderCfg.vec3x(dir, 1f);
        float dy = RenderCfg.vec3y(dir, -1f);
        float dz = RenderCfg.vec3z(dir, 0.3f);

        float r = RenderCfg.vec3x(col, 0.45f);
        float g = RenderCfg.vec3y(col, 0.55f);
        float b = RenderCfg.vec3z(col, 0.85f);

        float i = (float) Math.max(0.0, RenderCfg.num(cfg, "intensity", 0.0));
        return new DirLightConfig(dx, dy, dz, r, g, b, i);
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);

        this.app = ctx.app;
        this.assets = ctx.assets;
        this.ecs = ctx.ecs;

        RenderThread rt = new RenderThread(ctx.engine, ctx.app);

        this.viewport = new ViewportContract(app, log);
        this.lights = new LightRigModule(rt, app);
        this.shadows = new ShadowModule(rt, app, assets, log, lights);

        this.sky = new SkyModule(app, assets, log);
        this.post = new PostModule(app, assets, log);
    }

    @Override
    public void detach() {
        sceneReady = false;
        viewport = null;
        lights = null;
        shadows = null;
        sky = null;
        post = null;

        app = null;
        assets = null;
        ecs = null;

        super.detach();
    }

    // ---------------------------------------------------------------------
    // Sky dome
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void skyDomeClear() {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeClear", () -> {
                viewport.ensure("skyDomeClear");
                sky.skyDomeClear();
            });
        });
    }

    @HostAccess.Export
    public void skyDomeCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeCfg", () -> {
                viewport.ensure("skyDomeCfg");
                sky.skyDomeCfg(cfg);
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexA(String asset) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeTexA", () -> {
                viewport.ensure("skyDomeTexA");
                sky.skyDomeTexA(asset);
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexB(String asset) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeTexB", () -> {
                viewport.ensure("skyDomeTexB");
                sky.skyDomeTexB(asset);
            });
        });
    }

    @HostAccess.Export
    public void skyDomeTexClear() {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeTexClear", () -> {
                viewport.ensure("skyDomeTexClear");
                sky.skyDomeTexClear();
            });
        });
    }

    // ---------------------------------------------------------------------
    // Ambient
    // ---------------------------------------------------------------------

    public void __resetWorldCache(String reason) {
        ambR = ambG = ambB = ambI = Float.NaN;
        sunDx = sunDy = sunDz = Float.NaN;
        sunR = sunG = sunB = sunI = Float.NaN;
        moonDx = moonDy = moonDz = Float.NaN;
        moonR = moonG = moonB = moonI = Float.NaN;

        final String why = (reason == null || reason.isBlank()) ? "worldReset" : reason.trim();
        onJmeSyncVoid("render.__resetWorldCache", () -> {
            PostModule p = post;
            if (p != null) p.resetCache();

            ShadowModule s = shadows;
            if (s != null) s.clearShadowMaps(why);
        });
    }

    @HostAccess.Export
    @Override
    public void ensureScene() {
        profiledVoid(() -> {
            if (sceneReady) return;
            sceneReady = true;

            onJmeSyncVoid("render.ensureScene", () -> {
                ViewportContract vp = viewport;
                if (vp != null) vp.ensure("ensureScene");

                LightRigModule lr = lights;
                if (lr != null) lr.ensure();

                PostModule p = post;
                if (p != null) p.ensureMainFpp("ensureScene");

                if (log != null) log.info("[render] scene ensured");
            });
        });
    }

    // ---------------------------------------------------------------------
    // Sun / Moon
    // ---------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void ambientCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();

            AmbientConfig c = AmbientConfig.parse(cfg);

            if (RenderCfg.approx(c.r, ambR) && RenderCfg.approx(c.g, ambG) && RenderCfg.approx(c.b, ambB) && RenderCfg.approx(c.intensity, ambI)) {
                return;
            }

            ambR = c.r;
            ambG = c.g;
            ambB = c.b;
            ambI = c.intensity;

            onJmeSyncVoid("render.ambientCfg", () -> {
                viewport.ensure("ambientCfg");
                lights.ensure();

                AmbientLight a = lights.ambient();
                a.setColor(new ColorRGBA(c.r, c.g, c.b, 1f).mult(c.intensity));
            });
        });
    }

    @HostAccess.Export
    @Override
    public void sunCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();

            DirLightConfig c = parseSun(cfg);

            if (RenderCfg.approx(c.dx, sunDx) && RenderCfg.approx(c.dy, sunDy) && RenderCfg.approx(c.dz, sunDz)
                    && RenderCfg.approx(c.r, sunR) && RenderCfg.approx(c.g, sunG) && RenderCfg.approx(c.b, sunB)
                    && RenderCfg.approx(c.intensity, sunI)) {
                return;
            }

            sunDx = c.dx;
            sunDy = c.dy;
            sunDz = c.dz;
            sunR = c.r;
            sunG = c.g;
            sunB = c.b;
            sunI = c.intensity;

            onJmeSyncVoid("render.sunCfg", () -> {
                viewport.ensure("sunCfg");
                lights.ensure();

                Vector3f v = new Vector3f(c.dx, c.dy, c.dz);
                if (v.lengthSquared() < 1e-6f) v.set(-1f, -1f, -1f);
                v.normalizeLocal();

                DirectionalLight sun = lights.sun();
                sun.setDirection(v);
                sun.setColor(new ColorRGBA(c.r, c.g, c.b, 1f).mult(c.intensity));
            });
        });
    }

    @HostAccess.Export
    public void moonCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();

            DirLightConfig c = parseMoon(cfg);

            if (RenderCfg.approx(c.dx, moonDx) && RenderCfg.approx(c.dy, moonDy) && RenderCfg.approx(c.dz, moonDz)
                    && RenderCfg.approx(c.r, moonR) && RenderCfg.approx(c.g, moonG) && RenderCfg.approx(c.b, moonB)
                    && RenderCfg.approx(c.intensity, moonI)) {
                return;
            }

            moonDx = c.dx;
            moonDy = c.dy;
            moonDz = c.dz;
            moonR = c.r;
            moonG = c.g;
            moonB = c.b;
            moonI = c.intensity;

            onJmeSyncVoid("render.moonCfg", () -> {
                viewport.ensure("moonCfg");
                lights.ensure();

                Vector3f v = new Vector3f(c.dx, c.dy, c.dz);
                if (v.lengthSquared() < 1e-6f) v.set(1f, -1f, 0f);
                v.normalizeLocal();

                DirectionalLight moon = lights.moon();
                moon.setDirection(v);
                moon.setColor(new ColorRGBA(c.r, c.g, c.b, 1f).mult(c.intensity));
            });
        });
    }

    @HostAccess.Export
    public void setPrimaryDirectional(String which) {
        final String w = (which == null ? "" : which.trim().toLowerCase());
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }

        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.setPrimaryDirectional", () -> {
                viewport.ensure("setPrimaryDirectional");
                lights.ensure();

                if (w.equals(lights.primaryDirectional())) return;

                lights.setPrimaryDirectional(w);
                shadows.rebuild();//renderer();

                if (log != null) log.info("[render] primaryDirectional={}", w);
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

        RenderCfg.num(cfg, "softness", 0.0);
        intClampR(cfg, "pcfSamples", 16, 1, 64);
        RenderCfg.bool(cfg, "pcss", false);
        RenderCfg.num(cfg, "lightRadius", 0.0);

        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.sunShadowsCfg", () -> {
                viewport.ensure("sunShadowsCfg");
                lights.ensure();

                shadows.setEnabled(snap);
                ShadowSystemConfig shadowSystemConfig = new ShadowSystemConfig();

                // базовые параметры
                shadowSystemConfig.mapSize = map;
                shadowSystemConfig.splits = splits;
                shadowSystemConfig.intensity = (float) intensity;
                shadowSystemConfig.rendererType = ShadowSystemConfig.RendererType.PCSS;

                // splits (lambda)
                shadowSystemConfig.splitCfg.lambda = (float) lambda;

                // стабилизация каскадов (убирает shimmer)
                shadowSystemConfig.fitterCfg.extentsPadding = 1.10f;
                shadowSystemConfig.fitterCfg.zPadding = 25f;
                shadowSystemConfig.fitterCfg.quantTexels = 2.0f;

                // snap на тексель (убирает дрожание при движении камеры)
                shadowSystemConfig.enableSnap = true;
                shadowSystemConfig.snapCfg.enablePositionSnap = true;
                shadowSystemConfig.snapCfg.positionThreshold = 0.5f;
                shadowSystemConfig.snapCfg.maxSnapDistanceTexels = 2.0f;

                // hysteresis границ каскадов (убирает дергание стыков)
                shadowSystemConfig.enableSplitHysteresis = true;
                shadowSystemConfig.hysteresisCfg.minHalfLifeSeconds = 0.10f;
                shadowSystemConfig.hysteresisCfg.maxHalfLifeSeconds = 0.60f;

                // применить (это перестроит pipeline внутри)
                shadows.apply(shadowSystemConfig);


                //DirectionalLightShadowRenderer r = shadows.renderer();
                //if (r != null) r.setLight(lights.primaryLight());
            });
        });
    }

    @HostAccess.Export
    @Override
    public void sunShadows(int mapSize) {
        sunShadowsEx(mapSize, DEFAULT_SHADOW_SPLITS, DEFAULT_SHADOW_LAMBDA, DEFAULT_SHADOW_INTENSITY);
    }

    // ---------------------------------------------------------------------
    // Shadows
    // ---------------------------------------------------------------------

    @HostAccess.Export
    public void sunShadowsEx(int mapSize, int splits, double lambda, double intensity) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.sunShadowsEx", () -> {
                viewport.ensure("sunShadowsEx");
                lights.ensure();
                ShadowSystemConfig shadowSystemConfig = new ShadowSystemConfig();
                shadowSystemConfig.mapSize = mapSize;
                shadowSystemConfig.splits = splits;
                shadowSystemConfig.intensity = (float) intensity;
                shadowSystemConfig.rendererType = ShadowSystemConfig.RendererType.PCSS;
                shadowSystemConfig.splitCfg.lambda = (float) lambda;
                shadowSystemConfig.fitterCfg.extentsPadding = 1.10f;
                shadowSystemConfig.fitterCfg.zPadding = 25f;
                shadowSystemConfig.fitterCfg.quantTexels = 2.0f;
                shadowSystemConfig.enableSnap = true;
                shadowSystemConfig.snapCfg.enablePositionSnap = true;
                shadowSystemConfig.snapCfg.positionThreshold = 0.5f;
                shadowSystemConfig.snapCfg.maxSnapDistanceTexels = 2.0f;
                shadowSystemConfig.enableSplitHysteresis = true;
                shadowSystemConfig.hysteresisCfg.minHalfLifeSeconds = 0.10f;
                shadowSystemConfig.hysteresisCfg.maxHalfLifeSeconds = 0.60f;
                shadows.apply(shadowSystemConfig);
            });
        });
    }

    @HostAccess.Export
    @Override
    public void fogCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.fogCfg", () -> {
                viewport.ensure("fogCfg");
                post.fogCfg(cfg);
            });
        });
    }

    @HostAccess.Export
    @Override
    public void postCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.postCfg", () -> {
                viewport.ensure("postCfg");
                post.postCfg(cfg);
            });
        });
    }

    // ---------------------------------------------------------------------
    // Fog
    // ---------------------------------------------------------------------

    private static final class AmbientConfig {
        final float r, g, b, intensity;

        AmbientConfig(float r, float g, float b, float intensity) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.intensity = intensity;
        }

        static AmbientConfig parse(Value cfg) {
            double r = RenderCfg.num(cfg, "r", RenderCfg.numPath(cfg, "color", "r", 0.25));
            double g = RenderCfg.num(cfg, "g", RenderCfg.numPath(cfg, "color", "g", 0.28));
            double b = RenderCfg.num(cfg, "b", RenderCfg.numPath(cfg, "color", "b", 0.35));
            double i = RenderCfg.num(cfg, "intensity", 1.0);

            float fr = (float) r;
            float fg = (float) g;
            float fb = (float) b;
            float fi = (float) Math.max(0.0, i);

            return new AmbientConfig(fr, fg, fb, fi);
        }
    }

    // ---------------------------------------------------------------------
    // Post
    // ---------------------------------------------------------------------

    private static final class DirLightConfig {
        final float dx, dy, dz;
        final float r, g, b;
        final float intensity;

        DirLightConfig(float dx, float dy, float dz, float r, float g, float b, float intensity) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.r = r;
            this.g = g;
            this.b = b;
            this.intensity = intensity;
        }
    }
}