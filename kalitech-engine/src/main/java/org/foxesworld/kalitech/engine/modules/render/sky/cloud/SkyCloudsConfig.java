package org.foxesworld.kalitech.engine.modules.render.sky.cloud;

import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.num;

/**
 * Immutable cloud layer configuration snapshot.
 * <p>
 * Inputs are clamped to stable ranges to keep shader behavior deterministic.
 */
public final class SkyCloudsConfig {

    public final float uvScale;
    public final float speedU;
    public final float speedV;

    public final float coverage;
    public final float density;
    public final float softness;

    public final float sunDx, sunDy, sunDz;
    public final float moonDx, moonDy, moonDz;

    public final float sunR, sunG, sunB;
    public final float moonR, moonG, moonB;

    public final float sunIntensity;
    public final float moonIntensity;

    public final float tintR, tintG, tintB;
    public final float lighting;

    public final float alpha;
    public final float texBlend;
    public final float texExposure;

    public final float timeSec;

    private SkyCloudsConfig(
            float uvScale, float speedU, float speedV,
            float coverage, float density, float softness,
            float sunDx, float sunDy, float sunDz,
            float moonDx, float moonDy, float moonDz,
            float sunR, float sunG, float sunB,
            float moonR, float moonG, float moonB,
            float sunIntensity, float moonIntensity,
            float tintR, float tintG, float tintB,
            float lighting, float alpha,
            float texBlend, float texExposure,
            float timeSec
    ) {
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

    /**
     * Builds a config from a polyglot object.
     * <p>
     * Expected fields:
     * - timeSec: float (monotonic time)
     * - uvScale, speedU, speedV, coverage, density, softness, lighting, alpha
     * - texBlend, texExposure
     * - sunDir, moonDir: vec3
     * - sunColor, moonColor, tintColor: vec3
     * - sunIntensity, moonIntensity
     */
    public static SkyCloudsConfig from(Value cfg) {
        float timeSec = (float) Math.max(0.0, num(cfg, "timeSec", 0.0));

        float uvScale = RenderCfg.clamp((float) num(cfg, "uvScale", 0.6), 0.01f, 32f);
        float speedU = RenderCfg.clamp((float) num(cfg, "speedU", 0.0025), -10f, 10f);
        float speedV = RenderCfg.clamp((float) num(cfg, "speedV", 0.0010), -10f, 10f);

        float coverage = RenderCfg.clamp01((float) num(cfg, "coverage", 0.55));
        float density = RenderCfg.clamp((float) num(cfg, "density", 1.25), 0.0f, 10f);
        float softness = RenderCfg.clamp01((float) num(cfg, "softness", 0.65));

        float lighting = RenderCfg.clamp01((float) num(cfg, "lighting", 0.85));
        float alpha = RenderCfg.clamp01((float) num(cfg, "alpha", 0.90));

        float texBlend = RenderCfg.clamp01((float) num(cfg, "texBlend", 0.0));
        float texExposure = RenderCfg.clamp((float) num(cfg, "texExposure", 1.0), 0.001f, 100f);

        Value sunDir = member(cfg, "sunDir");
        Value moonDir = member(cfg, "moonDir");
        Value sunCol = member(cfg, "sunColor");
        Value moonCol = member(cfg, "moonColor");
        Value tintCol = member(cfg, "tintColor");

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

        float tr = RenderCfg.vec3x(tintCol, 1.0f);
        float tg = RenderCfg.vec3y(tintCol, 1.0f);
        float tb = RenderCfg.vec3z(tintCol, 1.0f);

        return new SkyCloudsConfig(
                uvScale, speedU, speedV,
                coverage, density, softness,
                sdx, sdy, sdz,
                mdx, mdy, mdz,
                sr, sg, sb,
                mr, mg, mb,
                sunInt, moonInt,
                tr, tg, tb,
                lighting, alpha,
                texBlend, texExposure,
                timeSec
        );
    }
}