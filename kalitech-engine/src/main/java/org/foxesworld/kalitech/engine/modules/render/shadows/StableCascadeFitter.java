// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/StableCascadeFitter.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

import com.jme3.math.Vector3f;

/**
 * Stable cascade fitter (CDPR-style building blocks):
 * - square ortho extents
 * - quantized radius (in world units, usually snapped to texel world size)
 * - z-fit with padding (receiver/caster-aware hook-ready)
 * <p>
 * This class does NOT touch JME internals. It just produces stable extents.
 */
public final class StableCascadeFitter {

    private final FitCfg cfg = new FitCfg();

    public FitCfg cfg() {
        return cfg;
    }

    /**
     * Fits a cascade sphere (center + radius) to stable square extents.
     *
     * @param sphereCenterWS cascade center (world space)
     * @param sphereRadius   cascade radius (world units)
     * @param casterMinZ     optional: min depth along light direction (relative), use Float.NaN to ignore
     * @param casterMaxZ     optional: max depth along light direction (relative), use Float.NaN to ignore
     */
    public FitOut fitSphere(Vector3f sphereCenterWS, float sphereRadius, float casterMinZ, float casterMaxZ, FitOut out) {
        if (out == null) out = new FitOut();
        out.centerWS.set(sphereCenterWS);

        float r = Math.max(0.001f, sphereRadius);
        r *= Math.max(1.0f, cfg.extentsPadding);

        // Quantize radius in WORLD UNITS (best when step == texelWorldSize * k)
        out.quantized = false;
        if (cfg.radiusQuantStep > 0f) {
            float step = cfg.radiusQuantStep;
            float q = (float) (Math.ceil(r / step) * step);
            if (q > 0f && q != r) {
                r = q;
                out.quantized = true;
            }
        }

        out.radius = r;

        // Z-fit: keep stable, padded, with minimum span.
        // In the minimal version we use sphere depth and optional caster bounds if provided.
        float z0 = -r; // relative along light forward axis
        float z1 = +r;

        if (!Float.isNaN(casterMinZ) && !Float.isNaN(casterMaxZ) && casterMaxZ > casterMinZ) {
            z0 = Math.min(z0, casterMinZ);
            z1 = Math.max(z1, casterMaxZ);
        }

        // Expand by padding
        z0 -= cfg.zPadding;
        z1 += cfg.zPadding;

        // Enforce minimum span
        float span = z1 - z0;
        if (span < cfg.minZSpan) {
            float mid = 0.5f * (z0 + z1);
            float half = 0.5f * cfg.minZSpan;
            z0 = mid - half;
            z1 = mid + half;
        }

        out.zNear = z0;
        out.zFar = z1;
        return out;
    }

    public static final class FitCfg {
        /**
         * Ensures ortho extents are >= 1.0 and stable.
         */
        public float extentsPadding = 1.10f; // >= 1
        /**
         * Quantization step in world units (usually texelWorldSize * k). 0 disables.
         */
        public float radiusQuantStep = 0.0f;
        /**
         * Extra padding along light Z (depth).
         */
        public float zPadding = 25.0f;
        /**
         * Minimum Z span to prevent near/far collapse.
         */
        public float minZSpan = 50.0f;

        /**
         * Back offset in radii (push light camera back).
         */
        public float backOffsetRadii = 1.10f;

        public FitCfg() {
        }
    }

    public static final class FitOut {
        /**
         * Light-space center in world space (anchor point).
         */
        public final Vector3f centerWS = new Vector3f();
        /**
         * Ortho half-width (world units). Square extents: [-r..r].
         */
        public float radius;
        /**
         * Light camera near/far in light-view direction (world units).
         */
        public float zNear;
        public float zFar;

        /**
         * Debug info.
         */
        public boolean quantized;
    }
}