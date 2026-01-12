// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Shadows module:
 * - owns DLSR lifecycle (create/recreate)
 * - owns primary light binding (sun/moon)
 * - owns snapping (ShadowSnapper) and exposes knobs for RenderApiImpl
 * - emits reason logs for shimmer root-cause (snap drift vs bias/PCF vs cascade transitions)
 * <p>
 * Author: Calista Verner
 */
public final class ShadowModule {

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final ViewportContract viewport;
    private final LightRigModule lights;

    // snapper knobs
    private final ShadowSnapper snapper = new ShadowSnapper();

    private int mapSize = -1;
    private int splits = -1;
    private float lambda = Float.NaN;
    private float intensity = Float.NaN;
    private SnappingDirectionalLightShadowRenderer dlsr;
    // snap toggles/knobs (cached so recreate keeps them)
    private boolean snapEnabled = true;
    private boolean stabilizeExtents = true;
    private float extentsPadding = 1.12f;
    // cascades depth stability knobs
    private float shadowZExtend = 2500f;
    private float shadowZFadeLength = 250f;

    // anti-shimmer bias knobs (best-effort: applied via reflection if supported by renderer)
    private float shadowBias = 0.0035f;
    private float shadowSlopeBias = 2.0f;
    private float shadowNormalOffset = 0.0f;
    private boolean biasWarned = false;

    // debug knobs
    private boolean debugEnabled = false;
    private int debugEveryFrames = 120;
    private int debugSnapIntervalMs = 500;

    public ShadowModule(SimpleApplication app, AssetManager assets, Logger log, ViewportContract viewport, LightRigModule lights) {
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        if (viewport == null) throw new IllegalArgumentException("viewport is null");
        if (lights == null) throw new IllegalArgumentException("lights is null");

        this.app = app;
        this.assets = assets;
        this.log = log;
        this.viewport = viewport;
        this.lights = lights;

        this.snapper.setLogger(log);
        this.snapper.setDebugEnabled(false);
        this.snapper.setDebugIntervalMs(this.debugSnapIntervalMs);
        this.snapper.setStabilizeExtents(this.stabilizeExtents);
        this.snapper.setExtentsPadding(this.extentsPadding);
    }

    // ------------------- public API (used by RenderApiImpl) -------------------

    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    private static boolean tryInvoke(Object target, String name, float v) {
        try {
            Method m = target.getClass().getMethod(name, float.class);
            m.invoke(target, v);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryInvoke(Object target, String name, double v) {
        try {
            Method m = target.getClass().getMethod(name, double.class);
            m.invoke(target, v);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int clampMapSize(int v) {
        if (v <= 0) return 0;
        if (v < 256) return 256;
        if (v > 16384) return 16384;
        return v;
    }

    private static int clampSplits(int v) {
        if (v < 1) return 1;
        if (v > 8) return 8;
        return v;
    }

    private static double clamp01(double v) {
        if (!(v >= 0.0)) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static boolean approx(float a, float b) {
        return Math.abs(a - b) <= 1e-6f;
    }

    private static String fmt3(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String fmt6(float v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (dlsr != null) dlsr.setSnapEnabled(enabled);
    }

    public void setStabilizeExtents(boolean stabilize) {
        this.stabilizeExtents = stabilize;
        snapper.setStabilizeExtents(stabilize);
        if (dlsr != null) dlsr.setStabilizeExtents(stabilize);
    }

    public void setExtentsPadding(double padding) {
        float p = (float) padding;
        if (!(p > 0f)) p = 1.12f;
        this.extentsPadding = p;
        snapper.setExtentsPadding(p);
        if (dlsr != null) dlsr.setExtentsPadding(p);
    }

    public void setShadowZExtend(double zExtend) {
        float z = (float) zExtend;
        if (!(z > 0f)) z = 2500f;
        this.shadowZExtend = z;
        if (dlsr != null) dlsr.setShadowZExtend(z);
    }

    public void setShadowZFadeLength(double fadeLen) {
        float f = (float) fadeLen;
        if (!(f >= 0f)) f = 250f;
        this.shadowZFadeLength = f;
        if (dlsr != null) dlsr.setShadowZFadeLength(f);
    }

    // ------------------- internals -------------------

    /**
     * Base shadow bias (helps shimmering/acne). Applied best-effort.
     */
    public void setShadowBias(double v) {
        float x = (float) v;
        if (!(x >= 0f)) x = 0f;
        this.shadowBias = x;
        if (dlsr != null) applyBiasKnobs(dlsr);
    }

    /**
     * Slope-scaled bias factor (helps steep angles). Applied best-effort.
     */
    public void setShadowSlopeBias(double v) {
        float x = (float) v;
        if (!(x >= 0f)) x = 0f;
        this.shadowSlopeBias = x;
        if (dlsr != null) applyBiasKnobs(dlsr);
    }

    /**
     * Normal offset (if supported by renderer). Applied best-effort.
     */
    public void setShadowNormalOffset(double v) {
        float x = (float) v;
        if (!(x >= 0f)) x = 0f;
        this.shadowNormalOffset = x;
        if (dlsr != null) applyBiasKnobs(dlsr);
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        snapper.setDebugEnabled(enabled);
        if (dlsr != null) dlsr.setDebugEnabled(enabled);
    }

    public void setDebugEveryFrames(int frames) {
        if (frames < 1) frames = 1;
        this.debugEveryFrames = frames;
        if (dlsr != null) dlsr.setDebugEveryFrames(frames);
    }

    public void setDebugSnapIntervalMs(int ms) {
        if (ms < 50) ms = 50;
        this.debugSnapIntervalMs = ms;
        snapper.setDebugIntervalMs(ms);
        if (dlsr != null) dlsr.setDebugSnapIntervalMs(ms);
    }

    /**
     * Create/recreate shadow renderer and attach to main viewport.
     * Must be called on JME thread.
     */
    public void enable(int mapSize, int splits, double lambda, double intensity) {
        final int m = clampMapSize(mapSize);
        if (m <= 0) {
            this.mapSize = 0;
            detachRenderer();
            return;
        }

        final int s = clampSplits(splits);
        final float l = (float) clamp01(lambda);
        final float i = (float) clamp01(intensity);

        final boolean same =
                (this.dlsr != null) &&
                        (this.mapSize == m) &&
                        (this.splits == s) &&
                        approx(this.lambda, l) &&
                        approx(this.intensity, i);

        this.mapSize = m;
        this.splits = s;
        this.lambda = l;
        this.intensity = i;

        if (!same) {
            detachRenderer();
            attachRenderer(m, s, l, i);
        } else {
            applyRuntimeKnobs();
        }

        refreshPrimaryLightBinding();
    }

    /**
     * Must be safe to call often, but should only rebind if light instance actually changed.
     * Must be called on JME thread.
     */
    public void refreshPrimaryLightBinding() {
        if (dlsr == null) return;

        final DirectionalLight light = lights.primaryLight();
        if (light == null) return;

        final DirectionalLight prev = dlsr.getLight();
        if (prev != light) {
            dlsr.setLight(light);
            if (log.isInfoEnabled()) log.info("[shadow] primary light rebound to {}", light);
        }
    }

    private void detachRenderer() {
        final SnappingDirectionalLightShadowRenderer r = this.dlsr;
        if (r == null) return;

        app.getViewPort().removeProcessor(r);
        this.dlsr = null;

        if (log.isInfoEnabled()) log.info("[shadow] renderer removed");
    }

    private void attachRenderer(int mapSize, int splits, float lambda, float intensity) {
        viewport.ensure("ShadowModule.attachRenderer");
        lights.ensure();

        final SnappingDirectionalLightShadowRenderer r =
                new SnappingDirectionalLightShadowRenderer(assets, mapSize, splits);

        r.setLambda(lambda);
        r.setShadowIntensity(intensity);

        // cascades depth stability
        r.setShadowZExtend(shadowZExtend);
        r.setShadowZFadeLength(shadowZFadeLength);

        // snapping + extents stabilization
        r.setSnapper(snapper);
        r.setSnapEnabled(snapEnabled);
        r.setStabilizeExtents(stabilizeExtents);
        r.setExtentsPadding(extentsPadding);

        // debug knobs
        r.setDebugEnabled(debugEnabled);
        r.setDebugEveryFrames(debugEveryFrames);
        r.setDebugSnapIntervalMs(debugSnapIntervalMs);

        // keep snapper consistent with mapSize
        snapper.setShadowMapSize(mapSize);

        app.getViewPort().addProcessor(r);
        this.dlsr = r;

        applyBiasKnobs(r);

        if (log.isInfoEnabled()) {
            log.info("[shadow] renderer attached mapSize={} splits={} lambda={} intensity={} snap={} stabilizeExtents={} pad={} zExtend={} zFade={} bias={} slopeBias={} normalOffset={}",
                    mapSize, splits,
                    fmt3(lambda), fmt3(intensity),
                    snapEnabled,
                    stabilizeExtents, fmt3(extentsPadding),
                    fmt3(shadowZExtend), fmt3(shadowZFadeLength),
                    fmt6(shadowBias), fmt3(shadowSlopeBias), fmt3(shadowNormalOffset));
        }
    }

    private void applyRuntimeKnobs() {
        final SnappingDirectionalLightShadowRenderer r = this.dlsr;
        if (r == null) return;

        r.setLambda(lambda);
        r.setShadowIntensity(intensity);

        r.setShadowZExtend(shadowZExtend);
        r.setShadowZFadeLength(shadowZFadeLength);

        r.setSnapEnabled(snapEnabled);
        r.setStabilizeExtents(stabilizeExtents);
        r.setExtentsPadding(extentsPadding);

        r.setDebugEnabled(debugEnabled);
        r.setDebugEveryFrames(debugEveryFrames);
        r.setDebugSnapIntervalMs(debugSnapIntervalMs);

        applyBiasKnobs(r);
    }

    private void applyBiasKnobs(Object renderer) {
        boolean ok = false;

        // Base bias
        ok |= tryInvoke(renderer, "setShadowBias", shadowBias);
        ok |= tryInvoke(renderer, "setShadowBias", (double) shadowBias);

        // Slope-scaled bias (different forks name it differently)
        ok |= tryInvoke(renderer, "setShadowSlopeScale", shadowSlopeBias);
        ok |= tryInvoke(renderer, "setShadowSlopeScale", (double) shadowSlopeBias);
        ok |= tryInvoke(renderer, "setSlopeScale", shadowSlopeBias);
        ok |= tryInvoke(renderer, "setSlopeScale", (double) shadowSlopeBias);

        // Normal offset (optional)
        ok |= tryInvoke(renderer, "setShadowNormalOffset", shadowNormalOffset);
        ok |= tryInvoke(renderer, "setShadowNormalOffset", (double) shadowNormalOffset);
        ok |= tryInvoke(renderer, "setNormalOffset", shadowNormalOffset);
        ok |= tryInvoke(renderer, "setNormalOffset", (double) shadowNormalOffset);

        if (!ok && !biasWarned && log.isWarnEnabled()) {
            biasWarned = true;
            log.warn("[shadow] bias knobs not applied (no compatible methods on {}). Shimmer likely needs polygon offset / slope bias support in your renderer.",
                    renderer.getClass().getName());
        }
    }
}