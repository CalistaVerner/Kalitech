// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowRendererHandle.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Safe lifecycle controller for shadow renderer with deferred apply and forced hot reload.
 * <p>
 * Threading contract:
 * - requestApply/requestFullReload may be called from any thread.
 * - flushPending MUST be called on the render thread in a safe point.
 */
public final class ShadowRendererHandle {

    private static final Logger log = LogManager.getLogger(ShadowRendererHandle.class);

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final ViewPort viewPort;
    private final ShadowConfigTask configTask = new ShadowConfigTask();
    private final AtomicReference<Pending> pending = new AtomicReference<>();

    private PipelineDirectionalLightShadowRenderer renderer;
    private DirectionalLight light;

    public ShadowRendererHandle(AssetManager assets, RenderManager rm, ViewPort vp) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.renderManager = Objects.requireNonNull(rm, "renderManager");
        this.viewPort = Objects.requireNonNull(vp, "viewPort");
    }

    public void setLight(DirectionalLight light) {
        this.light = light;
        if (renderer != null) {
            renderer.setLight(light);
        }
        log.debug("[shadow][cfg] light=set {}", light != null);
    }

    public PipelineDirectionalLightShadowRenderer getRenderer() {
        return renderer;
    }

    public void requestApply(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        pending.set(new Pending(settings, false));
        log.trace("[shadow][cfg] request=apply sig={}", settings.signature());
    }

    public void requestFullReload(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        pending.set(new Pending(settings, true));
        log.debug("[shadow][cfg] request=fullReload sig={}", settings.signature());
    }

    public void flushPending() {
        Pending p = pending.getAndSet(null);
        if (p == null) return;

        if (p.forceRecreate) {
            fullReloadNow(p.settings);
        } else {
            applyNow(p.settings);
        }
    }

    public void applyNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");

        if (renderer == null) {
            log.info("[shadow][cfg] create reason=missingRenderer map={} cascades={}",
                    settings.getShadowMapSize(), settings.getCascades());
            createAndAttach(settings.getShadowMapSize(), settings.getCascades());
            configTask.forceApply(renderer, settings);
            return;
        }

        if (renderer.isDisposed() || renderer.isBroken()) {
            log.info("[shadow][cfg] recreate reason=rendererState disposed={} broken={} map={} cascades={}",
                    renderer.isDisposed(), renderer.isBroken(),
                    settings.getShadowMapSize(), settings.getCascades());
            recreate(settings.getShadowMapSize(), settings.getCascades());
            configTask.forceApply(renderer, settings);
            return;
        }

        ShadowConfigTask.Plan plan = configTask.apply(renderer, settings);
        if (!plan.isRecreateRequired()) {
            return;
        }

        log.info("[shadow][cfg] recreate reason=structuralChange map={} cascades={}",
                plan.getDesiredMapSize(), plan.getDesiredCascades());
        recreate(plan.getDesiredMapSize(), plan.getDesiredCascades());
        configTask.forceApply(renderer, settings);
    }

    public void fullReloadNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        log.info("[shadow][cfg] fullReload map={} cascades={}", settings.getShadowMapSize(), settings.getCascades());

        disposeInternal();

        createAndAttach(settings.getShadowMapSize(), settings.getCascades());

        configTask.invalidate();
        configTask.forceApply(renderer, settings);
    }

    public void dispose() {
        log.info("[shadow][cfg] dispose");
        disposeInternal();
        pending.set(null);
        configTask.invalidate();
    }

    private void disposeInternal() {
        if (renderer == null) return;

        viewPort.removeProcessor(renderer);
        renderer.destroy(renderManager);
        renderer = null;
    }

    private void recreate(int mapSize, int cascades) {
        disposeInternal();
        createAndAttach(mapSize, cascades);
    }

    private void createAndAttach(int mapSize, int cascades) {
        renderer = new PipelineDirectionalLightShadowRenderer(assets, mapSize, cascades);
        renderer.setLight(light);
        viewPort.addProcessor(renderer);
    }

    private static final class Pending {
        final ShadowRendererSettings settings;
        final boolean forceRecreate;

        Pending(ShadowRendererSettings settings, boolean forceRecreate) {
            this.settings = settings;
            this.forceRecreate = forceRecreate;
        }
    }
}