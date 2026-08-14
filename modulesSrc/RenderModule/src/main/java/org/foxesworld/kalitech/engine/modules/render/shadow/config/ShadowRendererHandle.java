/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.light.DirectionalLight
 *  com.jme3.post.SceneProcessor
 *  com.jme3.renderer.RenderManager
 *  com.jme3.renderer.ViewPort
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.config;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowConfigTask;
import org.foxesworld.kalitech.engine.modules.render.shadow.config.ShadowRendererSettings;

public final class ShadowRendererHandle {
    private static final Logger log = LogManager.getLogger(ShadowRendererHandle.class);
    private final AssetManager assets;
    private final RenderManager renderManager;
    private final ViewPort viewPort;
    private final ShadowConfigTask configTask = new ShadowConfigTask();
    private final AtomicReference<Pending> pending = new AtomicReference();
    private PipelineDirectionalLightShadowRenderer renderer;
    private DirectionalLight light;

    public ShadowRendererHandle(AssetManager assets, RenderManager rm, ViewPort vp) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.renderManager = Objects.requireNonNull(rm, "renderManager");
        this.viewPort = Objects.requireNonNull(vp, "viewPort");
    }

    public void setLight(DirectionalLight light) {
        this.light = light;
        if (this.renderer != null) {
            this.renderer.setLight(light);
        }
        log.debug("[shadow][cfg] light=set {}", (Object)(light != null ? 1 : 0));
    }

    public PipelineDirectionalLightShadowRenderer getRenderer() {
        return this.renderer;
    }

    public void requestApply(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.pending.set(new Pending(settings, false));
        log.trace("[shadow][cfg] request=apply sig={}", (Object)settings.signature());
    }

    public void requestFullReload(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.pending.set(new Pending(settings, true));
        log.debug("[shadow][cfg] request=fullReload sig={}", (Object)settings.signature());
    }

    public void flushPending() {
        Pending p = this.pending.getAndSet(null);
        if (p == null) {
            return;
        }
        if (p.forceRecreate) {
            this.fullReloadNow(p.settings);
        } else {
            this.applyNow(p.settings);
        }
    }

    public void applyNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (this.renderer == null) {
            log.info("[shadow][cfg] create reason=missingRenderer map={} cascades={}", (Object)settings.getShadowMapSize(), (Object)settings.getCascades());
            this.createAndAttach(settings.getShadowMapSize(), settings.getCascades());
            this.configTask.forceApply(this.renderer, settings);
            return;
        }
        if (this.renderer.isDisposed() || this.renderer.isBroken()) {
            log.info("[shadow][cfg] recreate reason=rendererState disposed={} broken={} map={} cascades={}", (Object)this.renderer.isDisposed(), (Object)this.renderer.isBroken(), (Object)settings.getShadowMapSize(), (Object)settings.getCascades());
            this.recreate(settings.getShadowMapSize(), settings.getCascades());
            this.configTask.forceApply(this.renderer, settings);
            return;
        }
        ShadowConfigTask.Plan plan = this.configTask.apply(this.renderer, settings);
        if (!plan.isRecreateRequired()) {
            return;
        }
        log.info("[shadow][cfg] recreate reason=structuralChange map={} cascades={}", (Object)plan.getDesiredMapSize(), (Object)plan.getDesiredCascades());
        this.recreate(plan.getDesiredMapSize(), plan.getDesiredCascades());
        this.configTask.forceApply(this.renderer, settings);
    }

    public void fullReloadNow(ShadowRendererSettings settings) {
        Objects.requireNonNull(settings, "settings");
        log.info("[shadow][cfg] fullReload map={} cascades={}", (Object)settings.getShadowMapSize(), (Object)settings.getCascades());
        this.disposeInternal();
        this.createAndAttach(settings.getShadowMapSize(), settings.getCascades());
        this.configTask.invalidate();
        this.configTask.forceApply(this.renderer, settings);
    }

    public void dispose() {
        log.info("[shadow][cfg] dispose");
        this.disposeInternal();
        this.pending.set(null);
        this.configTask.invalidate();
    }

    private void disposeInternal() {
        if (this.renderer == null) {
            return;
        }
        this.viewPort.removeProcessor((SceneProcessor)this.renderer);
        this.renderer.destroy(this.renderManager);
        this.renderer = null;
    }

    private void recreate(int mapSize, int cascades) {
        this.disposeInternal();
        this.createAndAttach(mapSize, cascades);
    }

    private void createAndAttach(int mapSize, int cascades) {
        this.renderer = new PipelineDirectionalLightShadowRenderer(this.assets, mapSize, cascades);
        this.renderer.setLight(this.light);
        this.viewPort.addProcessor((SceneProcessor)this.renderer);
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

