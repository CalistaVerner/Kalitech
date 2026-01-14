// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/SplitHysteresisManager.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

/**
 * Stabilizes shadow cascade split distances to reduce cascade-boundary shimmer.
 *
 * <p>Two-stage approach:
 * <ul>
 *   <li>Hysteresis (dead-zone): hold previous split until the wanted value moves far enough.</li>
 *   <li>Optional smoothing: when leaving the dead-zone, approach the wanted value smoothly (with optional rate limit).</li>
 * </ul>
 *
 * <p>Designed to be driven from a pipeline filter. No external/manual toggles are required:
 * if the filter is in the pipeline, stabilization is applied.</p>
 */
public final class SplitHysteresisManager {

    private final Cfg cfg = new Cfg();

    private float[] prev;
    private float[] vel; // only used for SMOOTH
    private boolean init;

    public Cfg cfg() {
        return cfg;
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public void reset() {
        init = false;
        prev = null;
        vel = null;
    }

    /**
     * Stabilize split distances.
     *
     * @param wanted      wanted split end distances (length = splits), ascending
     * @param cameraSpeed camera linear speed in world units per second (or consistent unit if dt is consistent)
     * @param dtSeconds   delta time in seconds (must be > 0 for SMOOTH mode; in HOLD mode can be 0)
     * @return stabilized distances (internal array, stable between calls)
     */
    public float[] stabilize(float[] wanted, float cameraSpeed, float dtSeconds) {
        if (wanted == null || wanted.length == 0) {
            return wanted;
        }

        if (!init || prev == null || prev.length != wanted.length) {
            ensureBuffers(wanted.length);
            System.arraycopy(wanted, 0, prev, 0, wanted.length);
            if (vel != null) {
                for (int i = 0; i < vel.length; i++) vel[i] = 0f;
            }
            init = true;
            return prev;
        }

        // Teleport / extreme jump handling (optional but very useful in practice)
        if (cfg.teleportEnabled) {
            float maxDelta = 0f;
            for (int i = 0; i < wanted.length; i++) {
                float d = Math.abs(wanted[i] - prev[i]);
                if (d > maxDelta) maxDelta = d;
            }
            if (cameraSpeed >= cfg.teleportSpeed || maxDelta >= cfg.teleportDistance) {
                System.arraycopy(wanted, 0, prev, 0, wanted.length);
                if (vel != null) {
                    for (int i = 0; i < vel.length; i++) vel[i] = 0f;
                }
                return prev;
            }
        }

        final float speed = Math.max(0f, cameraSpeed);
        final float thrBase = clamp(cfg.baseThreshold + cfg.speedFactor * speed, cfg.minThreshold, cfg.maxThreshold);

        final boolean smooth = (cfg.mode == Mode.SMOOTH);
        final float dt = (dtSeconds > 0f) ? dtSeconds : 0f;

        // Exponential smoothing coefficient (half-life model)
        final float alpha;
        if (smooth) {
            // Avoid division by zero and extreme values
            float halfLife = Math.max(1e-4f, cfg.smoothingHalfLifeSeconds);
            alpha = 1f - (float) Math.exp((-0.69314718056f) * (dt / halfLife));
        } else {
            alpha = 1f;
        }

        for (int i = 0; i < wanted.length; i++) {
            float w = wanted[i];
            float p = prev[i];

            // Relative threshold helps far cascades (percentage of distance)
            float thrRel = cfg.relativeThreshold * Math.max(cfg.relativeMinDistance, Math.abs(p));
            float thr = Math.max(thrBase, thrRel);

            float delta = (w - p);
            float ad = Math.abs(delta);

            // Dead-zone: keep previous
            if (ad < thr) {
                // If we smooth, we should also damp velocity so it doesn't "wake up" later
                if (vel != null && cfg.dampVelocityInDeadZone) vel[i] *= cfg.deadZoneVelocityDamping;
                continue;
            }

            if (!smooth || dt <= 0f) {
                // HOLD update: snap once outside dead-zone
                prev[i] = w;
                continue;
            }

            // SMOOTH update:
            // 1) First, compute target step by exponential smoothing.
            float next = p + delta * alpha;

            // 2) Optional rate limit (prevents boundary "pops" on abrupt wanted changes).
            if (cfg.maxChangePerSecond > 0f) {
                float maxStep = cfg.maxChangePerSecond * dt;
                float step = next - p;
                if (step > maxStep) step = maxStep;
                else if (step < -maxStep) step = -maxStep;
                next = p + step;
            }

            // 3) Optional soft clamp to never overshoot (shouldn't overshoot with alpha, but rate limit can).
            if (cfg.noOvershoot) {
                if ((delta > 0f && next > w) || (delta < 0f && next < w)) next = w;
            }

            prev[i] = next;
        }

        // Ensure monotonic ascending splits (defensive). Small numeric jitter can break ordering.
        if (cfg.enforceAscending) {
            for (int i = 1; i < prev.length; i++) {
                if (prev[i] < prev[i - 1] + cfg.minAscendingGap) {
                    prev[i] = prev[i - 1] + cfg.minAscendingGap;
                }
            }
        }

        return prev;
    }

    private void ensureBuffers(int n) {
        if (prev == null || prev.length != n) {
            prev = new float[n];
        }
        if (cfg.mode == Mode.SMOOTH) {
            if (vel == null || vel.length != n) vel = new float[n];
        } else {
            vel = null;
        }
    }

    public enum Mode {
        /**
         * Classic hysteresis: keep previous until wanted moves beyond threshold, then snap.
         */
        HOLD,
        /**
         * Hysteresis + smooth approach to wanted value once outside threshold.
         */
        SMOOTH
    }

    public static final class Cfg {
        /**
         * Stabilization mode.
         */
        public Mode mode = Mode.SMOOTH;

        /**
         * Base hysteresis threshold in world units.
         */
        public float baseThreshold = 0.75f;

        /**
         * Extra hysteresis per camera speed (world units per (unit speed)).
         */
        public float speedFactor = 0.15f;

        /**
         * Minimum threshold clamp.
         */
        public float minThreshold = 0.25f;

        /**
         * Maximum threshold clamp.
         */
        public float maxThreshold = 5.0f;

        /**
         * Relative hysteresis threshold as a fraction of current split distance.
         * Useful for far cascades (e.g. 0.002 means 0.2% of distance).
         */
        public float relativeThreshold = 0.0020f;

        /**
         * Minimum distance used for relative threshold calculation to avoid too small thresholds near zero.
         */
        public float relativeMinDistance = 25.0f;

        /**
         * Smoothing half-life in seconds (SMOOTH mode).
         * Smaller values follow wanted faster; larger values are more stable.
         */
        public float smoothingHalfLifeSeconds = 0.10f;

        /**
         * Maximum change rate (world units per second). 0 disables rate limit.
         * Helps avoid boundary pops on abrupt wanted changes.
         */
        public float maxChangePerSecond = 0.0f;

        /**
         * Prevent overshoot in smoothing.
         */
        public boolean noOvershoot = true;

        /**
         * Ensure stabilized splits remain ascending (defensive fix).
         */
        public boolean enforceAscending = true;

        /**
         * Minimum gap enforced when enforceAscending is enabled.
         */
        public float minAscendingGap = 0.001f;

        /**
         * If true, large speed or large delta triggers an immediate snap to wanted.
         */
        public boolean teleportEnabled = true;

        /**
         * If cameraSpeed >= teleportSpeed, stabilization snaps instantly.
         */
        public float teleportSpeed = 35.0f;

        /**
         * If any split changes by >= teleportDistance, stabilization snaps instantly.
         */
        public float teleportDistance = 50.0f;

        /**
         * In SMOOTH mode, damp velocity inside dead-zone to prevent latent "wake up".
         */
        public boolean dampVelocityInDeadZone = true;

        /**
         * Velocity damping factor applied each call inside dead-zone (0..1).
         */
        public float deadZoneVelocityDamping = 0.25f;
    }
}