// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;

public final class ShadowModule {

    private static final int DEFAULT_SHADOW_SPLITS = 3;
    private static final float DEFAULT_SHADOW_LAMBDA = 0.65f;
    private static final float DEFAULT_SHADOW_INTENSITY = 0.65f;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;

    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;

    private int mapSize = -1;
    private int splits = -1;
    private float lambda = Float.NaN;
    private float intensity = Float.NaN;

    private boolean snapEnabled = true;

    /**
     * When {@code true}, use a PCSS (percentage closer soft shadows) renderer
     * instead of the default snapping renderer. PCSS produces contact‑hardening
     * soft shadows similar to those seen in AAA games. Note that enabling this
     * option requires an appropriate shader supporting PCSS uniforms. Defaults
     * to {@code false}.
     */
    private boolean usePcss = false;

    /**
     * Configures whether PCSS shadow rendering is used. When enabled, the
     * {@link PcssDirectionalLightShadowRenderer} is instantiated instead of
     * {@link SnappingDirectionalLightShadowRenderer}. Changing this flag
     * invalidates and recreates the shadow renderer the next time shadows are
     * enabled.
     *
     * @param use whether to enable PCSS soft shadows
     */
    public void setUsePcss(boolean use) {
        this.usePcss = use;
        // If a renderer exists, recreate it on the next enable call
        if (dlsr != null) {
            app.getViewPort().removeProcessor(dlsr);
            dlsr = null;
        }
    }

    public ShadowModule(SimpleApplication app, AssetManager assets, Logger log, LightRigModule lights) {
        this.app = app;
        this.assets = assets;
        this.log = log;
        this.lights = lights;
    }

    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (dlsr instanceof SnappingDirectionalLightShadowRenderer s) {
            s.setSnapEnabled(enabled);
        }
    }

    public void enableDefault(int mapSize) {
        enable(mapSize, DEFAULT_SHADOW_SPLITS, DEFAULT_SHADOW_LAMBDA, DEFAULT_SHADOW_INTENSITY);
    }

    public void enable(int mapSize, int splits, double lambda, double intensity) {
        lights.ensure();

        // disable shadows
        if (mapSize <= 0) {
            if (dlsr != null) {
                app.getViewPort().removeProcessor(dlsr);
                dlsr = null;
            }
            this.mapSize = 0;
            log.info("RenderApi: shadows disabled");
            return;
        }

        final int ms = Math.max(256, Math.min(mapSize, 8192));
        final int sp = Math.max(1, Math.min(splits, 4));
        final float lam = (float) Math.max(0.0, Math.min(lambda, 1.0));
        final float inten = (float) Math.max(0.0, Math.min(intensity, 1.0));

        // same topology -> just update params
        if (dlsr != null && this.mapSize == ms && this.splits == sp) {
            this.lambda = lam;
            this.intensity = inten;

            dlsr.setLight(lights.primaryLight());
            dlsr.setLambda(lam);
            dlsr.setShadowIntensity(inten);
            return;
        }

        // recreate
        if (dlsr != null) {
            app.getViewPort().removeProcessor(dlsr);
            dlsr = null;
        }

        this.mapSize = ms;
        this.splits = sp;
        this.lambda = lam;
        this.intensity = inten;

        // Choose renderer type based on PCSS flag
        if (usePcss) {
            PcssDirectionalLightShadowRenderer r =
                    new PcssDirectionalLightShadowRenderer(assets, ms, sp);
            r.setLight(lights.primaryLight());
            r.setLambda(lam);
            r.setShadowIntensity(inten);
            // Optional: tune PCSS parameters here or expose them externally
            dlsr = r;
        } else {
            SnappingDirectionalLightShadowRenderer r =
                    new SnappingDirectionalLightShadowRenderer(assets, ms, sp);
            r.setLight(lights.primaryLight());
            r.setLambda(lam);
            r.setShadowIntensity(inten);
            r.setSnapEnabled(snapEnabled);
            dlsr = r;
        }

        app.getViewPort().addProcessor(dlsr);

        log.info("RenderApi: shadows enabled mapSize={} splits={} lambda={} intensity={} primary={} snap={} pcss={}",
                ms, sp, lam, inten, lights.primaryDirectional(), snapEnabled, usePcss);
    }

    public void refreshPrimaryLightBinding() {
        if (dlsr == null) return;
        dlsr.setLight(lights.primaryLight());
    }
}