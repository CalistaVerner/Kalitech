/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyDomeConfig;

public final class SkyDomeUniformCache {
    private final Vector3f tmpSunDir = new Vector3f();
    private final Vector3f tmpMoonDir = new Vector3f();
    private final ColorRGBA tmpSunCol = new ColorRGBA();
    private final ColorRGBA tmpMoonCol = new ColorRGBA();
    private final ColorRGBA tmpZenCol = new ColorRGBA();
    private final ColorRGBA tmpHorCol = new ColorRGBA();
    private float skyBlend = Float.NaN;
    private float texBlend = Float.NaN;
    private float texExposure = Float.NaN;
    private float sunDx = Float.NaN;
    private float sunDy = Float.NaN;
    private float sunDz = Float.NaN;
    private float moonDx = Float.NaN;
    private float moonDy = Float.NaN;
    private float moonDz = Float.NaN;
    private float sunR = Float.NaN;
    private float sunG = Float.NaN;
    private float sunB = Float.NaN;
    private float sunI = Float.NaN;
    private float moonR = Float.NaN;
    private float moonG = Float.NaN;
    private float moonB = Float.NaN;
    private float moonI = Float.NaN;
    private float zenR = Float.NaN;
    private float zenG = Float.NaN;
    private float zenB = Float.NaN;
    private float horR = Float.NaN;
    private float horG = Float.NaN;
    private float horB = Float.NaN;
    private float haze = Float.NaN;
    private float sunDisk = Float.NaN;
    private float moonDisk = Float.NaN;
    private float exposure = Float.NaN;

    public void reset() {
        this.skyBlend = Float.NaN;
        this.texBlend = Float.NaN;
        this.texExposure = Float.NaN;
        this.sunDz = Float.NaN;
        this.sunDy = Float.NaN;
        this.sunDx = Float.NaN;
        this.moonDz = Float.NaN;
        this.moonDy = Float.NaN;
        this.moonDx = Float.NaN;
        this.sunI = Float.NaN;
        this.sunB = Float.NaN;
        this.sunG = Float.NaN;
        this.sunR = Float.NaN;
        this.moonI = Float.NaN;
        this.moonB = Float.NaN;
        this.moonG = Float.NaN;
        this.moonR = Float.NaN;
        this.zenB = Float.NaN;
        this.zenG = Float.NaN;
        this.zenR = Float.NaN;
        this.horB = Float.NaN;
        this.horG = Float.NaN;
        this.horR = Float.NaN;
        this.exposure = Float.NaN;
        this.moonDisk = Float.NaN;
        this.sunDisk = Float.NaN;
        this.haze = Float.NaN;
    }

    public void onTexturesChanged() {
        this.texBlend = Float.NaN;
        this.texExposure = Float.NaN;
    }

    public void apply(Material mat, SkyDomeConfig cfg, boolean hasTex) {
        boolean changed;
        boolean changedTex;
        float texBlendEffective = hasTex ? cfg.texBlend : 0.0f;
        boolean bl = changedTex = !RenderCfg.approx(cfg.skyBlend, this.skyBlend) || !RenderCfg.approx(texBlendEffective, this.texBlend) || !RenderCfg.approx(cfg.texExposure, this.texExposure);
        if (changedTex) {
            this.skyBlend = cfg.skyBlend;
            this.texBlend = texBlendEffective;
            this.texExposure = cfg.texExposure;
            mat.setFloat("SkyBlend", this.skyBlend);
            mat.setFloat("TexBlend", this.texBlend);
            mat.setFloat("TexExposure", this.texExposure);
        }
        boolean bl2 = changed = changedTex || !RenderCfg.approx3(cfg.sunDx, cfg.sunDy, cfg.sunDz, this.sunDx, this.sunDy, this.sunDz) || !RenderCfg.approx3(cfg.moonDx, cfg.moonDy, cfg.moonDz, this.moonDx, this.moonDy, this.moonDz) || !RenderCfg.approx(cfg.sunR, this.sunR) || !RenderCfg.approx(cfg.sunG, this.sunG) || !RenderCfg.approx(cfg.sunB, this.sunB) || !RenderCfg.approx(cfg.sunIntensity, this.sunI) || !RenderCfg.approx(cfg.moonR, this.moonR) || !RenderCfg.approx(cfg.moonG, this.moonG) || !RenderCfg.approx(cfg.moonB, this.moonB) || !RenderCfg.approx(cfg.moonIntensity, this.moonI) || !RenderCfg.approx(cfg.zenR, this.zenR) || !RenderCfg.approx(cfg.zenG, this.zenG) || !RenderCfg.approx(cfg.zenB, this.zenB) || !RenderCfg.approx(cfg.horR, this.horR) || !RenderCfg.approx(cfg.horG, this.horG) || !RenderCfg.approx(cfg.horB, this.horB) || !RenderCfg.approx(cfg.haze, this.haze) || !RenderCfg.approx(cfg.sunDisk, this.sunDisk) || !RenderCfg.approx(cfg.moonDisk, this.moonDisk) || !RenderCfg.approx(cfg.exposure, this.exposure);
        if (!changed) {
            return;
        }
        this.sunDx = cfg.sunDx;
        this.sunDy = cfg.sunDy;
        this.sunDz = cfg.sunDz;
        this.moonDx = cfg.moonDx;
        this.moonDy = cfg.moonDy;
        this.moonDz = cfg.moonDz;
        this.sunR = cfg.sunR;
        this.sunG = cfg.sunG;
        this.sunB = cfg.sunB;
        this.sunI = cfg.sunIntensity;
        this.moonR = cfg.moonR;
        this.moonG = cfg.moonG;
        this.moonB = cfg.moonB;
        this.moonI = cfg.moonIntensity;
        this.zenR = cfg.zenR;
        this.zenG = cfg.zenG;
        this.zenB = cfg.zenB;
        this.horR = cfg.horR;
        this.horG = cfg.horG;
        this.horB = cfg.horB;
        this.haze = cfg.haze;
        this.sunDisk = cfg.sunDisk;
        this.moonDisk = cfg.moonDisk;
        this.exposure = cfg.exposure;
        this.tmpSunDir.set(this.sunDx, this.sunDy, this.sunDz);
        if (this.tmpSunDir.lengthSquared() < 1.0E-6f) {
            this.tmpSunDir.set(-1.0f, -1.0f, -1.0f);
        }
        this.tmpSunDir.normalizeLocal();
        this.tmpMoonDir.set(this.moonDx, this.moonDy, this.moonDz);
        if (this.tmpMoonDir.lengthSquared() < 1.0E-6f) {
            this.tmpMoonDir.set(1.0f, -1.0f, 0.0f);
        }
        this.tmpMoonDir.normalizeLocal();
        this.tmpSunCol.set(this.sunR, this.sunG, this.sunB, 1.0f);
        this.tmpMoonCol.set(this.moonR, this.moonG, this.moonB, 1.0f);
        this.tmpZenCol.set(this.zenR, this.zenG, this.zenB, 1.0f);
        this.tmpHorCol.set(this.horR, this.horG, this.horB, 1.0f);
        mat.setVector3("SunDir", this.tmpSunDir);
        mat.setVector3("MoonDir", this.tmpMoonDir);
        mat.setColor("SunColor", this.tmpSunCol);
        mat.setFloat("SunIntensity", this.sunI);
        mat.setColor("MoonColor", this.tmpMoonCol);
        mat.setFloat("MoonIntensity", this.moonI);
        mat.setColor("ZenithColor", this.tmpZenCol);
        mat.setColor("HorizonColor", this.tmpHorCol);
        mat.setFloat("Haze", this.haze);
        mat.setFloat("SunDisk", this.sunDisk);
        mat.setFloat("MoonDisk", this.moonDisk);
        mat.setFloat("Exposure", this.exposure);
    }
}

