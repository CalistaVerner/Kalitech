// FILE: org/foxesworld/kalitech/engine/modules/render/shadows/SplitHysteresisManager.java
package org.foxesworld.kalitech.engine.modules.render.shadows;

/**
 * Stabilizes split distances to avoid "cascade boundary shimmer".
 * Works as: keep previous split until new value differs beyond threshold.
 */
public final class SplitHysteresisManager {

    private final Cfg cfg = new Cfg();
    private float[] prev; // far distances per split (or end distances)
    private boolean init;

    public Cfg cfg() {
        return cfg;
    }

    public void reset() {
        init = false;
        prev = null;
    }

    /**
     * @param wanted      wanted split distances (length = splits)
     * @param cameraSpeed world units per second (or per frame if you feed it consistently)
     * @return stabilized array (may be same instance as wanted if first time)
     */
    public float[] stabilize(float[] wanted, float cameraSpeed) {
        if (wanted == null || wanted.length == 0) return wanted;

        if (!init || prev == null || prev.length != wanted.length) {
            prev = wanted.clone();
            init = true;
            return prev;
        }

        float thr = cfg.baseThreshold + cfg.speedFactor * Math.max(0f, cameraSpeed);
        if (thr < cfg.minThreshold) thr = cfg.minThreshold;
        if (thr > cfg.maxThreshold) thr = cfg.maxThreshold;

        for (int i = 0; i < wanted.length; i++) {
            float w = wanted[i];
            float p = prev[i];

            // only update if moved enough
            if (Math.abs(w - p) >= thr) prev[i] = w;
        }

        return prev;
    }

    public static final class Cfg {
        /**
         * Base hysteresis in world units.
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
    }
}