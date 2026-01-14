// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/config/ShadowRendererHandle.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
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
    }

    public PipelineDirectionalLightShadowRenderer getRenderer() {
        return renderer;
    }

    /**
     * Schedule apply. If config signature did not change, nothing will happen.
     * May be called from any thread.
     */
    public void requestApply(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        pending.set(new Pending(settings, false));
    }

    /**
     * Schedule full hot reload: detach -> destroy -> create -> attach -> force apply.
     * Use this on script/F5 reload to guarantee shadows are recreated.
     * May be called from any thread.
     */
    public void requestFullReload(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        pending.set(new Pending(settings, true));
    }

    /**
     * Must be called on render thread in a safe point (beginning of frame/update).
     */
    public void flushPending() {
        Pending p = pending.getAndSet(null);
        if (p == null) return;

        if (p.forceRecreate) {
            fullReloadNow(p.settings);
        } else {
            applyNow(p.settings);
        }
    }

    /**
     * Render-thread only: applies settings, may recreate if structural changes require it.
     */
    public void applyNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");

        if (renderer == null || renderer.isDisposed() || renderer.isBroken()) {
            recreate(settings.getShadowMapSize(), settings.getCascades());
            configTask.forceApply(renderer, settings);
            return;
        }

        ShadowConfigTask.Result res = configTask.applyIfChanged(renderer, settings);
        if (!res.isRecreateRequired()) {
            return;
        }

        recreate(res.getDesiredMapSize(), res.getDesiredCascades());
        configTask.forceApply(renderer, settings);
    }

    /**
     * Render-thread only: guaranteed full reload regardless of signature.
     */
    public void fullReloadNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");

        // Always nuke the current renderer first (this is the "reset shadows" contract).
        disposeInternal();

        // Create new
        createAndAttach(settings.getShadowMapSize(), settings.getCascades());

        // Force apply regardless of signature cache
        configTask.invalidate();
        configTask.forceApply(renderer, settings);
    }

    /**
     * Render-thread only.
     */
    public void dispose() {
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