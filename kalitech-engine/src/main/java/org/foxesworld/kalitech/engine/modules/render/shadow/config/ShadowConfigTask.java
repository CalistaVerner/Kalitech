// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowConfigTask.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;

import java.util.Objects;

/**
 * Central configuration task for the shadow renderer.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Validate and normalize {@link ShadowRendererSettings}.</li>
 *   <li>Compute a deterministic change plan (structural/dynamic/pipeline).</li>
 *   <li>Apply dynamic and pipeline changes deterministically (never allocates a new renderer).</li>
 * </ul>
 * <p>
 * Lifecycle concerns (detach/destroy/create/attach) are intentionally handled by
 * {@link ShadowRendererHandle} so this class stays single-purpose.
 */
public final class ShadowConfigTask {

    private static final Logger log = LogManager.getLogger(ShadowConfigTask.class);

    private long lastStructuralSig;
    private long lastDynamicSig;
    private long lastPipelineSig;
    private boolean initialized;

    private static void applyDynamic(PipelineDirectionalLightShadowRenderer r, ShadowRendererSettings s) {
        r.setLambda(s.getLambda());
        r.setShadowIntensity(s.getIntensity());
    }

    public void invalidate() {
        initialized = false;
        lastStructuralSig = 0L;
        lastDynamicSig = 0L;
        lastPipelineSig = 0L;
        log.debug("[shadow][cfg] invalidate");
    }

    public Plan plan(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");

        settings.validate();

        long structural = settings.structuralSignature();
        long dynamic = settings.dynamicSignature();
        long pipeline = settings.pipelineSignature();

        int wantMap = settings.getShadowMapSize();
        int wantCascades = settings.getCascades();

        boolean rendererStructuralMismatch =
                renderer.getShadowMapSize() != wantMap || renderer.getNumShadowMaps() != wantCascades;

        if (!initialized) {
            return new Plan(true, rendererStructuralMismatch, true, true, wantMap, wantCascades);
        }

        boolean structuralChanged = structural != lastStructuralSig;
        boolean dynamicChanged = dynamic != lastDynamicSig;
        boolean pipelineChanged = pipeline != lastPipelineSig;

        boolean recreateRequired = structuralChanged || rendererStructuralMismatch;
        boolean anyApply = dynamicChanged || pipelineChanged;

        return new Plan(anyApply, recreateRequired, dynamicChanged, pipelineChanged, wantMap, wantCascades);
    }

    public Plan apply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        Plan p = plan(renderer, settings);

        if (p.recreateRequired) {
            if (!initialized) {
                cache(settings);
            }
            log.debug("[shadow][cfg] plan=recreate map={} cascades={} dyn={} pipe={}",
                    p.desiredMapSize, p.desiredCascades, p.dynamicChanged, p.pipelineChanged);
            return p;
        }

        if (!p.applied) {
            log.trace("[shadow][cfg] noop");
            return p;
        }

        final boolean trace = settings.pipeline().debug().isTraceEnabled();

        if (p.dynamicChanged) {
            applyDynamic(renderer, settings);
            if (trace) {
                log.trace("[shadow][cfg] apply=dynamic lambda={} intensity={} zFarOverride={}",
                        settings.getLambda(), settings.getIntensity(), settings.getZFarOverride());
            }
        }

        if (p.pipelineChanged) {
            ShadowRendererConfigurator.apply(renderer, settings.pipeline());
            if (trace) {
                log.trace("[shadow][cfg] apply=pipeline sig={}", settings.pipelineSignature());
            }
        }

        cache(settings);
        log.debug("[shadow][cfg] applied dyn={} pipe={}", p.dynamicChanged, p.pipelineChanged);
        return p;
    }

    public Plan forceApply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        invalidate();
        return apply(renderer, settings);
    }

    private void cache(ShadowRendererSettings settings) {
        initialized = true;
        lastStructuralSig = settings.structuralSignature();
        lastDynamicSig = settings.dynamicSignature();
        lastPipelineSig = settings.pipelineSignature();
    }

    public static final class Plan {
        private final boolean applied;
        private final boolean recreateRequired;
        private final boolean dynamicChanged;
        private final boolean pipelineChanged;
        private final int desiredMapSize;
        private final int desiredCascades;

        private Plan(boolean applied,
                     boolean recreateRequired,
                     boolean dynamicChanged,
                     boolean pipelineChanged,
                     int desiredMapSize,
                     int desiredCascades) {
            this.applied = applied;
            this.recreateRequired = recreateRequired;
            this.dynamicChanged = dynamicChanged;
            this.pipelineChanged = pipelineChanged;
            this.desiredMapSize = desiredMapSize;
            this.desiredCascades = desiredCascades;
        }

        public boolean isApplied() {
            return applied;
        }

        public boolean isRecreateRequired() {
            return recreateRequired;
        }

        public boolean isDynamicChanged() {
            return dynamicChanged;
        }

        public boolean isPipelineChanged() {
            return pipelineChanged;
        }

        public int getDesiredMapSize() {
            return desiredMapSize;
        }

        public int getDesiredCascades() {
            return desiredCascades;
        }
    }
}