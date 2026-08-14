/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SkyDomeConfig {
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
    public final float zenR;
    public final float zenG;
    public final float zenB;
    public final float horR;
    public final float horG;
    public final float horB;
    public final float haze;
    public final float sunDisk;
    public final float moonDisk;
    public final float exposure;
    public final float skyBlend;
    public final float texBlend;
    public final float texExposure;

    private SkyDomeConfig(float sunDx, float sunDy, float sunDz, float moonDx, float moonDy, float moonDz, float sunR, float sunG, float sunB, float moonR, float moonG, float moonB, float sunIntensity, float moonIntensity, float zenR, float zenG, float zenB, float horR, float horG, float horB, float haze, float sunDisk, float moonDisk, float exposure, float skyBlend, float texBlend, float texExposure) {
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
        this.zenR = zenR;
        this.zenG = zenG;
        this.zenB = zenB;
        this.horR = horR;
        this.horG = horG;
        this.horB = horB;
        this.haze = haze;
        this.sunDisk = sunDisk;
        this.moonDisk = moonDisk;
        this.exposure = exposure;
        this.skyBlend = skyBlend;
        this.texBlend = texBlend;
        this.texExposure = texExposure;
    }

    public static SkyDomeConfig from(LuaValueRef cfg) {
        LuaValueRef sunDir = LuaCfg.member((LuaValueRef)cfg, (String)"sunDir");
        LuaValueRef moonDir = LuaCfg.member((LuaValueRef)cfg, (String)"moonDir");
        LuaValueRef sunCol = LuaCfg.member((LuaValueRef)cfg, (String)"sunColor");
        LuaValueRef moonCol = LuaCfg.member((LuaValueRef)cfg, (String)"moonColor");
        LuaValueRef zen = LuaCfg.member((LuaValueRef)cfg, (String)"zenithColor");
        LuaValueRef hor = LuaCfg.member((LuaValueRef)cfg, (String)"horizonColor");
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
        float zr = RenderCfg.vec3x(zen, 0.1f);
        float zg = RenderCfg.vec3y(zen, 0.17f);
        float zb = RenderCfg.vec3z(zen, 0.32f);
        float hr = RenderCfg.vec3x(hor, 0.65f);
        float hg = RenderCfg.vec3y(hor, 0.72f);
        float hb = RenderCfg.vec3z(hor, 0.82f);
        float haze = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"haze", (double)0.55));
        float sunDisk = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"sunDisk", (double)45.0), 0.5f, 500.0f);
        float moonDisk = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"moonDisk", (double)120.0), 0.5f, 2000.0f);
        float exposure = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"exposure", (double)1.0), 0.05f, 10.0f);
        float skyBlend = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"skyBlend", (double)0.0));
        float texBlend = RenderCfg.clamp01((float)LuaCfg.num((LuaValueRef)cfg, (String)"texBlend", (double)0.0));
        float texExposure = RenderCfg.clamp((float)LuaCfg.num((LuaValueRef)cfg, (String)"texExposure", (double)8.0), 0.001f, 100.0f);
        return new SkyDomeConfig(sdx, sdy, sdz, mdx, mdy, mdz, sr, sg, sb, mr, mg, mb, sunInt, moonInt, zr, zg, zb, hr, hg, hb, haze, sunDisk, moonDisk, exposure, skyBlend, texBlend, texExposure);
    }
}

