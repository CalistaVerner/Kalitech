/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowRendererConfigurator;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowRendererSettings;

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
        this.initialized = false;
        this.lastStructuralSig = 0L;
        this.lastDynamicSig = 0L;
        this.lastPipelineSig = 0L;
        log.debug("[shadow][cfg] invalidate");
    }

    public Plan plan(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        boolean rendererStructuralMismatch;
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");
        settings.validate();
        long structural = settings.structuralSignature();
        long dynamic = settings.dynamicSignature();
        long pipeline = settings.pipelineSignature();
        int wantMap = settings.getShadowMapSize();
        int wantCascades = settings.getCascades();
        boolean bl = rendererStructuralMismatch = renderer.getShadowMapSize() != wantMap || renderer.getNumShadowMaps() != wantCascades;
        if (!this.initialized) {
            return new Plan(true, rendererStructuralMismatch, true, true, wantMap, wantCascades);
        }
        boolean structuralChanged = structural != this.lastStructuralSig;
        boolean dynamicChanged = dynamic != this.lastDynamicSig;
        boolean pipelineChanged = pipeline != this.lastPipelineSig;
        boolean recreateRequired = structuralChanged || rendererStructuralMismatch;
        boolean anyApply = dynamicChanged || pipelineChanged;
        return new Plan(anyApply, recreateRequired, dynamicChanged, pipelineChanged, wantMap, wantCascades);
    }

    public Plan apply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        Plan p = this.plan(renderer, settings);
        if (p.recreateRequired) {
            if (!this.initialized) {
                this.cache(settings);
            }
            log.debug("[shadow][cfg] plan=recreate map={} cascades={} dyn={} pipe={}", (Object)p.desiredMapSize, (Object)p.desiredCascades, (Object)p.dynamicChanged, (Object)p.pipelineChanged);
            return p;
        }
        if (!p.applied) {
            log.trace("[shadow][cfg] noop");
            return p;
        }
        boolean trace = settings.pipeline().debug().isTraceEnabled();
        if (p.dynamicChanged) {
            ShadowConfigTask.applyDynamic(renderer, settings);
            if (trace) {
                log.trace("[shadow][cfg] apply=dynamic lambda={} intensity={} zFarOverride={}", (Object)Float.valueOf(settings.getLambda()), (Object)Float.valueOf(settings.getIntensity()), (Object)Float.valueOf(settings.getZFarOverride()));
            }
        }
        if (p.pipelineChanged) {
            ShadowRendererConfigurator.apply(renderer, settings.pipeline());
            if (trace) {
                log.trace("[shadow][cfg] apply=pipeline sig={}", (Object)settings.pipelineSignature());
            }
        }
        this.cache(settings);
        log.debug("[shadow][cfg] applied dyn={} pipe={}", (Object)p.dynamicChanged, (Object)p.pipelineChanged);
        return p;
    }

    public Plan forceApply(PipelineDirectionalLightShadowRenderer renderer, ShadowRendererSettings settings) {
        this.invalidate();
        return this.apply(renderer, settings);
    }

    private void cache(ShadowRendererSettings settings) {
        this.initialized = true;
        this.lastStructuralSig = settings.structuralSignature();
        this.lastDynamicSig = settings.dynamicSignature();
        this.lastPipelineSig = settings.pipelineSignature();
    }

    public static final class Plan {
        private final boolean applied;
        private final boolean recreateRequired;
        private final boolean dynamicChanged;
        private final boolean pipelineChanged;
        private final int desiredMapSize;
        private final int desiredCascades;

        private Plan(boolean applied, boolean recreateRequired, boolean dynamicChanged, boolean pipelineChanged, int desiredMapSize, int desiredCascades) {
            this.applied = applied;
            this.recreateRequired = recreateRequired;
            this.dynamicChanged = dynamicChanged;
            this.pipelineChanged = pipelineChanged;
            this.desiredMapSize = desiredMapSize;
            this.desiredCascades = desiredCascades;
        }

        public boolean isApplied() {
            return this.applied;
        }

        public boolean isRecreateRequired() {
            return this.recreateRequired;
        }

        public boolean isDynamicChanged() {
            return this.dynamicChanged;
        }

        public boolean isPipelineChanged() {
            return this.pipelineChanged;
        }

        public int getDesiredMapSize() {
            return this.desiredMapSize;
        }

        public int getDesiredCascades() {
            return this.desiredCascades;
        }
    }
}

