// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/filters/EnhancedSplitHysteresisFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadows.filters;

import com.jme3.math.FastMath;
import org.foxesworld.kalitech.engine.modules.render.shadows.SplitHysteresisManager;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadows.pipeline.ShadowPipeline;

import java.util.Objects;

/**
 * Enhanced split stabilization.
 *
 * <p>Adds speed-aware damping on top of {@link SplitHysteresisManager}.
 * Presence in pipeline == enabled.</p>
 */
public final class EnhancedSplitHysteresisFilter implements ShadowFilter {

    public final Cfg cfg;
    private final SplitHysteresisManager hysteresis;
    private float baseHalfLifeSeconds = Float.NaN;

    public EnhancedSplitHysteresisFilter() {
        this(new SplitHysteresisManager(), new Cfg());
    }

    public EnhancedSplitHysteresisFilter(SplitHysteresisManager hysteresis, Cfg cfg) {
        this.hysteresis = Objects.requireNonNull(hysteresis, "hysteresis");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    public SplitHysteresisManager.Cfg baseCfg() {
        return hysteresis.cfg();
    }

    @Override
    public void onAdded(ShadowPipeline pipeline) {
        hysteresis.reset();
        baseHalfLifeSeconds = Float.NaN;
    }

    @Override
    public void beforeSplits(ShadowFrameContext ctx) {
        // Capture base once (so runtime tweaking of cfg remains stable unless reset)
        SplitHysteresisManager.Cfg hc = hysteresis.cfg();
        if (Float.isNaN(baseHalfLifeSeconds)) {
            baseHalfLifeSeconds = hc.smoothingHalfLifeSeconds;
        }

        // k: 0..1 depends on speed
        float k = (cfg.fastMotionSpeed <= 0f)
                ? 0f
                : FastMath.clamp(ctx.cameraSpeed / cfg.fastMotionSpeed, 0f, 1f);

        // damp: 1 -> no extra damping, cfg.fastMotionDamping -> full damping at high speed
        float damp = FastMath.interpolateLinear(k, 1.0f, cfg.fastMotionDamping);

        // In half-life space, "more damping" means slower response => larger half-life.
        // We scale half-life by 1/damp (damp < 1 => larger half-life).
        float effectiveHalfLife = baseHalfLifeSeconds;
        if (damp > 1e-4f) {
            effectiveHalfLife = baseHalfLifeSeconds / damp;
        }

        hc.smoothingHalfLifeSeconds = FastMath.clamp(effectiveHalfLife, cfg.minHalfLifeSeconds, cfg.maxHalfLifeSeconds);
    }

    @Override
    public void afterSplits(ShadowFrameContext ctx) {
        if (ctx.splitFarsFinal == null || ctx.splitFarsFinal.length == 0) {
            return;
        }

        float[] stabilized = hysteresis.stabilize(ctx.splitFarsFinal, ctx.cameraSpeed, ctx.dt);

        // Keep original reference if the rest of the pipeline expects ctx.splitFarsFinal identity.
        if (stabilized != ctx.splitFarsFinal) {
            float[] dst = ctx.splitFarsFinal;
            int n = Math.min(dst.length, stabilized.length);
            for (int i = 0; i < n; i++) {
                dst[i] = stabilized[i];
            }
        }
    }

    public static final class Cfg {
        /**
         * Extra damping when camera moves fast (multiplier).
         * <p>Typical range: 0.35..0.75 (smaller = more damping at high speed).</p>
         */
        public float fastMotionDamping = 0.45f;

        /**
         * Camera speed (units/sec) at which fast damping reaches full strength.
         */
        public float fastMotionSpeed = 12.0f;

        /**
         * Clamp for effective smoothing half-life to avoid extreme values.
         */
        public float minHalfLifeSeconds = 0.02f;

        /**
         * Clamp for effective smoothing half-life to avoid extreme values.
         */
        public float maxHalfLifeSeconds = 0.50f;
    }
}