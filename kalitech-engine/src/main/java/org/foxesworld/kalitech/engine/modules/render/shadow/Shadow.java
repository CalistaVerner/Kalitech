// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/Shadow.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.light.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.*;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelinePresetLibrary;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelineRegistry;
import org.graalvm.polyglot.Value;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jme3.renderer.Limits.TextureSize;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Shadow orchestrator.
 * <p>
 * Owns the active shadow renderer instance and applies a full JS configuration object.
 * Supports safe hot reload: detach -> create new -> attach, and cleanup old renderer deferred.
 */
public final class Shadow {

    private static final Logger log = LogManager.getLogger(Shadow.class);

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;

    // Pending application flags
    private final AtomicBoolean pendingRebuild = new AtomicBoolean(false);
    private final AtomicBoolean pendingFullReload = new AtomicBoolean(false);
    private final ShadowPipelineRegistry registry = new ShadowPipelineRegistry(presets);
    private boolean enabled = true;
    private int splits = 4;
    private float lambda = 0.72f;
    private float intensity = 0.75f;
    private float shadowZExtend = 1000f;
    private float shadowZFadeLength = 0f;
    // Base values (defaults)
    private int mapSize = 8192;
    private float extentsPadding = 1.02f;
    private float splitSmoothing = 0.10f;
    // Fit/anti-shimmer knobs
    private int snapFirstCascades = 2;

    // Knobs
    private boolean snapEnabled = true;

    // PCSS toggle (wired later via material/shader filter)
    private boolean usePcss = false;

    // Optional fixed splits (null = disabled)
    private float[] fixedSplitDistances = null;
    private float splitHysteresis = 10.0f;

    // Pipeline system
    private final ShadowPipelinePresetLibrary presets = new ShadowPipelinePresetLibrary();
    // GPU limits cache
    private int glMaxTexSize = 0;

    private String pipelineKey = "";
    private ShadowPipelineRegistry.PipelineDef pipelineDef = null;

    public Shadow(RenderThread thread, SimpleApplication app, AssetManager assets, Logger ignoredExternalLogger, LightRigModule lights) {
        this.thread = Objects.requireNonNull(thread, "thread");
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.lights = Objects.requireNonNull(lights, "lights");
        registerDefaultPipelineSteps();
    }

    private void registerDefaultPipelineSteps() {
        registry.register("hysteresis", CascadeHysteresisFilter.class);
        registry.register("basis", StableLightBasisFilter.class);

        registry.register("stableFit", StableFitShadowCamFilter.class);
        registry.register("tightFit", TightStableFitShadowCamFilter.class);

        registry.register("temporalGate", TemporalSnapGateFilter.class);
        registry.register("texelSnap", TexelSnapFilter.class);

        registry.register("trace", ShadowTraceFilter.class);

        registry.register("onlySplit", OnlySplitFilter.class);

        registry.register("poissonPcf", PoissonPcfFilter.class);
        registry.alias("pcf", "poissonPcf");

        registry.register("telemetry", CascadeStabilityTelemetryFilter.class);
        registry.register("stabilityTelemetry", CascadeStabilityTelemetryFilter.class);

        registry.register("fit", TightStableFitShadowCamFilter.class);
        registry.register("snap", TexelSnapFilter.class);
    }

    // ---------------------------------------------------------------------
    // Engine API
    // ---------------------------------------------------------------------

    private static boolean hasAny(Value v, String a, String b) {
        return has(v, a) || has(v, b);
    }

    private static int clampInt(int v, int lo, int hi, int def) {
        if (v == 0) return def;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double clampDouble(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        requestFullReload();
    }

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = clampInt(mapSize, 256, 16384, this.mapSize);
        this.splits = clampInt(splits, 1, 8, this.splits);
        if (lambda > 0f) this.lambda = lambda;
        this.intensity = Math.max(0f, intensity);
        requestRebuild();
    }

    /**
     * Forces full hot reload: shadows are recreated even if the config did not change.
     * Call this on F5/script reload.
     */
    public void requestFullReload() {
        pendingFullReload.set(true);
        pendingRebuild.set(true);
        flushPendingAsync();
    }

    /**
     * Requests a rebuild; may be coalesced.
     */
    private void requestRebuild() {
        pendingRebuild.set(true);
        flushPendingAsync();
    }

    /**
     * Schedules pending rebuild on jME thread.
     */
    private void flushPendingAsync() {
        thread.onJme(this::flushPending);
    }

    /**
     * Executes pending rebuild on render thread (safe-point).
     * This method is render-thread only.
     */
    public void flushPending() {
        if (!pendingRebuild.getAndSet(false)) return;
        boolean full = pendingFullReload.getAndSet(false);
        rebuildNow(full);
    }

    public void setSnapEnabled(boolean enabled) {
        if (this.snapEnabled == enabled) return;
        this.snapEnabled = enabled;
        requestRebuild();
    }

    public void setSnapFirstCascades(int count) {
        int v = clampInt(count, 0, 8, this.snapFirstCascades);
        if (v == this.snapFirstCascades) return;
        this.snapFirstCascades = v;
        requestRebuild();
    }

    public void setExtentsPadding(float padding) {
        float v = Math.max(1.0f, padding);
        if (v == this.extentsPadding) return;
        this.extentsPadding = v;
        requestRebuild();
    }

    public void setSplitHysteresis(float hysteresis) {
        float v = Math.max(0f, hysteresis);
        if (v == this.splitHysteresis) return;
        this.splitHysteresis = v;
        requestRebuild();
    }

    public void setSplitSmoothing(float smoothing) {
        float v = Math.max(0f, Math.min(1f, smoothing));
        if (v == this.splitSmoothing) return;
        this.splitSmoothing = v;
        requestRebuild();
    }

    public void setUsePcss(boolean enabled) {
        if (this.usePcss == enabled) return;
        this.usePcss = enabled;
        requestRebuild();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private ViewPort vp() {
        return app.getViewPort();
    }

    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    public void setShadowZExtend(float zExtend) {
        float v = Math.max(0f, zExtend);
        if (v == this.shadowZExtend) return;
        this.shadowZExtend = v;
        thread.onJme(() -> {
            if (dlsr != null) dlsr.setShadowZExtend(this.shadowZExtend);
        });
        log.info("[shadow][cfg] zExtend={}", this.shadowZExtend);
    }

    public void setShadowZFadeLength(float zFadeLength) {
        float v = Math.max(0f, zFadeLength);
        if (v == this.shadowZFadeLength) return;
        this.shadowZFadeLength = v;
        thread.onJme(() -> {
            if (dlsr != null) dlsr.setShadowZFadeLength(this.shadowZFadeLength);
        });
        log.info("[shadow][cfg] zFadeLength={}", this.shadowZFadeLength);
    }

    public void onPrimaryLightChanged() {
        thread.onJme(() -> {
            if (dlsr == null) return;
            if (lights.primaryLight() == null) {
                log.warn("[shadow][cfg] primaryLight=null => disabledByLight");
                return;
            }
            dlsr.setLight(lights.primaryLight());
            log.info("[shadow][cfg] primaryLight=updated dir={}", lights.primaryDirectional());
        });
    }

    private RenderManager rm() {
        return app.getRenderManager();
    }

    /**
     * Detach current renderer from viewport, but do not cleanup here.
     * Returns old renderer instance for deferred cleanup.
     */
    private DirectionalLightShadowRenderer detachOnly() {
        if (dlsr == null) return null;

        DirectionalLightShadowRenderer old = dlsr;
        dlsr = null;

        ViewPort vp = vp();
        try {
            vp.removeProcessor(old);
        } catch (Throwable t) {
            log.warn("[shadow][cfg] removeProcessor failed: {}", t.toString());
        }
        return old;
    }

    /**
     * Cleanup must be executed on render thread safe-point.
     * We schedule it via app.enqueue(...) which runs on the jME thread.
     */
    private void deferCleanup(DirectionalLightShadowRenderer old) {
        if (old == null) return;

        app.enqueue(() -> {
            try {
                RenderManager rm = rm();
                if (old instanceof PipelineDirectionalLightShadowRenderer p) {
                    p.destroy(rm);
                } else {
                    old.cleanup();
                }
            } catch (Throwable t) {
                log.warn("[shadow][cfg] deferredCleanup failed: {}", t.toString());
            }
            return null;
        });
    }

    private void sanitize() {
        mapSize = clampInt(mapSize, 256, 16384, 8192);
        splits = clampInt(splits, 1, 8, 4);

        if (lambda <= 0f) lambda = 0.72f;
        if (shadowZExtend < 0f) shadowZExtend = 0f;
        if (extentsPadding < 1.0f) extentsPadding = 1.02f;

        snapFirstCascades = clampInt(snapFirstCascades, 0, splits, Math.min(1, splits));
    }

    private int queryMaxShadowMapSize() {
        try {
            RenderManager rm = app.getRenderManager();
            if (rm == null || rm.getRenderer() == null) return 16384;
            Integer lim = rm.getRenderer().getLimits().get(TextureSize);
            if (lim == null) return 16384;
            return lim;
        } catch (Throwable t) {
            return 16384;
        }
    }

    private int clampShadowMapSizeToGpu(int requested) {
        if (glMaxTexSize <= 0) {
            glMaxTexSize = queryMaxShadowMapSize();
        }
        int max = glMaxTexSize > 0 ? glMaxTexSize : 16384;

        if (max >= 16384 && requested >= 16384) {
            return 12456;
        }

        return Math.min(requested, max);
    }

    /**
     * Render-thread only: immediate full hot reload (detach -> new -> attach) without pending scheduling.
     * Use it inside onJmeSyncVoid/onJme blocks.
     */
    public void fullReloadNow() {
        rebuildNow(true);
    }

    public void applyCfg(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        Value src = cfg;
        Value nested = member(cfg, "shadows");
        if (nested != null && !nested.isNull()) src = nested;

        boolean changed = false;

        if (has(src, "enabled")) {
            boolean v = bool(src, "enabled", this.enabled);
            if (v != this.enabled) {
                this.enabled = v;
                changed = true;
            }
        }

        int newMap = clampInt((int) num(src, "mapSize", this.mapSize), 256, 16384, this.mapSize);
        if (newMap != this.mapSize) {
            this.mapSize = newMap;
            changed = true;
        }

        int newSplits = clampInt((int) num(src, "splits", this.splits), 1, 8, this.splits);
        if (newSplits != this.splits) {
            this.splits = newSplits;
            changed = true;
        }

        if (has(src, "lambda")) {
            float v = (float) num(src, "lambda", this.lambda);
            if (v > 0f && v != this.lambda) {
                this.lambda = v;
                changed = true;
            }
        }

        if (has(src, "intensity")) {
            float v = (float) Math.max(0.0, num(src, "intensity", this.intensity));
            if (v != this.intensity) {
                this.intensity = v;
                changed = true;
            }
        }

        if (hasAny(src, "shadowZExtend", "zExtend")) {
            float v = (float) Math.max(0.0, num(src, "shadowZExtend", num(src, "zExtend", this.shadowZExtend)));
            if (v != this.shadowZExtend) {
                this.shadowZExtend = v;
                changed = true;
            }
        }

        if (hasAny(src, "shadowZFadeLength", "zFadeLength")) {
            float v = (float) Math.max(0.0, num(src, "shadowZFadeLength", num(src, "zFadeLength", this.shadowZFadeLength)));
            if (v != this.shadowZFadeLength) {
                this.shadowZFadeLength = v;
                changed = true;
            }
        }

        if (has(src, "snap")) {
            boolean v = bool(src, "snap", this.snapEnabled);
            if (v != this.snapEnabled) {
                this.snapEnabled = v;
                changed = true;
            }
        }

        if (has(src, "snapFirstCascades")) {
            int v = clampInt((int) num(src, "snapFirstCascades", this.snapFirstCascades), 0, 8, this.snapFirstCascades);
            if (v != this.snapFirstCascades) {
                this.snapFirstCascades = v;
                changed = true;
            }
        }

        if (hasAny(src, "extentsPadding", "pad")) {
            float v = (float) Math.max(1.0, num(src, "extentsPadding", num(src, "pad", this.extentsPadding)));
            if (v != this.extentsPadding) {
                this.extentsPadding = v;
                changed = true;
            }
        }

        if (has(src, "splitHysteresis")) {
            float v = (float) Math.max(0.0, num(src, "splitHysteresis", this.splitHysteresis));
            if (v != this.splitHysteresis) {
                this.splitHysteresis = v;
                changed = true;
            }
        }

        if (has(src, "splitSmoothing")) {
            float v = (float) clampDouble(num(src, "splitSmoothing", this.splitSmoothing), 0.0, 1.0);
            if (v != this.splitSmoothing) {
                this.splitSmoothing = v;
                changed = true;
            }
        }

        if (has(src, "fixedSplits")) {
            Value arr = member(src, "fixedSplits");
            if (arr == null || arr.isNull()) {
                if (this.fixedSplitDistances != null) {
                    this.fixedSplitDistances = null;
                    changed = true;
                }
            } else if (arr.hasArrayElements()) {
                int n = (int) arr.getArraySize();
                float[] out = new float[n];
                for (int i = 0; i < n; i++) {
                    Value e = arr.getArrayElement(i);
                    out[i] = (float) (e != null && e.isNumber() ? e.asDouble() : 0.0);
                }
                Arrays.sort(out);
                if (!Arrays.equals(out, this.fixedSplitDistances)) {
                    this.fixedSplitDistances = out;
                    changed = true;
                }
            }
        }

        if (has(src, "pcss")) {
            boolean v = bool(src, "pcss", this.usePcss);
            if (v != this.usePcss) {
                this.usePcss = v;
                changed = true;
            }
        }

        ShadowPipelineRegistry.PipelineDef newDef = registry.parsePipeline(log, src, this.splits);
        String newKey = (newDef == null) ? "" : newDef.key;
        if (!Objects.equals(newKey, this.pipelineKey)) {
            this.pipelineKey = newKey;
            this.pipelineDef = newDef;
            changed = true;
        }

        if (changed) requestRebuild();
    }

    /**
     * Hot-reload safe rebuild:
     * - detach old processor
     * - attach new processor (if enabled)
     * - cleanup old is deferred via app.enqueue(...)
     */
    private void rebuildNow(boolean fullReload) {
        ViewPort vp = vp();

        if (!enabled) {
            DirectionalLightShadowRenderer old = detachOnly();
            deferCleanup(old);
            log.info("[shadow][cfg] state=disabled");
            return;
        }

        if (lights.primaryLight() == null) {
            DirectionalLightShadowRenderer old = detachOnly();
            deferCleanup(old);
            log.warn("[shadow][cfg] state=blocked reason=primaryLightNull");
            return;
        }

        sanitize();

        int reqMap = mapSize;
        mapSize = clampShadowMapSizeToGpu(mapSize);
        if (mapSize != reqMap) {
            log.warn("[shadow][cfg] mapSize clamped requested={} actual={}", reqMap, mapSize);
        }

        DirectionalLightShadowRenderer old = detachOnly();

        PipelineDirectionalLightShadowRenderer r =
                new PipelineDirectionalLightShadowRenderer(assets, mapSize, splits);

        if (fixedSplitDistances != null && fixedSplitDistances.length == (splits + 1)) {
            r.setFixedSplitDistances(fixedSplitDistances);
        }

        // ----- Build pipeline -----
        if (pipelineDef != null) {
            buildPipelineFromRegistry(r, pipelineDef);
        } else {
            // Default deterministic pipeline (workspace-synchronized filters)
            CascadeHysteresisFilter hyst = new CascadeHysteresisFilter();
            hyst.hysteresis = (splitHysteresis <= 0f) ? 10.0f : splitHysteresis;
            hyst.smoothing = (splitSmoothing <= 0f) ? 0.10f : splitSmoothing;

            StableLightBasisFilter basis = new StableLightBasisFilter();

            // Fit publishes TEXEL_WORLD into workspace (critical for gate/snap/pcf)
            StableFitShadowCamFilter fit = new StableFitShadowCamFilter();
            fit.extentsPadding = Math.max(0f, extentsPadding - 1.0f);
            fit.forceSquare = true;
            fit.sizeQuantizeTexels = 1.0f;
            fit.minNear = 0.5f;
            fit.casterBackBase = 140f;
            fit.casterBackCascadeMul = 0.9f;
            fit.receiverFrontBase = 40f;

            TemporalSnapGateFilter gate = new TemporalSnapGateFilter();
            gate.setEnabled(true);
            gate.setMinRotateDeg(0.25f);
            gate.setMinMoveTexels(1.25f);
            gate.setTeleportMoveTexels(24.0f);
            gate.setGatedFirstCascades(Math.min(1, splits));

            TexelSnapFilter snap = new TexelSnapFilter();
            snap.enabled = snapEnabled;
            snap.snapFirstCascades = snapFirstCascades;

            ShadowTraceFilter trace = new ShadowTraceFilter();
            trace.setEveryFrames(60);

            r.pipeline()
                    .add(hyst)
                    .add(basis)
                    .add(fit)
                    .add(gate)
                    .add(snap)
                    .add(trace);
        }

        // ----- Apply dynamic params -----
        r.setLight(lights.primaryLight());
        r.setLambda(lambda);
        r.setShadowIntensity(intensity);
        r.setShadowZExtend(shadowZExtend);
        r.setShadowZFadeLength(shadowZFadeLength);

        dlsr = r;
        if (!vp.getProcessors().contains(dlsr)) {
            vp.addProcessor(dlsr);
        }

        deferCleanup(old);

        log.info(
                "[shadow][cfg] reload={} type={} map={} splits={} lambda={} intensity={} snap={} snapCascades={} pad={} hyst={} smooth={} zExtend={} zFade={} fixedSplits={} pipeline={}",
                fullReload,
                dlsr.getClass().getSimpleName(),
                mapSize, splits, lambda, intensity,
                snapEnabled, snapFirstCascades,
                extentsPadding,
                splitHysteresis, splitSmoothing,
                shadowZExtend, shadowZFadeLength,
                (fixedSplitDistances == null ? "null" : Arrays.toString(fixedSplitDistances)),
                (pipelineDef == null ? "default" : pipelineDef.key)
        );
    }

    private void buildPipelineFromRegistry(PipelineDirectionalLightShadowRenderer r, ShadowPipelineRegistry.PipelineDef def) {
        ShadowPipelineRegistry.Runtime rt = new ShadowPipelineRegistry.Runtime(mapSize, splits);
        for (ShadowFilter f : registry.build(log, rt, def)) {
            r.pipeline().add(f);
        }
    }

    // ---------------------------------------------------------------------
    // Optional helpers for JS tooling (schema/introspection)
    // ---------------------------------------------------------------------

    public Set<String> pipelineKnownTypes() {
        return registry.knownTypes();
    }

    public java.util.List<ShadowPipelineRegistry.OptionSpec> pipelineSchema(String type) {
        return registry.schemaFor(type);
    }

    public Set<String> pipelinePresetNames() {
        return presets.names();
    }
}