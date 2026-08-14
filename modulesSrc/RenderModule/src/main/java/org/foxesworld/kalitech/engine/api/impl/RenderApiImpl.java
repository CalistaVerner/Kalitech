/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetManager
 *  com.jme3.light.AmbientLight
 *  com.jme3.light.DirectionalLight
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 *  com.jme3.shadow.DirectionalLightShadowRenderer
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApi
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.ecs.EcsWorld
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
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
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.RenderApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.ViewportContract;
import org.foxesworld.kalitech.engine.modules.render.light.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.post.PostModule;
import org.foxesworld.kalitech.engine.modules.render.shadow.Shadow;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyModule;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class RenderApiImpl
extends AbstractApiModule
implements RenderApi {
    private static final Logger log = LogManager.getLogger(RenderApiImpl.class);
    private static final int DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;
    private SimpleApplication app;
    private AssetManager assets;
    private EcsWorld ecs;
    private volatile boolean sceneReady = false;
    private ViewportContract viewport;
    private LightRigModule lights;
    private Shadow shadows;
    private SkyModule sky;
    private PostModule post;
    private float ambR = Float.NaN;
    private float ambG = Float.NaN;
    private float ambB = Float.NaN;
    private float ambI = Float.NaN;
    private float sunDx = Float.NaN;
    private float sunDy = Float.NaN;
    private float sunDz = Float.NaN;
    private float sunR = Float.NaN;
    private float sunG = Float.NaN;
    private float sunB = Float.NaN;
    private float sunI = Float.NaN;
    private float moonDx = Float.NaN;
    private float moonDy = Float.NaN;
    private float moonDz = Float.NaN;
    private float moonR = Float.NaN;
    private float moonG = Float.NaN;
    private float moonB = Float.NaN;
    private float moonI = Float.NaN;

    public RenderApiImpl() {
        super("render", "Render", "1.0.0");
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.ecs = ctx.ecs;
        this.viewport = new ViewportContract(this.app, log);
        this.lights = new LightRigModule(new RenderThread((EngineApi)ctx.engine, ctx.app), this.app);
        this.shadows = new Shadow(new RenderThread((EngineApi)ctx.engine, this.app), this.app, this.assets, log, this.lights);
        this.sky = new SkyModule(this.app, this.assets, log);
        this.post = new PostModule(this.app, this.assets, log);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void ensureScene() {
        this.profiledVoid(() -> {
            if (this.sceneReady) {
                return;
            }
            this.sceneReady = true;
            this.onJmeSyncVoid("render.ensureScene", () -> {
                this.viewport.ensure("ensureScene");
                this.lights.ensure();
                this.post.ensureMainFpp("ensureScene");
                log.info("RenderApi: scene ensured");
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void skyDomeClear() {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.skyDomeClear", () -> {
                this.viewport.ensure("skyDomeClear");
                this.sky.skyDomeClear();
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void skyDomeCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.skyDomeCfg", () -> {
                this.viewport.ensure("skyDomeCfg");
                this.sky.skyDomeCfg(cfg);
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void skyDomeTexA(String asset) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.skyDomeTexA", () -> {
                this.viewport.ensure("skyDomeTexA");
                this.sky.skyDomeTexA(asset);
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void skyDomeTexB(String asset) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.skyDomeTexB", () -> {
                this.viewport.ensure("skyDomeTexB");
                this.sky.skyDomeTexB(asset);
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void skyDomeTexClear() {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.skyDomeTexClear", () -> {
                this.viewport.ensure("skyDomeTexClear");
                this.sky.skyDomeTexClear();
            });
        });
    }

    @Override
    public void __resetWorldCache(String reason) {
        this.ambI = Float.NaN;
        this.ambB = Float.NaN;
        this.ambG = Float.NaN;
        this.ambR = Float.NaN;
        this.sunDz = Float.NaN;
        this.sunDy = Float.NaN;
        this.sunDx = Float.NaN;
        this.sunI = Float.NaN;
        this.sunB = Float.NaN;
        this.sunG = Float.NaN;
        this.sunR = Float.NaN;
        this.moonDz = Float.NaN;
        this.moonDy = Float.NaN;
        this.moonDx = Float.NaN;
        this.moonI = Float.NaN;
        this.moonB = Float.NaN;
        this.moonG = Float.NaN;
        this.moonR = Float.NaN;
        String why = reason == null || reason.isBlank() ? "worldReset" : reason.trim();
        this.onJmeSyncVoid("render.__resetWorldCache", () -> {
            PostModule p = this.post;
            Shadow s = this.shadows;
            s.fullReloadNow();
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void ambientCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.ambientCfg", () -> {
                this.viewport.ensure("ambientCfg");
                this.lights.ensure();
                double r = LuaCfg.num((LuaValueRef)cfg, (String)"r", (double)RenderCfg.numPath(cfg, "color", "r", 0.25));
                double g = LuaCfg.num((LuaValueRef)cfg, (String)"g", (double)RenderCfg.numPath(cfg, "color", "g", 0.28));
                double b = LuaCfg.num((LuaValueRef)cfg, (String)"b", (double)RenderCfg.numPath(cfg, "color", "b", 0.35));
                double intensity = LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)1.0);
                float fr = (float)r;
                float fg = (float)g;
                float fb = (float)b;
                float fi = (float)Math.max(0.0, intensity);
                if (RenderCfg.approx(fr, this.ambR) && RenderCfg.approx(fg, this.ambG) && RenderCfg.approx(fb, this.ambB) && RenderCfg.approx(fi, this.ambI)) {
                    return;
                }
                this.ambR = fr;
                this.ambG = fg;
                this.ambB = fb;
                this.ambI = fi;
                AmbientLight a = this.lights.ambient();
                a.setColor(new ColorRGBA(fr, fg, fb, 1.0f).mult(fi));
            });
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void sunCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.sunCfg", () -> {
                this.viewport.ensure("sunCfg");
                this.lights.ensure();
                LuaValueRef dir = LuaCfg.member((LuaValueRef)cfg, (String)"dir");
                LuaValueRef col = LuaCfg.member((LuaValueRef)cfg, (String)"color");
                float dx = RenderCfg.vec3x(dir, -1.0f);
                float dy = RenderCfg.vec3y(dir, -1.0f);
                float dz = RenderCfg.vec3z(dir, -0.3f);
                float r = RenderCfg.vec3x(col, 1.0f);
                float g = RenderCfg.vec3y(col, 0.98f);
                float b = RenderCfg.vec3z(col, 0.9f);
                float intensity = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)1.2));
                if (RenderCfg.approx(dx, this.sunDx) && RenderCfg.approx(dy, this.sunDy) && RenderCfg.approx(dz, this.sunDz) && RenderCfg.approx(r, this.sunR) && RenderCfg.approx(g, this.sunG) && RenderCfg.approx(b, this.sunB) && RenderCfg.approx(intensity, this.sunI)) {
                    return;
                }
                this.sunDx = dx;
                this.sunDy = dy;
                this.sunDz = dz;
                this.sunR = r;
                this.sunG = g;
                this.sunB = b;
                this.sunI = intensity;
                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1.0E-6f) {
                    v.set(-1.0f, -1.0f, -1.0f);
                }
                v.normalizeLocal();
                DirectionalLight sun = this.lights.sun();
                sun.setDirection(v);
                sun.setColor(new ColorRGBA(r, g, b, 1.0f).mult(intensity));
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void moonCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.moonCfg", () -> {
                this.viewport.ensure("moonCfg");
                this.lights.ensure();
                LuaValueRef dir = LuaCfg.member((LuaValueRef)cfg, (String)"dir");
                LuaValueRef col = LuaCfg.member((LuaValueRef)cfg, (String)"color");
                float dx = RenderCfg.vec3x(dir, 1.0f);
                float dy = RenderCfg.vec3y(dir, -1.0f);
                float dz = RenderCfg.vec3z(dir, 0.3f);
                float r = RenderCfg.vec3x(col, 0.45f);
                float g = RenderCfg.vec3y(col, 0.55f);
                float b = RenderCfg.vec3z(col, 0.85f);
                float intensity = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)0.0));
                if (RenderCfg.approx(dx, this.moonDx) && RenderCfg.approx(dy, this.moonDy) && RenderCfg.approx(dz, this.moonDz) && RenderCfg.approx(r, this.moonR) && RenderCfg.approx(g, this.moonG) && RenderCfg.approx(b, this.moonB) && RenderCfg.approx(intensity, this.moonI)) {
                    return;
                }
                this.moonDx = dx;
                this.moonDy = dy;
                this.moonDz = dz;
                this.moonR = r;
                this.moonG = g;
                this.moonB = b;
                this.moonI = intensity;
                Vector3f v = new Vector3f(dx, dy, dz);
                if (v.lengthSquared() < 1.0E-6f) {
                    v.set(1.0f, -1.0f, 0.0f);
                }
                v.normalizeLocal();
                DirectionalLight moon = this.lights.moon();
                moon.setDirection(v);
                moon.setColor(new ColorRGBA(r, g, b, 1.0f).mult(intensity));
            });
        });
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPrimaryDirectional(String which) {
        String w;
        String string = w = which == null ? "" : which.trim().toLowerCase();
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.setPrimaryDirectional", () -> {
                this.viewport.ensure("setPrimaryDirectional");
                this.lights.ensure();
                if (w.equals(this.lights.primaryDirectional())) {
                    return;
                }
                this.lights.setPrimaryDirectional(w);
                this.shadows.onPrimaryLightChanged();
                log.info("RenderApi: primaryDirectional={}", (Object)w);
            });
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void sunShadowsCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.sunShadowsCfg", () -> {
                this.viewport.ensure("sunShadowsCfg");
                this.lights.ensure();
                this.shadows.applyCfg(cfg);
                DirectionalLightShadowRenderer r = this.shadows.renderer();
                if (r != null) {
                    r.setLight(this.lights.primaryLight());
                }
            });
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void fogCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.fogCfg", () -> {
                this.viewport.ensure("fogCfg");
                this.post.fogCfg(cfg);
            });
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void postCfg(LuaValueRef cfg) {
        this.profiledVoid(() -> {
            this.ensureScene();
            this.onJmeSyncVoid("render.postCfg", () -> {
                this.viewport.ensure("postCfg");
                this.post.postCfg(cfg);
            });
        });
    }
}

