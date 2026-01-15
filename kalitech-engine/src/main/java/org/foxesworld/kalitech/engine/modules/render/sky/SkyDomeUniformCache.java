// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyDomeUniformCache.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;

/**
 * Caches last pushed uniform values to avoid redundant GPU updates.
 * <p>
 * Uses reusable objects to prevent per-update allocations.
 */
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
    private float sunDx = Float.NaN, sunDy = Float.NaN, sunDz = Float.NaN;
    private float moonDx = Float.NaN, moonDy = Float.NaN, moonDz = Float.NaN;
    private float sunR = Float.NaN, sunG = Float.NaN, sunB = Float.NaN, sunI = Float.NaN;
    private float moonR = Float.NaN, moonG = Float.NaN, moonB = Float.NaN, moonI = Float.NaN;
    private float zenR = Float.NaN, zenG = Float.NaN, zenB = Float.NaN;
    private float horR = Float.NaN, horG = Float.NaN, horB = Float.NaN;
    private float haze = Float.NaN, sunDisk = Float.NaN, moonDisk = Float.NaN, exposure = Float.NaN;

    public void reset() {
        skyBlend = Float.NaN;
        texBlend = Float.NaN;
        texExposure = Float.NaN;

        sunDx = sunDy = sunDz = Float.NaN;
        moonDx = moonDy = moonDz = Float.NaN;

        sunR = sunG = sunB = sunI = Float.NaN;
        moonR = moonG = moonB = moonI = Float.NaN;

        zenR = zenG = zenB = Float.NaN;
        horR = horG = horB = Float.NaN;

        haze = sunDisk = moonDisk = exposure = Float.NaN;
    }

    public void onTexturesChanged() {
        texBlend = Float.NaN;
        texExposure = Float.NaN;
    }

    public void apply(Material mat, SkyDomeConfig cfg, boolean hasTex) {
        float texBlendEffective = hasTex ? cfg.texBlend : 0.0f;

        boolean changedTex =
                !RenderCfg.approx(cfg.skyBlend, skyBlend) ||
                        !RenderCfg.approx(texBlendEffective, texBlend) ||
                        !RenderCfg.approx(cfg.texExposure, texExposure);

        if (changedTex) {
            skyBlend = cfg.skyBlend;
            texBlend = texBlendEffective;
            texExposure = cfg.texExposure;
            mat.setFloat("SkyBlend", skyBlend);
            mat.setFloat("TexBlend", texBlend);
            mat.setFloat("TexExposure", texExposure);
        }

        boolean changed =
                changedTex ||
                        !RenderCfg.approx3(cfg.sunDx, cfg.sunDy, cfg.sunDz, sunDx, sunDy, sunDz) ||
                        !RenderCfg.approx3(cfg.moonDx, cfg.moonDy, cfg.moonDz, moonDx, moonDy, moonDz) ||
                        !RenderCfg.approx(cfg.sunR, sunR) || !RenderCfg.approx(cfg.sunG, sunG) || !RenderCfg.approx(cfg.sunB, sunB) || !RenderCfg.approx(cfg.sunIntensity, sunI) ||
                        !RenderCfg.approx(cfg.moonR, moonR) || !RenderCfg.approx(cfg.moonG, moonG) || !RenderCfg.approx(cfg.moonB, moonB) || !RenderCfg.approx(cfg.moonIntensity, moonI) ||
                        !RenderCfg.approx(cfg.zenR, zenR) || !RenderCfg.approx(cfg.zenG, zenG) || !RenderCfg.approx(cfg.zenB, zenB) ||
                        !RenderCfg.approx(cfg.horR, horR) || !RenderCfg.approx(cfg.horG, horG) || !RenderCfg.approx(cfg.horB, horB) ||
                        !RenderCfg.approx(cfg.haze, haze) ||
                        !RenderCfg.approx(cfg.sunDisk, sunDisk) ||
                        !RenderCfg.approx(cfg.moonDisk, moonDisk) ||
                        !RenderCfg.approx(cfg.exposure, exposure);

        if (!changed) {
            return;
        }

        sunDx = cfg.sunDx;
        sunDy = cfg.sunDy;
        sunDz = cfg.sunDz;

        moonDx = cfg.moonDx;
        moonDy = cfg.moonDy;
        moonDz = cfg.moonDz;

        sunR = cfg.sunR;
        sunG = cfg.sunG;
        sunB = cfg.sunB;
        sunI = cfg.sunIntensity;

        moonR = cfg.moonR;
        moonG = cfg.moonG;
        moonB = cfg.moonB;
        moonI = cfg.moonIntensity;

        zenR = cfg.zenR;
        zenG = cfg.zenG;
        zenB = cfg.zenB;

        horR = cfg.horR;
        horG = cfg.horG;
        horB = cfg.horB;

        haze = cfg.haze;
        sunDisk = cfg.sunDisk;
        moonDisk = cfg.moonDisk;
        exposure = cfg.exposure;

        tmpSunDir.set(sunDx, sunDy, sunDz);
        if (tmpSunDir.lengthSquared() < 1e-6f) tmpSunDir.set(-1f, -1f, -1f);
        tmpSunDir.normalizeLocal();

        tmpMoonDir.set(moonDx, moonDy, moonDz);
        if (tmpMoonDir.lengthSquared() < 1e-6f) tmpMoonDir.set(1f, -1f, 0f);
        tmpMoonDir.normalizeLocal();

        tmpSunCol.set(sunR, sunG, sunB, 1f);
        tmpMoonCol.set(moonR, moonG, moonB, 1f);
        tmpZenCol.set(zenR, zenG, zenB, 1f);
        tmpHorCol.set(horR, horG, horB, 1f);

        mat.setVector3("SunDir", tmpSunDir);
        mat.setVector3("MoonDir", tmpMoonDir);

        mat.setColor("SunColor", tmpSunCol);
        mat.setFloat("SunIntensity", sunI);

        mat.setColor("MoonColor", tmpMoonCol);
        mat.setFloat("MoonIntensity", moonI);

        mat.setColor("ZenithColor", tmpZenCol);
        mat.setColor("HorizonColor", tmpHorCol);

        mat.setFloat("Haze", haze);
        mat.setFloat("SunDisk", sunDisk);
        mat.setFloat("MoonDisk", moonDisk);
        mat.setFloat("Exposure", exposure);
    }
}