// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/EnhancedSplitHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadows.SplitHysteresisManager;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;

import java.util.Objects;

/**
 * Motion-aware split hysteresis for cascaded shadows.
 * Uses camera speed + acceleration (derived from ctx.dt) to stabilize split planes.
 */
public final class EnhancedSplitHysteresisFilter implements ShadowFilter {

    private final SplitHysteresisManager hysteresis = new SplitHysteresisManager();
    private final MotionCfg motionCfg = new MotionCfg();
    // motion state (no allocations)
    private final Vector3f lastPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private final Vector3f vel = new Vector3f();
    private final Vector3f accel = new Vector3f();
    private float motionFactorSmoothed = 0f;

    private static float clamp01(float v) {
        return (v < 0f) ? 0f : (v > 1f ? 1f : v);
    }

    private static void enforceMonotonicity(float[] splits, float minGap) {
        Objects.requireNonNull(splits, "splits");
        if (splits.length < 2) return;

        float g = Math.max(1e-6f, minGap);
        for (int i = 1; i < splits.length; i++) {
            float prev = splits[i - 1];
            float cur = splits[i];
            if (cur <= prev + g) splits[i] = prev + g;
        }
    }

    @Override
    public String id() {
        return "EnhancedSplitHysteresis";
    }

    public MotionCfg motionCfg() {
        return motionCfg;
    }

    public SplitHysteresisManager.Cfg hysteresisCfg() {
        return hysteresis.cfg();
    }

    public void reset() {
        hysteresis.reset();
        lastPos.set(Float.NaN, Float.NaN, Float.NaN);
        vel.set(0, 0, 0);
        accel.set(0, 0, 0);
        motionFactorSmoothed = 0f;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (ctx == null) return;
        if (!motionCfg.enabled) return;
        updateCameraMotion(ctx);
    }

    @Override
    public void beforeSplits(ShadowFrameContext ctx) {
        if (ctx == null) return;
        if (!motionCfg.enabled) return;

        if (ctx.splitFarsWanted == null || ctx.splitFarsFinal == null) return;
        if (ctx.cascades <= 0) return;

        float motionFactor = computeMotionFactor(ctx);

        // Drive hysteresis using smoothed motion factor
        SplitHysteresisManager.Cfg cfg = hysteresis.cfg();
        cfg.speedFactor = motionCfg.baseSpeedFactor * motionFactor;
        cfg.minThreshold = motionCfg.minThresholdBase + (motionCfg.minThresholdMotionAdd * motionFactor);
        cfg.maxThreshold = motionCfg.maxThresholdBase * (1.0f + motionCfg.maxThresholdMotionMul * motionFactor);

        float effectiveSpeed = Math.max(0f, ctx.cameraSpeed) * motionFactor;

        float[] stable = hysteresis.stabilize(ctx.splitFarsWanted, effectiveSpeed);
        System.arraycopy(stable, 0, ctx.splitFarsFinal, 0, ctx.cascades);

        enforceMonotonicity(ctx.splitFarsFinal, motionCfg.minSplitGap);

        // Optional safety: keep last split not beyond viewFar (if provided)
        if (ctx.viewFar > 0f) {
            int last = ctx.cascades - 1;
            if (ctx.splitFarsFinal[last] > ctx.viewFar) ctx.splitFarsFinal[last] = ctx.viewFar;
            enforceMonotonicity(ctx.splitFarsFinal, motionCfg.minSplitGap);
        }
    }

    private void updateCameraMotion(ShadowFrameContext ctx) {
        if (ctx.viewCam == null) return;

        float dt = clampDt(ctx.dt);

        Vector3f p = ctx.viewCam.getLocation();
        if (Float.isNaN(lastPos.x)) {
            lastPos.set(p);
            vel.set(0, 0, 0);
            accel.set(0, 0, 0);
            return;
        }

        float vx = (p.x - lastPos.x) / dt;
        float vy = (p.y - lastPos.y) / dt;
        float vz = (p.z - lastPos.z) / dt;

        float ax = (vx - vel.x) / dt;
        float ay = (vy - vel.y) / dt;
        float az = (vz - vel.z) / dt;

        accel.set(ax, ay, az);
        vel.set(vx, vy, vz);
        lastPos.set(p);
    }

    private float computeMotionFactor(ShadowFrameContext ctx) {
        float speed = Math.max(0f, ctx.cameraSpeed);

        float accelMag = accel.length();

        float speedFactor = (motionCfg.fullSpeed > 0f) ? clamp01(speed / motionCfg.fullSpeed) : 0f;
        float accelFactor = (motionCfg.fullAccel > 0f) ? clamp01(accelMag / motionCfg.fullAccel) : 0f;

        float raw = Math.max(speedFactor, accelFactor * motionCfg.accelWeight);
        raw = clamp01(raw);

        float dt = clampDt(ctx.dt);
        float a = clamp01(dt * Math.max(0f, motionCfg.motionSmoothing));
        motionFactorSmoothed = FastMath.interpolateLinear(a, motionFactorSmoothed, raw);

        // If you already compute a global ctx.cameraMotionWeight in renderer, you can blend it here:
        // motionFactorSmoothed = Math.max(motionFactorSmoothed, clamp01(ctx.cameraMotionWeight));

        return motionFactorSmoothed;
    }

    private float clampDt(float dt) {
        if (!(dt > 0f)) return 1f / 60f;
        return FastMath.clamp(dt, motionCfg.dtMin, motionCfg.dtMax);
    }

    public static final class MotionCfg {

        /**
         * If false, filter passes through wanted splits unchanged.
         */
        public boolean enabled = true;

        /**
         * Clamp dt used for motion integration.
         */
        public float dtMin = 1e-4f;

        /**
         * Clamp dt used for motion integration.
         */
        public float dtMax = 0.05f;

        /**
         * Speed (world units/s) that maps to speedFactor=1.
         */
        public float fullSpeed = 50.0f;

        /**
         * Acceleration (world units/s^2) that maps to accelFactor=1.
         */
        public float fullAccel = 100.0f;

        /**
         * Weight multiplier for accel factor in motion factor calculation.
         */
        public float accelWeight = 1.5f;

        /**
         * Smoothing (per second) for motionFactor. Higher = faster response.
         */
        public float motionSmoothing = 8.0f;

        /**
         * Base hysteresis speed factor multiplier.
         */
        public float baseSpeedFactor = 0.15f;

        /**
         * Minimum split threshold base.
         */
        public float minThresholdBase = 0.25f;

        /**
         * Additional min threshold per motion factor.
         */
        public float minThresholdMotionAdd = 0.50f;

        /**
         * Maximum split threshold base multiplier.
         */
        public float maxThresholdBase = 5.0f;

        /**
         * Additional max threshold multiplier per motion factor.
         */
        public float maxThresholdMotionMul = 1.0f;

        /**
         * Minimum enforced gap between split fars.
         */
        public float minSplitGap = 0.001f;
    }
}