// FILE: org/foxesworld/kalitech/engine/api/impl/RenderApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.ViewportContract;
import org.foxesworld.kalitech.engine.modules.render.light.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.post.PostModule;
import org.foxesworld.kalitech.engine.modules.render.shadow.Shadow;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyModule;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;

public final class RenderApiImpl extends AbstractApiModule implements RenderApi {

    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);

    private static final int DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;

    private SimpleApplication app;
    private AssetManager assets;
    @SuppressWarnings("unused")
    private EcsWorld ecs;

    private volatile boolean sceneReady = false;

    // Core modules
    private ViewportContract viewport;
    private LightRigModule lights;
    private Shadow shadows;

    // Moved responsibilities
    private SkyModule sky;
    private PostModule post;

    // Light caches (API-layer: "do-not-spam apply" policy)
    private float ambR = Float.NaN, ambG = Float.NaN, ambB = Float.NaN, ambI = Float.NaN;
    private float sunDx = Float.NaN, sunDy = Float.NaN, sunDz = Float.NaN;
    private float sunR = Float.NaN, sunG = Float.NaN, sunB = Float.NaN, sunI = Float.NaN;
    private float moonDx = Float.NaN, moonDy = Float.NaN, moonDz = Float.NaN;
    private float moonR = Float.NaN, moonG = Float.NaN, moonB = Float.NaN, moonI = Float.NaN;

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
        this.lights = new LightRigModule(new RenderThread(ctx.engine, ctx.app), app);
        this.shadows = new Shadow(new RenderThread(ctx.engine, app), app, assets, log, lights);

        this.sky = new SkyModule(app, assets, log);
        this.post = new PostModule(app, assets, log);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void ensureScene() {
        profiledVoid(() -> {
            if (sceneReady) return;
            sceneReady = true;

            onJmeSyncVoid("render.ensureScene", () -> {
                viewport.ensure("ensureScene");
                lights.ensure();
                post.ensureMainFpp("ensureScene");
                log.info("RenderApi: scene ensured");
            });
        });
    }

    // --------------------- sky dome ---------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
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
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
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
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
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
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
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
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void skyDomeTexClear() {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.skyDomeTexClear", () -> {
                viewport.ensure("skyDomeTexClear");
                sky.skyDomeTexClear();
            });
        });
    }

    public void __resetWorldCache(String reason) {
        ambR = ambG = ambB = ambI = Float.NaN;
        sunDx = sunDy = sunDz = Float.NaN;
        sunR = sunG = sunB = sunI = Float.NaN;
        moonDx = moonDy = moonDz = Float.NaN;
        moonR = moonG = moonB = moonI = Float.NaN;

        final String why = (reason == null || reason.isBlank()) ? "worldReset" : reason.trim();
        onJmeSyncVoid("render.__resetWorldCache", () -> {
            PostModule p = post;
            //if (p != null) p.resetCache();

            Shadow s = shadows;
            s.fullReloadNow();
        });
    }

    // --------------------- ambient ---------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void ambientCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.ambientCfg", () -> {
                viewport.ensure("ambientCfg");
                lights.ensure();//ensureAmbient();

                double r = num(cfg, "r", RenderCfg.numPath(cfg, "color", "r", 0.25));
                double g = num(cfg, "g", RenderCfg.numPath(cfg, "color", "g", 0.28));
                double b = num(cfg, "b", RenderCfg.numPath(cfg, "color", "b", 0.35));
                double intensity = num(cfg, "intensity", 1.0);

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

    // --------------------- sun / moon ---------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void sunCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.sunCfg", () -> {
                viewport.ensure("sunCfg");
                lights.ensure();

                Value dir = member(cfg, "dir");
                Value col = member(cfg, "color");

                float dx = RenderCfg.vec3x(dir, -1f);
                float dy = RenderCfg.vec3y(dir, -1f);
                float dz = RenderCfg.vec3z(dir, -0.3f);

                float r = RenderCfg.vec3x(col, 1f);
                float g = RenderCfg.vec3y(col, 0.98f);
                float b = RenderCfg.vec3z(col, 0.9f);

                float intensity = (float) Math.max(0.0, num(cfg, "intensity", 1.2));

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
            });
        });
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void moonCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.moonCfg", () -> {
                viewport.ensure("moonCfg");
                lights.ensure();

                Value dir = member(cfg, "dir");
                Value col = member(cfg, "color");

                float dx = RenderCfg.vec3x(dir, 1f);
                float dy = RenderCfg.vec3y(dir, -1f);
                float dz = RenderCfg.vec3z(dir, 0.3f);

                float r = RenderCfg.vec3x(col, 0.45f);
                float g = RenderCfg.vec3y(col, 0.55f);
                float b = RenderCfg.vec3z(col, 0.85f);

                float intensity = (float) Math.max(0.0, num(cfg, "intensity", 0.0));

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
            });
        });
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
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
                shadows.onPrimaryLightChanged();

                log.info("RenderApi: primaryDirectional={}", w);
            });
        });
    }

    // --------------------- shadows ---------------------


// FILE: org/foxesworld/kalitech/engine/api/impl/RenderApiImpl.java
// PATCH: pass full JS config object to Shadow module

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void sunShadowsCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.sunShadowsCfg", () -> {
                viewport.ensure("sunShadowsCfg");
                lights.ensure();

                // Full config pass-through (Shadow owns defaults + compatibility)
                shadows.applyCfg(cfg);

                DirectionalLightShadowRenderer r = shadows.renderer();
                if (r != null) r.setLight(lights.primaryLight());
            });
        });
    }


    // --------------------- fog ---------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void fogCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.fogCfg", () -> {
                viewport.ensure("fogCfg");
                post.fogCfg(cfg);
            });
        });
    }

    // --------------------- post ---------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void postCfg(Value cfg) {
        profiledVoid(() -> {
            ensureScene();
            onJmeSyncVoid("render.postCfg", () -> {
                viewport.ensure("postCfg");
                post.postCfg(cfg);
            });
        });
    }
}