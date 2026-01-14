// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowConfigTask.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;

import java.util.Objects;

/**
 * Centralized deterministic configuration task for the shadow renderer.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Validate and normalize settings</li>
 *   <li>Detect changes via stable signatures</li>
 *   <li>Apply only what actually changed (dynamic vs pipeline)</li>
 *   <li>Report when structural changes require a renderer recreation</li>
 * </ul>
 *
 * <p>Structural changes: map size or cascade count. These require recreating the renderer instance.</p>
 * <p>Dynamic changes: lambda/intensity/zFarOverride.</p>
 * <p>Pipeline changes: stability/snapping/fitting/hysteresis/debug filters.</p>
 */
public final class ShadowConfigTask {

    private boolean initialized;

    private long lastFullSig;
    private long lastPipelineSig;
    private int lastDynamicSig;

    private static int dynSig(ShadowRendererSettings s) {
        int h = 0x811c9dc5;
        h = mix(h, Float.floatToIntBits(s.getLambda()));
        h = mix(h, Float.floatToIntBits(s.getIntensity()));
        h = mix(h, Float.floatToIntBits(s.getZFarOverride()));
        return h;
    }

    private static int mix(int h, int v) {
        h ^= v;
        h *= 16777619;
        return h;
    }

    private static void applyDynamic(PipelineDirectionalLightShadowRenderer r, ShadowRendererSettings s) {
        r.setLambda(s.getLambda());
        r.setShadowIntensity(s.getIntensity());
        r.setZFarOverride(s.getZFarOverride());
    }

    /**
     * Invalidates cached signatures so the next apply will run.
     * Useful for hot reload / forced rebuild scenarios.
     */
    public void invalidate() {
        initialized = false;
        lastFullSig = 0L;
        lastPipelineSig = 0L;
        lastDynamicSig = 0;
    }

    /**
     * Applies settings to an existing renderer only if something changed.
     * <p>
     * Must be called from the render thread.
     */
    public Result applyIfChanged(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");

        settings.validate();

        final int wantMap = settings.getShadowMapSize();
        final int wantSplits = settings.getCascades();

        final boolean structuralMismatch =
                renderer.getShadowMapSize() != wantMap || renderer.getNumShadowMaps() != wantSplits;

        if (structuralMismatch) {
            return Result.recreateRequired(wantMap, wantSplits, Reason.STRUCTURAL_CHANGED);
        }

        final long fullSig = settings.signature();
        if (initialized && fullSig == lastFullSig) {
            return Result.noop(wantMap, wantSplits);
        }

        final long pipelineSig = settings.pipeline().signature();
        final int dynamicSig = dynSig(settings);

        final boolean rebuildPipeline = !initialized || pipelineSig != lastPipelineSig;
        final boolean applyDynamic = !initialized || dynamicSig != lastDynamicSig;

        if (applyDynamic) {
            applyDynamic(renderer, settings);
        }
        if (rebuildPipeline) {
            ShadowRendererConfigurator.apply(renderer, settings.pipeline());
        }

        initialized = true;
        lastFullSig = fullSig;
        lastPipelineSig = pipelineSig;
        lastDynamicSig = dynamicSig;

        return Result.applied(wantMap, wantSplits, applyDynamic, rebuildPipeline);
    }

    /**
     * Forces apply regardless of cached signature.
     * <p>
     * Must be called from the render thread.
     */
    public Result forceApply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        invalidate();
        return applyIfChanged(renderer, settings);
    }

    public enum Reason {
        NONE,
        STRUCTURAL_CHANGED
    }

    public static final class Result {
        private final boolean applied;
        private final boolean appliedDynamic;
        private final boolean rebuiltPipeline;
        private final boolean recreateRequired;
        private final int desiredMapSize;
        private final int desiredCascades;
        private final Reason reason;

        private Result(
                boolean applied,
                boolean appliedDynamic,
                boolean rebuiltPipeline,
                boolean recreateRequired,
                int desiredMapSize,
                int desiredCascades,
                Reason reason
        ) {
            this.applied = applied;
            this.appliedDynamic = appliedDynamic;
            this.rebuiltPipeline = rebuiltPipeline;
            this.recreateRequired = recreateRequired;
            this.desiredMapSize = desiredMapSize;
            this.desiredCascades = desiredCascades;
            this.reason = reason;
        }

        public static Result noop(int map, int cascades) {
            return new Result(false, false, false, false, map, cascades, Reason.NONE);
        }

        public static Result applied(int map, int cascades, boolean dyn, boolean pipe) {
            return new Result(true, dyn, pipe, false, map, cascades, Reason.NONE);
        }

        public static Result recreateRequired(int map, int cascades, Reason reason) {
            return new Result(false, false, false, true, map, cascades, reason);
        }

        public boolean isApplied() {
            return applied;
        }

        public boolean isAppliedDynamic() {
            return appliedDynamic;
        }

        public boolean isRebuiltPipeline() {
            return rebuiltPipeline;
        }

        public boolean isRecreateRequired() {
            return recreateRequired;
        }

        public int getDesiredMapSize() {
            return desiredMapSize;
        }

        public int getDesiredCascades() {
            return desiredCascades;
        }

        public Reason getReason() {
            return reason;
        }
    }
}