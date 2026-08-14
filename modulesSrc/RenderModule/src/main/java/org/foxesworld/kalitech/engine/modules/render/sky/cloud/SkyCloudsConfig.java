/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.sky.cloud;

import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SkyCloudsConfig {
    public final float uvScale;
    public final float speedU;
    public final float speedV;
    public final float coverage;
    public final float density;
    public final float softness;
    public final float sunDx;
    public final float sunDy;
    public final float sunDz;
    public final float moonDx;
    public final float moonDy;
    public final float moonDz;
    public final float sunR;
    public final float sunG;
    public final float sunB;
    public final float moonR;
    public final float moonG;
    public final float moonB;
    public final float sunIntensity;
    public final float moonIntensity;
    public final float tintR;
    public final float tintG;
    public final float tintB;
    public final float lighting;
    public final float alpha;
    public final float texBlend;
    public final float texExposure;
    public final float timeSec;

    private SkyCloudsConfig(float uvScale, float speedU, float speedV, float coverage, float density, float softness, float sunDx, float sunDy, float sunDz, float moonDx, float moonDy, float moonDz, float sunR, float sunG, float sunB, float moonR, float moonG, float moonB, float sunIntensity, float moonIntensity, float tintR, float tintG, float tintB, float lighting, float alpha, float texBlend, float texExposure, float timeSec) {
        this.uvScale = uvScale;
        this.speedU = speedU;
        this.speedV = speedV;
        this.coverage = coverage;
        this.density = density;
        this.softness = softness;
        this.sunDx = sunDx;
        this.sunDy = sunDy;
        this.sunDz = sunDz;
        this.moonDx = moonDx;
        this.moonDy = moonDy;
        this.moonDz = moonDz;
        this.sunR = sunR;
        this.sunG = sunG;
        this.sunB = sunB;
        this.moonR = moonR;
        this.moonG = moonG;
        this.moonB = moonB;
        this.sunIntensity = sunIntensity;
        this.moonIntensity = moonIntensity;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
        this.lighting = lighting;
        this.alpha = alpha;
        this.texBlend = texBlend;
        this.texExposure = texExposure;
        this.timeSec = timeSec;
    }

    public static SkyCloudsConfig from(LuaValueRef cfg) {
        float timeSec = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"timeSec", (double)0.0));
        float uvScale = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"uvScale", (double)0.6), 0.01f, 32.0f);
        float speedU = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"speedU", (double)0.0025), -10.0f, 10.0f);
        float speedV = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"speedV", (double)0.001), -10.0f, 10.0f);
        float coverage = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"coverage", (double)0.55));
        float density = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"density", (double)1.25), 0.0f, 10.0f);
        float softness = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"softness", (double)0.65));
        float lighting = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"lighting", (double)0.85));
        float alpha = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"alpha", (double)0.9));
        float texBlend = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"texBlend", (double)0.0));
        float texExposure = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"texExposure", (double)1.0), 0.001f, 100.0f);
        LuaValueRef sunDir = LuaCfg.member((LuaValueRef)cfg, (String)"sunDir");
        LuaValueRef moonDir = LuaCfg.member((LuaValueRef)cfg, (String)"moonDir");
        LuaValueRef sunCol = LuaCfg.member((LuaValueRef)cfg, (String)"sunColor");
        LuaValueRef moonCol = LuaCfg.member((LuaValueRef)cfg, (String)"moonColor");
        LuaValueRef tintCol = LuaCfg.member((LuaValueRef)cfg, (String)"tintColor");
        float sdx = RenderCfg.vec3x(sunDir, -1.0f);
        float sdy = RenderCfg.vec3y(sunDir, -1.0f);
        float sdz = RenderCfg.vec3z(sunDir, -0.3f);
        float mdx = RenderCfg.vec3x(moonDir, 1.0f);
        float mdy = RenderCfg.vec3y(moonDir, -1.0f);
        float mdz = RenderCfg.vec3z(moonDir, 0.3f);
        float sr = RenderCfg.vec3x(sunCol, 1.0f);
        float sg = RenderCfg.vec3y(sunCol, 0.98f);
        float sb = RenderCfg.vec3z(sunCol, 0.9f);
        float mr = RenderCfg.vec3x(moonCol, 0.45f);
        float mg = RenderCfg.vec3y(moonCol, 0.55f);
        float mb = RenderCfg.vec3z(moonCol, 0.85f);
        float sunInt = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"sunIntensity", (double)1.0));
        float moonInt = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"moonIntensity", (double)0.0));
        float tr = RenderCfg.vec3x(tintCol, 1.0f);
        float tg = RenderCfg.vec3y(tintCol, 1.0f);
        float tb = RenderCfg.vec3z(tintCol, 1.0f);
        return new SkyCloudsConfig(uvScale, speedU, speedV, coverage, density, softness, sdx, sdy, sdz, mdx, mdy, mdz, sr, sg, sb, mr, mg, mb, sunInt, moonInt, tr, tg, tb, lighting, alpha, texBlend, texExposure, timeSec);
    }
}

