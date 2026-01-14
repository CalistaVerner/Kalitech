// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/ShadowFilteringSystem.java
// Author: Calista Verner (K\u039bYL\u039b)
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.math.FastMath;

/**
 * Shadow filtering policy executor.
 * <p>
 * This class is renderer-agnostic: it computes per-cascade filtering parameters
 * (radius/samples/mode) but does not bind uniforms or textures by itself.
 * A renderer or a pipeline filter may read {@link #getShaderConfig(int)} and
 * apply it to materials/shaders when those parameters are supported.
 */
public final class ShadowFilteringSystem {

    private final FilteringConfig cfg;
    private final ShaderConfig[] perCascade;

    public ShadowFilteringSystem(int numCascades, FilteringConfig cfg) {
        int n = Math.max(1, numCascades);
        this.cfg = (cfg == null) ? new FilteringConfig() : cfg;
        this.perCascade = new ShaderConfig[n];
        for (int i = 0; i < n; i++) {
            perCascade[i] = new ShaderConfig();
        }
        reset();
    }

    public FilteringConfig cfg() {
        return cfg;
    }

    public int cascades() {
        return perCascade.length;
    }

    public void reset() {
        for (int i = 0; i < perCascade.length; i++) {
            perCascade[i].reset(cfg.mode, cfg.baseRadius, cfg.baseSamples, cfg.lightSize);
        }
    }

    /**
     * Updates per-cascade filtering parameters.
     *
     * @param dtSeconds   delta time (seconds)
     * @param cascadeFars far distance per cascade (optional, can be null)
     * @param cameraSpeed camera speed (units/sec)
     */
    public void update(float dtSeconds, float[] cascadeFars, float cameraSpeed) {
        if (!cfg.adaptive) {
            for (int i = 0; i < perCascade.length; i++) {
                float r = baseRadius(i);
                int s = baseSamples(i);
                perCascade[i].reset(cfg.mode, clampRadius(r), clampSamples(s), cfg.lightSize);
            }
            return;
        }

        float motionK = (cfg.motionSpeedRef <= 0f) ? 0f : FastMath.clamp(cameraSpeed / cfg.motionSpeedRef, 0f, 1f);
        float motionScale = 1.0f + cfg.motionRadiusScale * motionK;

        int n = perCascade.length;
        for (int i = 0; i < n; i++) {
            float cascadeFactor = (n <= 1) ? 0f : (float) i / (float) (n - 1);

            float r = baseRadius(i);
            r *= (1.0f + cfg.distanceRadiusScale * cascadeFactor);
            r *= motionScale;
            r = clampRadius(r);

            int s = baseSamples(i);
            float samplesScale = 1.0f - cfg.distanceSamplesFalloff * cascadeFactor;
            s = Math.round(s * samplesScale);
            s = clampSamples(s);

            perCascade[i].reset(cfg.mode, r, s, cfg.lightSize);
        }
    }

    public ShaderConfig getShaderConfig(int cascade) {
        if (cascade < 0 || cascade >= perCascade.length) return null;
        return perCascade[cascade];
    }

    private float baseRadius(int cascade) {
        float[] a = cfg.baseRadiusPerCascade;
        if (a != null && cascade < a.length) return a[cascade];
        return cfg.baseRadius;
    }

    private int baseSamples(int cascade) {
        int[] a = cfg.baseSamplesPerCascade;
        if (a != null && cascade < a.length) return a[cascade];
        return cfg.baseSamples;
    }

    private float clampRadius(float r) {
        return FastMath.clamp(r, Math.max(0f, cfg.minRadius), Math.max(cfg.minRadius, cfg.maxRadius));
    }

    private int clampSamples(int s) {
        int max = Math.max(1, cfg.maxSamples);
        return Math.max(1, Math.min(max, s));
    }

    public enum FilteringMode {
        PCF,
        PCSS,
        EVSM,
        HYBRID
    }

    public static final class FilteringConfig {
        public FilteringMode mode = FilteringMode.PCF;
        public boolean adaptive = false;

        public float[] baseRadiusPerCascade = null;
        public float baseRadius = 1.5f;

        public int[] baseSamplesPerCascade = null;
        public int baseSamples = 16;

        public int maxSamples = 64;

        public float minRadius = 0.25f;
        public float maxRadius = 6.0f;

        public float motionRadiusScale = 0.25f;
        public float motionSpeedRef = 15.0f;

        public float distanceRadiusScale = 1.25f;
        public float distanceSamplesFalloff = 0.55f;

        public float lightSize = 0.0f;

        public FilteringConfig copy() {
            FilteringConfig c = new FilteringConfig();
            c.mode = this.mode;
            c.adaptive = this.adaptive;
            c.baseRadius = this.baseRadius;
            c.baseSamples = this.baseSamples;
            c.maxSamples = this.maxSamples;
            c.minRadius = this.minRadius;
            c.maxRadius = this.maxRadius;
            c.motionRadiusScale = this.motionRadiusScale;
            c.motionSpeedRef = this.motionSpeedRef;
            c.distanceRadiusScale = this.distanceRadiusScale;
            c.distanceSamplesFalloff = this.distanceSamplesFalloff;
            c.lightSize = this.lightSize;
            c.baseRadiusPerCascade = (this.baseRadiusPerCascade == null) ? null : this.baseRadiusPerCascade.clone();
            c.baseSamplesPerCascade = (this.baseSamplesPerCascade == null) ? null : this.baseSamplesPerCascade.clone();
            return c;
        }
    }

    public static final class ShaderConfig {
        public FilteringMode mode;
        public float filterRadius;
        public int samples;
        public float lightSize;

        public ShaderConfig reset(FilteringMode mode, float radius, int samples, float lightSize) {
            this.mode = mode;
            this.filterRadius = radius;
            this.samples = samples;
            this.lightSize = lightSize;
            return this;
        }
    }
}