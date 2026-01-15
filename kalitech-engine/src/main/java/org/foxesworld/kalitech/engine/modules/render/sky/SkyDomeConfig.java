// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyDomeConfig.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;

/**
 * Immutable sky dome configuration snapshot.
 * <p>
 * The parser clamps inputs to stable ranges to avoid shader instabilities and invalid values.
 */
public final class SkyDomeConfig {

    public final float sunDx, sunDy, sunDz;
    public final float moonDx, moonDy, moonDz;

    public final float sunR, sunG, sunB;
    public final float moonR, moonG, moonB;

    public final float sunIntensity;
    public final float moonIntensity;

    public final float zenR, zenG, zenB;
    public final float horR, horG, horB;

    public final float haze;
    public final float sunDisk;
    public final float moonDisk;
    public final float exposure;

    public final float skyBlend;
    public final float texBlend;
    public final float texExposure;

    private SkyDomeConfig(
            float sunDx, float sunDy, float sunDz,
            float moonDx, float moonDy, float moonDz,
            float sunR, float sunG, float sunB,
            float moonR, float moonG, float moonB,
            float sunIntensity, float moonIntensity,
            float zenR, float zenG, float zenB,
            float horR, float horG, float horB,
            float haze, float sunDisk, float moonDisk, float exposure,
            float skyBlend, float texBlend, float texExposure
    ) {
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

    /**
     * Builds a config from a polyglot object.
     * <p>
     * Expected fields:
     * - sunDir, moonDir: vec3
     * - sunColor, moonColor, zenithColor, horizonColor: vec3
     * - sunIntensity, moonIntensity, haze, sunDisk, moonDisk, exposure, skyBlend, texBlend, texExposure
     */
    public static SkyDomeConfig from(Value cfg) {
        Value sunDir = member(cfg, "sunDir");
        Value moonDir = member(cfg, "moonDir");
        Value sunCol = member(cfg, "sunColor");
        Value moonCol = member(cfg, "moonColor");
        Value zen = member(cfg, "zenithColor");
        Value hor = member(cfg, "horizonColor");

        float sdx = RenderCfg.vec3x(sunDir, -1f);
        float sdy = RenderCfg.vec3y(sunDir, -1f);
        float sdz = RenderCfg.vec3z(sunDir, -0.3f);

        float mdx = RenderCfg.vec3x(moonDir, 1f);
        float mdy = RenderCfg.vec3y(moonDir, -1f);
        float mdz = RenderCfg.vec3z(moonDir, 0.3f);

        float sr = RenderCfg.vec3x(sunCol, 1f);
        float sg = RenderCfg.vec3y(sunCol, 0.98f);
        float sb = RenderCfg.vec3z(sunCol, 0.90f);

        float mr = RenderCfg.vec3x(moonCol, 0.45f);
        float mg = RenderCfg.vec3y(moonCol, 0.55f);
        float mb = RenderCfg.vec3z(moonCol, 0.85f);

        float sunInt = (float) Math.max(0.0, num(cfg, "sunIntensity", 1.0));
        float moonInt = (float) Math.max(0.0, num(cfg, "moonIntensity", 0.0));

        float zr = RenderCfg.vec3x(zen, 0.10f);
        float zg = RenderCfg.vec3y(zen, 0.17f);
        float zb = RenderCfg.vec3z(zen, 0.32f);

        float hr = RenderCfg.vec3x(hor, 0.65f);
        float hg = RenderCfg.vec3y(hor, 0.72f);
        float hb = RenderCfg.vec3z(hor, 0.82f);

        float haze = RenderCfg.clamp01((float) num(cfg, "haze", 0.55));
        float sunDisk = RenderCfg.clamp((float) num(cfg, "sunDisk", 45.0), 0.5f, 500f);
        float moonDisk = RenderCfg.clamp((float) num(cfg, "moonDisk", 120.0), 0.5f, 2000f);
        float exposure = RenderCfg.clamp((float) num(cfg, "exposure", 1.0), 0.05f, 10f);

        float skyBlend = RenderCfg.clamp01((float) num(cfg, "skyBlend", 0.0));
        float texBlend = RenderCfg.clamp01((float) num(cfg, "texBlend", 0.0));
        float texExposure = RenderCfg.clamp((float) num(cfg, "texExposure", 8.0), 0.001f, 100.0f);

        return new SkyDomeConfig(
                sdx, sdy, sdz,
                mdx, mdy, mdz,
                sr, sg, sb,
                mr, mg, mb,
                sunInt, moonInt,
                zr, zg, zb,
                hr, hg, hb,
                haze, sunDisk, moonDisk, exposure,
                skyBlend, texBlend, texExposure
        );
    }
}