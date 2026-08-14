/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Arrays;

public final class ShadowRenderConfig {
    private final Cascades cascades = new Cascades();
    private final Fitting fitting = new Fitting();
    private final Snapping snapping = new Snapping();
    private final Debug debug = new Debug();

    public static float[] sortedCloneOrNull(float[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }
        float[] copy = (float[])arr.clone();
        Arrays.sort(copy);
        return copy;
    }

    public static ShadowRenderConfig cdpr8192() {
        ShadowRenderConfig c = new ShadowRenderConfig();
        c.cascades().setHysteresisEnabled(true).setSplitHysteresis(0.12f).setSplitSmoothing(0.65f).setFixedSplits(null);
        c.fitting().setMode(FitMode.TIGHT_STABLE).setMinNear(0.5f).setCasterBackBase(0.1f).setCasterBackCascadeMul(0.55f).setReceiverFrontBase(0.0f).setXyPadding(1.12f).setForceSquare(true).setSizeQuantizeTexels(16).setLockNearCascadeSize(true).setNearTierTexels(2048).setNearShrinkHysteresisTiers(2);
        c.snapping().setEnabled(true).setSnapFirstCascades(2);
        c.snapping().temporalGate().setEnabled(true).setMinMoveTexels(0.75f).setMinRotateDeg(0.2f).setGatedFirstCascades(2);
        c.debug().setTraceEnabled(false).setTraceEveryFrames(60);
        return c;
    }

    private static long fnv64Init() {
        return 1469598103934665603L;
    }

    private static long mix(long h, int v) {
        h ^= (long)v & 0xFFFFFFFFL;
        return h *= 1099511628211L;
    }

    private static long mix(long h, long v) {
        h = ShadowRenderConfig.mix(h, (int)(v & 0xFFFFFFFFL));
        h = ShadowRenderConfig.mix(h, (int)(v >>> 32 & 0xFFFFFFFFL));
        return h;
    }

    private static long arraySignature(float[] arr) {
        if (arr == null) {
            return 0L;
        }
        long h = ShadowRenderConfig.fnv64Init();
        h = ShadowRenderConfig.mix(h, arr.length);
        for (float f : arr) {
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(f));
        }
        return h;
    }

    public Cascades cascades() {
        return this.cascades;
    }

    public Fitting fitting() {
        return this.fitting;
    }

    public Snapping snapping() {
        return this.snapping;
    }

    public Debug debug() {
        return this.debug;
    }

    public long signature() {
        long h = ShadowRenderConfig.fnv64Init();
        h = ShadowRenderConfig.mix(h, this.cascades.signature());
        h = ShadowRenderConfig.mix(h, this.fitting.signature());
        h = ShadowRenderConfig.mix(h, this.snapping.signature());
        h = ShadowRenderConfig.mix(h, this.debug.signature());
        return h;
    }

    public static final class Cascades {
        private boolean hysteresisEnabled = true;
        private float splitHysteresis = 0.1f;
        private float splitSmoothing = 0.6f;
        private float[] fixedSplits = null;

        public boolean isHysteresisEnabled() {
            return this.hysteresisEnabled;
        }

        public Cascades setHysteresisEnabled(boolean hysteresisEnabled) {
            this.hysteresisEnabled = hysteresisEnabled;
            return this;
        }

        public float getSplitHysteresis() {
            return this.splitHysteresis;
        }

        public Cascades setSplitHysteresis(float splitHysteresis) {
            this.splitHysteresis = splitHysteresis;
            return this;
        }

        public float getSplitSmoothing() {
            return this.splitSmoothing;
        }

        public Cascades setSplitSmoothing(float splitSmoothing) {
            this.splitSmoothing = splitSmoothing;
            return this;
        }

        public float[] getFixedSplits() {
            return this.fixedSplits;
        }

        public Cascades setFixedSplits(float[] fixedSplits) {
            this.fixedSplits = fixedSplits == null ? null : (float[])fixedSplits.clone();
            return this;
        }

        long signature() {
            long h = ShadowRenderConfig.fnv64Init();
            h = ShadowRenderConfig.mix(h, this.hysteresisEnabled ? 1 : 0);
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.splitHysteresis));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.splitSmoothing));
            h = ShadowRenderConfig.mix(h, ShadowRenderConfig.arraySignature(this.fixedSplits));
            return h;
        }
    }

    public static final class Fitting {
        private FitMode mode = FitMode.TIGHT_STABLE;
        private float minNear = 0.5f;
        private float casterBackBase = 0.1f;
        private float casterBackCascadeMul = 0.55f;
        private float receiverFrontBase = 0.0f;
        private float xyPadding = 1.12f;
        private boolean forceSquare = true;
        private int sizeQuantizeTexels = 16;
        private boolean lockNearCascadeSize = true;
        private int nearTierTexels = 2048;
        private int nearShrinkHysteresisTiers = 2;

        public FitMode getMode() {
            return this.mode;
        }

        public Fitting setMode(FitMode mode) {
            this.mode = mode == null ? FitMode.TIGHT_STABLE : mode;
            return this;
        }

        public float getMinNear() {
            return this.minNear;
        }

        public Fitting setMinNear(float minNear) {
            this.minNear = minNear;
            return this;
        }

        public float getCasterBackBase() {
            return this.casterBackBase;
        }

        public Fitting setCasterBackBase(float casterBackBase) {
            this.casterBackBase = casterBackBase;
            return this;
        }

        public float getCasterBackCascadeMul() {
            return this.casterBackCascadeMul;
        }

        public Fitting setCasterBackCascadeMul(float casterBackCascadeMul) {
            this.casterBackCascadeMul = casterBackCascadeMul;
            return this;
        }

        public float getReceiverFrontBase() {
            return this.receiverFrontBase;
        }

        public Fitting setReceiverFrontBase(float receiverFrontBase) {
            this.receiverFrontBase = receiverFrontBase;
            return this;
        }

        public float getXyPadding() {
            return this.xyPadding;
        }

        public Fitting setXyPadding(float xyPadding) {
            this.xyPadding = xyPadding;
            return this;
        }

        public boolean isForceSquare() {
            return this.forceSquare;
        }

        public Fitting setForceSquare(boolean forceSquare) {
            this.forceSquare = forceSquare;
            return this;
        }

        public int getSizeQuantizeTexels() {
            return this.sizeQuantizeTexels;
        }

        public Fitting setSizeQuantizeTexels(int sizeQuantizeTexels) {
            this.sizeQuantizeTexels = sizeQuantizeTexels;
            return this;
        }

        public boolean isLockNearCascadeSize() {
            return this.lockNearCascadeSize;
        }

        public Fitting setLockNearCascadeSize(boolean lockNearCascadeSize) {
            this.lockNearCascadeSize = lockNearCascadeSize;
            return this;
        }

        public int getNearTierTexels() {
            return this.nearTierTexels;
        }

        public Fitting setNearTierTexels(int nearTierTexels) {
            this.nearTierTexels = nearTierTexels;
            return this;
        }

        public int getNearShrinkHysteresisTiers() {
            return this.nearShrinkHysteresisTiers;
        }

        public Fitting setNearShrinkHysteresisTiers(int nearShrinkHysteresisTiers) {
            this.nearShrinkHysteresisTiers = nearShrinkHysteresisTiers;
            return this;
        }

        long signature() {
            long h = ShadowRenderConfig.fnv64Init();
            h = ShadowRenderConfig.mix(h, this.mode.ordinal());
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.minNear));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.casterBackBase));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.casterBackCascadeMul));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.receiverFrontBase));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.xyPadding));
            h = ShadowRenderConfig.mix(h, this.forceSquare ? 1 : 0);
            h = ShadowRenderConfig.mix(h, this.sizeQuantizeTexels);
            h = ShadowRenderConfig.mix(h, this.lockNearCascadeSize ? 1 : 0);
            h = ShadowRenderConfig.mix(h, this.nearTierTexels);
            h = ShadowRenderConfig.mix(h, this.nearShrinkHysteresisTiers);
            return h;
        }
    }

    public static final class Snapping {
        private final TemporalGate temporalGate = new TemporalGate();
        private boolean enabled = true;
        private int snapFirstCascades = 2;

        public boolean isEnabled() {
            return this.enabled;
        }

        public Snapping setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public int getSnapFirstCascades() {
            return this.snapFirstCascades;
        }

        public Snapping setSnapFirstCascades(int snapFirstCascades) {
            this.snapFirstCascades = snapFirstCascades;
            return this;
        }

        public TemporalGate temporalGate() {
            return this.temporalGate;
        }

        long signature() {
            long h = ShadowRenderConfig.fnv64Init();
            h = ShadowRenderConfig.mix(h, this.enabled ? 1 : 0);
            h = ShadowRenderConfig.mix(h, this.snapFirstCascades);
            h = ShadowRenderConfig.mix(h, this.temporalGate.signature());
            return h;
        }
    }

    public static final class Debug {
        private boolean traceEnabled = true;
        private int traceEveryFrames = 60;

        public boolean isTraceEnabled() {
            return this.traceEnabled;
        }

        public Debug setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
            return this;
        }

        public int getTraceEveryFrames() {
            return this.traceEveryFrames;
        }

        public Debug setTraceEveryFrames(int traceEveryFrames) {
            this.traceEveryFrames = traceEveryFrames;
            return this;
        }

        long signature() {
            long h = ShadowRenderConfig.fnv64Init();
            h = ShadowRenderConfig.mix(h, this.traceEnabled ? 1 : 0);
            h = ShadowRenderConfig.mix(h, this.traceEveryFrames);
            return h;
        }
    }

    public static enum FitMode {
        TIGHT_STABLE,
        STABLE_AABB;

    }

    public static final class TemporalGate {
        private boolean enabled = true;
        private float minMoveTexels = 0.75f;
        private float minRotateDeg = 0.2f;
        private int gatedFirstCascades = 2;

        public boolean isEnabled() {
            return this.enabled;
        }

        public TemporalGate setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public float getMinMoveTexels() {
            return this.minMoveTexels;
        }

        public TemporalGate setMinMoveTexels(float minMoveTexels) {
            this.minMoveTexels = minMoveTexels;
            return this;
        }

        public float getMinRotateDeg() {
            return this.minRotateDeg;
        }

        public TemporalGate setMinRotateDeg(float minRotateDeg) {
            this.minRotateDeg = minRotateDeg;
            return this;
        }

        public int getGatedFirstCascades() {
            return this.gatedFirstCascades;
        }

        public TemporalGate setGatedFirstCascades(int gatedFirstCascades) {
            this.gatedFirstCascades = gatedFirstCascades;
            return this;
        }

        long signature() {
            long h = ShadowRenderConfig.fnv64Init();
            h = ShadowRenderConfig.mix(h, this.enabled ? 1 : 0);
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.minMoveTexels));
            h = ShadowRenderConfig.mix(h, Float.floatToIntBits(this.minRotateDeg));
            h = ShadowRenderConfig.mix(h, this.gatedFirstCascades);
            return h;
        }
    }
}

