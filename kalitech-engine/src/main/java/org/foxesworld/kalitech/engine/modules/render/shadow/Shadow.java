// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.*;

public final class Shadow {

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;

    private int mapSize = 8192;
    private int splits = 4;
    private float lambda = 0.72f;
    private float intensity = 0.75f;
    private float shadowZExtend = 1000f;
    private float shadowZFadeLength = 0f;

    private boolean enabled = true;

    // knobs
    private boolean snapEnabled = true;
    private int snapFirstCascades = 2;
    private float extentsPadding = 1.02f;

    // anti-popping
    private float splitHysteresis = 10.0f;
    private float splitSmoothing = 0.10f;

    // PCSS toggle (wired later via material/shader filter)
    private boolean usePcss = false;

    public Shadow(RenderThread thread, SimpleApplication app, AssetManager assets, Logger log, LightRigModule lights) {
        if (thread == null) throw new IllegalArgumentException("thread is null");
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        if (lights == null) throw new IllegalArgumentException("lights is null");

        this.thread = thread;
        this.app = app;
        this.assets = assets;
        this.log = log;
        this.lights = lights;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        rebuild();
    }

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = mapSize;
        this.splits = splits;
        this.lambda = lambda;
        this.intensity = intensity;
        rebuild();
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        rebuild();
    }

    public void setSnapFirstCascades(int count) {
        this.snapFirstCascades = Math.max(0, Math.min(4, count));
        rebuild();
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        rebuild();
    }

    public void setSplitHysteresis(float hysteresis) {
        this.splitHysteresis = Math.max(0f, hysteresis);
        rebuild();
    }

    public void setSplitSmoothing(float smoothing) {
        this.splitSmoothing = Math.max(0f, Math.min(1f, smoothing));
        rebuild();
    }

    public void setUsePcss(boolean enabled) {
        this.usePcss = enabled;
        rebuild();
    }

    private ViewPort vp() {
        return app.getViewPort();
    }

    private void detachOld() {
        if (dlsr == null) return;

        ViewPort vp = vp();
        try {
            vp.removeProcessor(dlsr);
        } catch (Throwable t) {
            log.warn("[shadow] removeProcessor failed: {}", t.toString());
        }
        dlsr = null;
    }

    public void rebuild() {
        thread.onJme(() -> {
            ViewPort vp = vp();

            detachOld();

            if (!enabled) {
                log.info("[shadow] disabled");
                return;
            }

            if (lights.primaryLight() == null) {
                log.warn("[shadow] cannot enable: primary directional light is null");
                return;
            }

            // Do not start with 8192, stabilize first.
            if (mapSize > 4096) {
                mapSize = 4096;
            }

            if (lambda <= 0f) lambda = 0.72f;
            if (shadowZExtend <= 0f) shadowZExtend = 1000f;
            if (extentsPadding < 1.0f) extentsPadding = 1.02f;

            if (snapFirstCascades <= 0) snapFirstCascades = 1;
            if (snapFirstCascades > 1) snapFirstCascades = 1;

            PipelineDirectionalLightShadowRenderer r =
                    new PipelineDirectionalLightShadowRenderer(assets, mapSize, splits);

            CascadeHysteresisFilter hyst = new CascadeHysteresisFilter();
            hyst.hysteresis = (splitHysteresis <= 0f) ? 10.0f : splitHysteresis;
            hyst.smoothing = (splitSmoothing <= 0f) ? 0.10f : splitSmoothing;

            StableLightBasisFilter basis = new StableLightBasisFilter();

            TightStableFitShadowCamFilter fit = new TightStableFitShadowCamFilter();
            fit.xyPadding = extentsPadding;
            fit.forceSquare = true;
            fit.sizeQuantizeTexels = 1.0f;
            fit.minNear = 0.5f;
            fit.casterBackBase = 140f;
            fit.casterBackCascadeMul = 0.9f;
            fit.receiverFrontBase = 40f;

            TemporalSnapGateFilter gate = new TemporalSnapGateFilter();
            gate.minRotateDeg = 0.25f;
            gate.minMoveTexels = 1.25f;
            gate.gatedFirstCascades = 1;

            TexelSnapFilter snap = new TexelSnapFilter();
            snap.enabled = snapEnabled;
            snap.snapFirstCascades = snapFirstCascades;

            // CRITICAL: wire gate into snap
            snap.gate = gate;

            r.setFixedSplitDistances(1f, 25f, 80f, 220f, 1000f);

            //TexelSnapFilter snap = new TexelSnapFilter();
            snap.enabled = false;


            r.pipeline()
                    .add(hyst)
                    .add(basis)
                    .add(fit)
                    .add(gate)
                    .add(snap);

            r.setLight(lights.primaryLight());
            r.setLambda(lambda);
            r.setShadowIntensity(intensity);
            r.setShadowZExtend(shadowZExtend);
            r.setShadowZFadeLength(shadowZFadeLength);
            r.pipeline().add(new ShadowTraceFilter());


            dlsr = r;

            if (!vp.getProcessors().contains(dlsr)) {
                vp.addProcessor(dlsr);
            }

            log.info(
                    "[shadow] enabled type={} map={} splits={} lambda={} intensity={} snap={} snapCascades={} pad={} hyst={} smooth={} zExtend={} zFade={} gateRotate={} gateMoveTex={}",
                    dlsr.getClass().getSimpleName(),
                    mapSize, splits, lambda, intensity,
                    snapEnabled, snap.snapFirstCascades,
                    fit.xyPadding,
                    hyst.hysteresis, hyst.smoothing,
                    shadowZExtend, shadowZFadeLength,
                    gate.minRotateDeg, gate.minMoveTexels
            );
        });
    }


    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    public void setShadowZExtend(float zExtend) {
        this.shadowZExtend = Math.max(0f, zExtend);
        if (dlsr != null) dlsr.setShadowZExtend(this.shadowZExtend);
        log.info("[shadow] zExtend={}", this.shadowZExtend);
    }

    public void setShadowZFadeLength(float zFadeLength) {
        this.shadowZFadeLength = Math.max(0f, zFadeLength);
        if (dlsr != null) dlsr.setShadowZFadeLength(this.shadowZFadeLength);
        log.info("[shadow] zFadeLength={}", this.shadowZFadeLength);
    }

    public void onPrimaryLightChanged() {
        thread.onJme(() -> {
            if (dlsr == null) return;
            if (lights.primaryLight() == null) {
                log.warn("[shadow] primary light became null => shadows will stop");
                return;
            }
            dlsr.setLight(lights.primaryLight());
            log.info("[shadow] primary light updated => {}", lights.primaryDirectional());
        });
    }
}