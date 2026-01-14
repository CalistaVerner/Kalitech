// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;

public final class ShadowModule {

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;

    private int mapSize = 2048;
    private int splits = 4;
    private float lambda = 0.65f;
    private float intensity = 0.75f;

    private boolean enabled = true;

    // stable + debug knobs
    private boolean snapEnabled = true;
    private float extentsPadding = 1.05f;

    private boolean dbg = true;
    private int dbgEveryFrames = 60;

    // PCSS toggle
    private boolean usePcss = false;

    public ShadowModule(RenderThread thread, SimpleApplication app, AssetManager assets, Logger log, LightRigModule lights) {
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

    /*
    public void setUsePcss(boolean usePcss) {
        this.usePcss = usePcss;
        rebuild();
    } */

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (dlsr instanceof StableDirectionalLightShadowRenderer s) s.setSnapEnabled(enabled);
        log.info("[shadow] snapEnabled={}", enabled);
    }

    /*
    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        if (dlsr instanceof StableDirectionalLightShadowRenderer s) s.setExtentsPadding(this.extentsPadding);
        log.info("[shadow] extentsPadding={}", this.extentsPadding);
    }

    public void setDebug(boolean enabled, int everyFrames) {
        this.dbg = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
        if (dlsr instanceof StableDirectionalLightShadowRenderer s) {
            s.setDebugLogger(log);
            s.setDebugEnabled(dbg);
            s.setDebugEveryFrames(dbgEveryFrames);
        }
        log.info("[shadow] debug={} everyFrames={}", dbg, dbgEveryFrames);
    } */

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = mapSize;
        this.splits = splits;
        this.lambda = lambda;
        this.intensity = intensity;
        rebuild();
    }

    private ViewPort vp() {
        // IMPORTANT: make sure it's the MAIN viewport you render scene into
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
                log.warn("[shadow] cannot enable: primary directional light is null (LightRig not ready?)");
                return;
            }

            DirectionalLightShadowRenderer r;

            if (usePcss) {
                PcssDirectionalLightShadowRenderer p = new PcssDirectionalLightShadowRenderer(assets, mapSize, splits);
                p.setPcssDebug(log, dbg);
                // you can tune these from cfg later
                // p.setLightSize(...); p.setSearchSamples(...); p.setFilterSamples(...);
                r = p;
            } else {
                r = new StableDirectionalLightShadowRenderer(assets, mapSize, splits);
            }

            r.setLight(lights.primaryLight());
            r.setLambda(lambda);
            r.setShadowIntensity(intensity);

            if (r instanceof StableDirectionalLightShadowRenderer s) {
                s.setExtentsPadding(extentsPadding);
                s.setSnapEnabled(snapEnabled);

                s.setDebugLogger(log);
                s.setDebugEnabled(dbg);
                s.setDebugEveryFrames(dbgEveryFrames);

                // Bias defaults (tune later)
                s.setShadowBias(0.0008f);
                s.setShadowSlopeBias(2.0f);
                s.setShadowNormalOffset(0.0f);
            }

            dlsr = r;

            dlsr.setShadowZExtend(1500f);      // или сколько тебе надо
            dlsr.setShadowZFadeLength(0f);     // чтобы не было “плавного исчезновения”

            // HARD GUARANTEE: must be in viewport
            if (!vp.getProcessors().contains(dlsr)) {
                vp.addProcessor(dlsr);
            }

            log.info("[shadow] enabled type={} map={} splits={} lambda={} intensity={} snap={} pad={} vpProcessors={}",
                    dlsr.getClass().getSimpleName(),
                    mapSize, splits, lambda, intensity,
                    snapEnabled, extentsPadding,
                    vp.getProcessors().size());
        });
    }

    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    public void setUsePcss(boolean enabled) {
        this.usePcss = enabled;
        rebuild();
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        if (dlsr instanceof StableDirectionalLightShadowRenderer s) {
            s.setExtentsPadding(this.extentsPadding);
        }
        log.info("[shadow] extentsPadding={}", this.extentsPadding);
    }

    public void setDebug(boolean enabled, int everyFrames) {
        this.dbg = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
        if (dlsr instanceof StableDirectionalLightShadowRenderer s) {
            s.setDebugLogger(log);
            s.setDebugEnabled(dbg);
            s.setDebugEveryFrames(dbgEveryFrames);
        }
        log.info("[shadow] debug={} everyFrames={}", dbg, dbgEveryFrames);
    }


    /**
     * Call this if sun/moon switches so renderer uses correct light.
     */
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