// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowRenderConfig.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Arrays;

/**
 * Pipeline configuration for shadow rendering.
 * <p>
 * This object controls stability, snapping, fitting, cascade behavior and debug.
 * It does not contain structural settings (map size / cascade count).
 */
public final class ShadowRenderConfig {

    private final Cascades cascades = new Cascades();
    private final Fitting fitting = new Fitting();
    private final Snapping snapping = new Snapping();
    private final Debug debug = new Debug();

    /**
     * Returns a sorted clone of the provided array or null.
     */
    public static float[] sortedCloneOrNull(float[] arr) {
        if (arr == null || arr.length == 0) return null;
        float[] copy = arr.clone();
        Arrays.sort(copy);
        return copy;
    }

    /**
     * CDPR-like stability preset tuned for large maps (e.g., 8192).
     */
    public static ShadowRenderConfig cdpr8192() {
        ShadowRenderConfig c = new ShadowRenderConfig();

        c.cascades()
                .setHysteresisEnabled(true)
                .setSplitHysteresis(0.12f)
                .setSplitSmoothing(0.65f)
                .setFixedSplits(null);

        c.fitting()
                .setMode(FitMode.TIGHT_STABLE)
                .setMinNear(0.50f)
                .setCasterBackBase(0.10f)
                .setCasterBackCascadeMul(0.55f)
                .setReceiverFrontBase(0.00f)
                .setXyPadding(1.12f)
                .setForceSquare(true)
                .setSizeQuantizeTexels(16)
                .setLockNearCascadeSize(true)
                .setNearTierTexels(2048)
                .setNearShrinkHysteresisTiers(2);

        c.snapping()
                .setEnabled(true)
                .setSnapFirstCascades(2);

        c.snapping().temporalGate()
                .setEnabled(true)
                .setMinMoveTexels(0.75f)
                .setMinRotateDeg(0.20f)
                .setGatedFirstCascades(2);

        c.debug()
                .setTraceEnabled(false)
                .setTraceEveryFrames(60);

        return c;
    }

    private static long fnv64Init() {
        return 1469598103934665603L;
    }

    private static long mix(long h, int v) {
        h ^= (v & 0xffffffffL);
        h *= 1099511628211L;
        return h;
    }

    private static long mix(long h, long v) {
        h = mix(h, (int) (v & 0xffffffffL));
        h = mix(h, (int) ((v >>> 32) & 0xffffffffL));
        return h;
    }

    private static long arraySignature(float[] arr) {
        if (arr == null) return 0L;
        long h = fnv64Init();
        h = mix(h, arr.length);
        for (float f : arr) {
            h = mix(h, Float.floatToIntBits(f));
        }
        return h;
    }

    public Cascades cascades() {
        return cascades;
    }

    public Fitting fitting() {
        return fitting;
    }

    // ---------------- Sections ----------------

    public Snapping snapping() {
        return snapping;
    }

    public Debug debug() {
        return debug;
    }

    /**
     * Computes a deterministic signature of the whole pipeline config.
     */
    public long signature() {
        long h = fnv64Init();
        h = mix(h, cascades.signature());
        h = mix(h, fitting.signature());
        h = mix(h, snapping.signature());
        h = mix(h, debug.signature());
        return h;
    }

    public enum FitMode {
        TIGHT_STABLE,
        STABLE_AABB
    }

    public static final class Cascades {
        private boolean hysteresisEnabled = true;
        private float splitHysteresis = 0.10f;
        private float splitSmoothing = 0.60f;
        private float[] fixedSplits = null;

        public boolean isHysteresisEnabled() {
            return hysteresisEnabled;
        }

        public Cascades setHysteresisEnabled(boolean hysteresisEnabled) {
            this.hysteresisEnabled = hysteresisEnabled;
            return this;
        }

        public float getSplitHysteresis() {
            return splitHysteresis;
        }

        public Cascades setSplitHysteresis(float splitHysteresis) {
            this.splitHysteresis = splitHysteresis;
            return this;
        }

        public float getSplitSmoothing() {
            return splitSmoothing;
        }

        public Cascades setSplitSmoothing(float splitSmoothing) {
            this.splitSmoothing = splitSmoothing;
            return this;
        }

        public float[] getFixedSplits() {
            return fixedSplits;
        }

        public Cascades setFixedSplits(float[] fixedSplits) {
            this.fixedSplits = fixedSplits == null ? null : fixedSplits.clone();
            return this;
        }

        long signature() {
            long h = fnv64Init();
            h = mix(h, hysteresisEnabled ? 1 : 0);
            h = mix(h, Float.floatToIntBits(splitHysteresis));
            h = mix(h, Float.floatToIntBits(splitSmoothing));
            h = mix(h, arraySignature(fixedSplits));
            return h;
        }
    }

    // ---------------- Hash utilities ----------------

    public static final class Fitting {
        private FitMode mode = FitMode.TIGHT_STABLE;

        private float minNear = 0.50f;

        private float casterBackBase = 0.10f;
        private float casterBackCascadeMul = 0.55f;
        private float receiverFrontBase = 0.00f;

        private float xyPadding = 1.12f;

        private boolean forceSquare = true;

        /**
         * Quantize orthographic size to N texels (e.g., 16) to reduce shimmering.
         * 0 disables quantization.
         */
        private int sizeQuantizeTexels = 16;

        /**
         * If true, keeps near cascade size "tier-locked" to avoid micro oscillations.
         */
        private boolean lockNearCascadeSize = true;

        /**
         * Near cascade target tier in texels (e.g., 2048 for 8192 map).
         */
        private int nearTierTexels = 2048;

        /**
         * How many tiers the near cascade may shrink before switching (hysteresis).
         */
        private int nearShrinkHysteresisTiers = 2;

        public FitMode getMode() {
            return mode;
        }

        public Fitting setMode(FitMode mode) {
            this.mode = mode == null ? FitMode.TIGHT_STABLE : mode;
            return this;
        }

        public float getMinNear() {
            return minNear;
        }

        public Fitting setMinNear(float minNear) {
            this.minNear = minNear;
            return this;
        }

        public float getCasterBackBase() {
            return casterBackBase;
        }

        public Fitting setCasterBackBase(float casterBackBase) {
            this.casterBackBase = casterBackBase;
            return this;
        }

        public float getCasterBackCascadeMul() {
            return casterBackCascadeMul;
        }

        public Fitting setCasterBackCascadeMul(float casterBackCascadeMul) {
            this.casterBackCascadeMul = casterBackCascadeMul;
            return this;
        }

        public float getReceiverFrontBase() {
            return receiverFrontBase;
        }

        public Fitting setReceiverFrontBase(float receiverFrontBase) {
            this.receiverFrontBase = receiverFrontBase;
            return this;
        }

        public float getXyPadding() {
            return xyPadding;
        }

        public Fitting setXyPadding(float xyPadding) {
            this.xyPadding = xyPadding;
            return this;
        }

        public boolean isForceSquare() {
            return forceSquare;
        }

        public Fitting setForceSquare(boolean forceSquare) {
            this.forceSquare = forceSquare;
            return this;
        }

        public int getSizeQuantizeTexels() {
            return sizeQuantizeTexels;
        }

        public Fitting setSizeQuantizeTexels(int sizeQuantizeTexels) {
            this.sizeQuantizeTexels = sizeQuantizeTexels;
            return this;
        }

        public boolean isLockNearCascadeSize() {
            return lockNearCascadeSize;
        }

        public Fitting setLockNearCascadeSize(boolean lockNearCascadeSize) {
            this.lockNearCascadeSize = lockNearCascadeSize;
            return this;
        }

        public int getNearTierTexels() {
            return nearTierTexels;
        }

        public Fitting setNearTierTexels(int nearTierTexels) {
            this.nearTierTexels = nearTierTexels;
            return this;
        }

        public int getNearShrinkHysteresisTiers() {
            return nearShrinkHysteresisTiers;
        }

        public Fitting setNearShrinkHysteresisTiers(int nearShrinkHysteresisTiers) {
            this.nearShrinkHysteresisTiers = nearShrinkHysteresisTiers;
            return this;
        }

        long signature() {
            long h = fnv64Init();
            h = mix(h, mode.ordinal());
            h = mix(h, Float.floatToIntBits(minNear));
            h = mix(h, Float.floatToIntBits(casterBackBase));
            h = mix(h, Float.floatToIntBits(casterBackCascadeMul));
            h = mix(h, Float.floatToIntBits(receiverFrontBase));
            h = mix(h, Float.floatToIntBits(xyPadding));
            h = mix(h, forceSquare ? 1 : 0);
            h = mix(h, sizeQuantizeTexels);
            h = mix(h, lockNearCascadeSize ? 1 : 0);
            h = mix(h, nearTierTexels);
            h = mix(h, nearShrinkHysteresisTiers);
            return h;
        }
    }

    public static final class Snapping {
        private final TemporalGate temporalGate = new TemporalGate();
        private boolean enabled = true;
        private int snapFirstCascades = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public Snapping setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public int getSnapFirstCascades() {
            return snapFirstCascades;
        }

        public Snapping setSnapFirstCascades(int snapFirstCascades) {
            this.snapFirstCascades = snapFirstCascades;
            return this;
        }

        public TemporalGate temporalGate() {
            return temporalGate;
        }

        long signature() {
            long h = fnv64Init();
            h = mix(h, enabled ? 1 : 0);
            h = mix(h, snapFirstCascades);
            h = mix(h, temporalGate.signature());
            return h;
        }
    }

    public static final class TemporalGate {
        private boolean enabled = true;
        private float minMoveTexels = 0.75f;
        private float minRotateDeg = 0.20f;
        private int gatedFirstCascades = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public TemporalGate setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public float getMinMoveTexels() {
            return minMoveTexels;
        }

        public TemporalGate setMinMoveTexels(float minMoveTexels) {
            this.minMoveTexels = minMoveTexels;
            return this;
        }

        public float getMinRotateDeg() {
            return minRotateDeg;
        }

        public TemporalGate setMinRotateDeg(float minRotateDeg) {
            this.minRotateDeg = minRotateDeg;
            return this;
        }

        public int getGatedFirstCascades() {
            return gatedFirstCascades;
        }

        public TemporalGate setGatedFirstCascades(int gatedFirstCascades) {
            this.gatedFirstCascades = gatedFirstCascades;
            return this;
        }

        long signature() {
            long h = fnv64Init();
            h = mix(h, enabled ? 1 : 0);
            h = mix(h, Float.floatToIntBits(minMoveTexels));
            h = mix(h, Float.floatToIntBits(minRotateDeg));
            h = mix(h, gatedFirstCascades);
            return h;
        }
    }

    public static final class Debug {
        private boolean traceEnabled = false;
        private int traceEveryFrames = 60;

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public Debug setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
            return this;
        }

        public int getTraceEveryFrames() {
            return traceEveryFrames;
        }

        public Debug setTraceEveryFrames(int traceEveryFrames) {
            this.traceEveryFrames = traceEveryFrames;
            return this;
        }

        long signature() {
            long h = fnv64Init();
            h = mix(h, traceEnabled ? 1 : 0);
            h = mix(h, traceEveryFrames);
            return h;
        }
    }
}