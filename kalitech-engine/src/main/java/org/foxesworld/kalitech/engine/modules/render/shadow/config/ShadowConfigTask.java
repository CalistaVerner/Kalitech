// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowConfigTask.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;

import java.util.Objects;

/**
 * Centralized configuration task for the shadow renderer.
 * Detects changes via signature and applies deterministically.
 */
public final class ShadowConfigTask {

    private long lastSignature = 0L;
    private boolean initialized = false;

    private static void applyDynamic(PipelineDirectionalLightShadowRenderer r, ShadowRendererSettings s) {
        r.setLambda(s.getLambda());
        r.setShadowIntensity(s.getIntensity());
        r.setZFarOverride(s.getZFarOverride());
    }

    /**
     * Invalidates the cached signature so the next apply will run.
     * Useful for hot reload / forced rebuild scenarios.
     */
    public void invalidate() {
        initialized = false;
        lastSignature = 0L;
    }

    public Result applyIfChanged(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");

        settings.validate();

        long sig = settings.signature();
        if (initialized && sig == lastSignature) {
            return new Result(false, false, renderer.getShadowMapSize(), renderer.getNumShadowMaps());
        }

        initialized = true;
        lastSignature = sig;

        int wantMap = settings.getShadowMapSize();
        int wantSplits = settings.getCascades();

        boolean recreate = renderer.getShadowMapSize() != wantMap || renderer.getNumShadowMaps() != wantSplits;
        if (recreate) {
            return new Result(false, true, wantMap, wantSplits);
        }

        applyDynamic(renderer, settings);
        ShadowRendererConfigurator.apply(renderer, settings.pipeline());

        return new Result(true, false, wantMap, wantSplits);
    }

    public Result forceApply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        invalidate();
        return applyIfChanged(renderer, settings);
    }

    public static final class Result {
        private final boolean applied;
        private final boolean recreateRequired;
        private final int desiredMapSize;
        private final int desiredCascades;

        private Result(boolean applied, boolean recreateRequired, int desiredMapSize, int desiredCascades) {
            this.applied = applied;
            this.recreateRequired = recreateRequired;
            this.desiredMapSize = desiredMapSize;
            this.desiredCascades = desiredCascades;
        }

        public boolean isApplied() {
            return applied;
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
    }
}