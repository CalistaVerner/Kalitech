/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetManager
 *  com.jme3.post.SceneProcessor
 *  com.jme3.renderer.Limits
 *  com.jme3.renderer.RenderManager
 *  com.jme3.renderer.ViewPort
 *  com.jme3.shadow.DirectionalLightShadowRenderer
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.Limits;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.light.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.shadow.PipelineDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.CascadeHysteresisFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.CascadeStabilityTelemetryFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.OnlySplitFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.PoissonPcfFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.ShadowSnapperFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.ShadowTraceFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.StableFitShadowCamFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.StableLightBasisFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.TemporalSnapGateFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.TightStableFitShadowCamFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelinePresetLibrary;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelineRegistry;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class Shadow {
    private static final Logger log = LogManager.getLogger(Shadow.class);
    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final LightRigModule lights;
    private DirectionalLightShadowRenderer dlsr;
    private final ShadowPipelinePresetLibrary presets = new ShadowPipelinePresetLibrary();
    private final AtomicBoolean pendingRebuild = new AtomicBoolean(false);
    private final AtomicBoolean pendingFullReload = new AtomicBoolean(false);
    private final ShadowPipelineRegistry registry = new ShadowPipelineRegistry(this.presets);
    private boolean enabled = true;
    private int splits = 4;
    private float lambda = 0.72f;
    private float intensity = 0.75f;
    private float shadowZExtend = 1000.0f;
    private float shadowZFadeLength = 0.0f;
    private int mapSize = 8192;
    private float extentsPadding = 1.02f;
    private float splitSmoothing = 0.1f;
    private int snapFirstCascades = 2;
    private boolean snapEnabled = true;
    private boolean usePcss = false;
    private float[] fixedSplitDistances = null;
    private float splitHysteresis = 10.0f;
    private int glMaxTexSize = 0;
    private String pipelineKey = "";
    private ShadowPipelineRegistry.PipelineDef pipelineDef = null;

    public Shadow(RenderThread thread, SimpleApplication app, AssetManager assets, Logger ignoredExternalLogger, LightRigModule lights) {
        this.thread = Objects.requireNonNull(thread, "thread");
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.lights = Objects.requireNonNull(lights, "lights");
        this.registerDefaultPipelineSteps();
    }

    private void registerDefaultPipelineSteps() {
        this.registry.register("hysteresis", CascadeHysteresisFilter.class);
        this.registry.register("basis", StableLightBasisFilter.class);
        this.registry.register("stableFit", StableFitShadowCamFilter.class);
        this.registry.register("tightFit", TightStableFitShadowCamFilter.class);
        this.registry.register("temporalGate", TemporalSnapGateFilter.class);
        this.registry.register("texelSnap", ShadowSnapperFilter.class);
        this.registry.register("trace", ShadowTraceFilter.class);
        this.registry.register("telemetry", CascadeStabilityTelemetryFilter.class);
        this.registry.register("onlySplit", OnlySplitFilter.class);
        this.registry.register("poissonPcf", PoissonPcfFilter.class);
    }

    private static boolean hasAny(LuaValueRef v, String a, String b) {
        return LuaCfg.has((LuaValueRef)v, (String)a) || LuaCfg.has((LuaValueRef)v, (String)b);
    }

    private static int clampInt(int v, int lo, int hi, int def) {
        if (v == 0) {
            return def;
        }
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private static double clampDouble(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.requestFullReload();
    }

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = Shadow.clampInt(mapSize, 256, 16384, this.mapSize);
        this.splits = Shadow.clampInt(splits, 1, 8, this.splits);
        if (lambda > 0.0f) {
            this.lambda = lambda;
        }
        this.intensity = Math.max(0.0f, intensity);
        this.requestRebuild();
    }

    public void requestFullReload() {
        this.pendingFullReload.set(true);
        this.pendingRebuild.set(true);
        this.flushPendingAsync();
    }

    private void requestRebuild() {
        this.pendingRebuild.set(true);
        this.flushPendingAsync();
    }

    private void flushPendingAsync() {
        this.thread.onJme(this::flushPending);
    }

    public void flushPending() {
        if (!this.pendingRebuild.getAndSet(false)) {
            return;
        }
        boolean full = this.pendingFullReload.getAndSet(false);
        this.rebuildNow(full);
    }

    public void setSnapEnabled(boolean enabled) {
        if (this.snapEnabled == enabled) {
            return;
        }
        this.snapEnabled = enabled;
        this.requestRebuild();
    }

    public void setSnapFirstCascades(int count) {
        int v = Shadow.clampInt(count, 0, 8, this.snapFirstCascades);
        if (v == this.snapFirstCascades) {
            return;
        }
        this.snapFirstCascades = v;
        this.requestRebuild();
    }

    public void setExtentsPadding(float padding) {
        float v = Math.max(1.0f, padding);
        if (v == this.extentsPadding) {
            return;
        }
        this.extentsPadding = v;
        this.requestRebuild();
    }

    public void setSplitHysteresis(float hysteresis) {
        float v = Math.max(0.0f, hysteresis);
        if (v == this.splitHysteresis) {
            return;
        }
        this.splitHysteresis = v;
        this.requestRebuild();
    }

    public void setSplitSmoothing(float smoothing) {
        float v = Math.max(0.0f, Math.min(1.0f, smoothing));
        if (v == this.splitSmoothing) {
            return;
        }
        this.splitSmoothing = v;
        this.requestRebuild();
    }

    public void setUsePcss(boolean enabled) {
        if (this.usePcss == enabled) {
            return;
        }
        this.usePcss = enabled;
        this.requestRebuild();
    }

    private ViewPort vp() {
        return this.app.getViewPort();
    }

    public DirectionalLightShadowRenderer renderer() {
        return this.dlsr;
    }

    public void setShadowZExtend(float zExtend) {
        float v = Math.max(0.0f, zExtend);
        if (v == this.shadowZExtend) {
            return;
        }
        this.shadowZExtend = v;
        this.thread.onJme(() -> {
            if (this.dlsr != null) {
                this.dlsr.setShadowZExtend(this.shadowZExtend);
            }
        });
        log.info("[shadow][cfg] zExtend={}", (Object)Float.valueOf(this.shadowZExtend));
    }

    public void setShadowZFadeLength(float zFadeLength) {
        float v = Math.max(0.0f, zFadeLength);
        if (v == this.shadowZFadeLength) {
            return;
        }
        this.shadowZFadeLength = v;
        this.thread.onJme(() -> {
            if (this.dlsr != null) {
                this.dlsr.setShadowZFadeLength(this.shadowZFadeLength);
            }
        });
        log.info("[shadow][cfg] zFadeLength={}", (Object)Float.valueOf(this.shadowZFadeLength));
    }

    public void onPrimaryLightChanged() {
        this.thread.onJme(() -> {
            if (this.dlsr == null) {
                return;
            }
            if (this.lights.primaryLight() == null) {
                log.warn("[shadow][cfg] primaryLight=null => disabledByLight");
                return;
            }
            this.dlsr.setLight(this.lights.primaryLight());
            log.info("[shadow][cfg] primaryLight=updated dir={}", (Object)this.lights.primaryDirectional());
        });
    }

    private RenderManager rm() {
        return this.app.getRenderManager();
    }

    private DirectionalLightShadowRenderer detachOnly() {
        if (this.dlsr == null) {
            return null;
        }
        DirectionalLightShadowRenderer old = this.dlsr;
        this.dlsr = null;
        ViewPort vp = this.vp();
        try {
            vp.removeProcessor((SceneProcessor)old);
        }
        catch (Throwable t) {
            log.warn("[shadow][cfg] removeProcessor failed: {}", (Object)t.toString());
        }
        return old;
    }

    private void deferCleanup(DirectionalLightShadowRenderer old) {
        if (old == null) {
            return;
        }
        this.app.enqueue(() -> {
            try {
                RenderManager rm = this.rm();
                if (old instanceof PipelineDirectionalLightShadowRenderer) {
                    PipelineDirectionalLightShadowRenderer p = (PipelineDirectionalLightShadowRenderer)old;
                    p.destroy(rm);
                } else {
                    old.cleanup();
                }
            }
            catch (Throwable t) {
                log.warn("[shadow][cfg] deferredCleanup failed: {}", (Object)t.toString());
            }
            return null;
        });
    }

    private void sanitize() {
        this.mapSize = Shadow.clampInt(this.mapSize, 256, 16384, 8192);
        this.splits = Shadow.clampInt(this.splits, 1, 8, 4);
        if (this.lambda <= 0.0f) {
            this.lambda = 0.72f;
        }
        if (this.shadowZExtend < 0.0f) {
            this.shadowZExtend = 0.0f;
        }
        if (this.extentsPadding < 1.0f) {
            this.extentsPadding = 1.02f;
        }
        this.snapFirstCascades = Shadow.clampInt(this.snapFirstCascades, 0, this.splits, Math.min(1, this.splits));
    }

    private int queryMaxShadowMapSize() {
        try {
            RenderManager rm = this.app.getRenderManager();
            if (rm == null || rm.getRenderer() == null) {
                return 16384;
            }
            Integer lim = (Integer)rm.getRenderer().getLimits().get(Limits.TextureSize);
            if (lim == null) {
                return 16384;
            }
            return lim;
        }
        catch (Throwable t) {
            return 16384;
        }
    }

    private int clampShadowMapSizeToGpu(int requested) {
        int max;
        if (this.glMaxTexSize <= 0) {
            this.glMaxTexSize = this.queryMaxShadowMapSize();
        }
        int n = max = this.glMaxTexSize > 0 ? this.glMaxTexSize : 16384;
        if (max >= 16384 && requested >= 16384) {
            return 12456;
        }
        return Math.min(requested, max);
    }

    public void fullReloadNow() {
        this.rebuildNow(true);
    }

    public void applyCfg(LuaValueRef cfg) {
        ShadowPipelineRegistry.PipelineDef newDef;
        String newKey;
        boolean v;
        float v2;
        float v3;
        float v4;
        int v5;
        boolean v6;
        float v7;
        int newSplits;
        int newMap;
        boolean v8;
        if (cfg == null || cfg.isNull()) {
            return;
        }
        LuaValueRef src = cfg;
        LuaValueRef nested = LuaCfg.member((LuaValueRef)cfg, (String)"shadows");
        if (nested != null && !nested.isNull()) {
            src = nested;
        }
        boolean changed = false;
        if (LuaCfg.has((LuaValueRef)src, (String)"enabled") && (v8 = LuaCfg.bool((LuaValueRef)src, (String)"enabled", (boolean)this.enabled)) != this.enabled) {
            this.enabled = v8;
            changed = true;
        }
        if ((newMap = Shadow.clampInt((int)LuaCfg.num((LuaValueRef)src, (String)"mapSize", (double)this.mapSize), 256, 16384, this.mapSize)) != this.mapSize) {
            this.mapSize = newMap;
            changed = true;
        }
        if ((newSplits = Shadow.clampInt((int)LuaCfg.num((LuaValueRef)src, (String)"splits", (double)this.splits), 1, 8, this.splits)) != this.splits) {
            this.splits = newSplits;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"lambda") && (v7 = (float)LuaCfg.num((LuaValueRef)src, (String)"lambda", (double)this.lambda)) > 0.0f && v7 != this.lambda) {
            this.lambda = v7;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"intensity") && (v7 = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)src, (String)"intensity", (double)this.intensity))) != this.intensity) {
            this.intensity = v7;
            changed = true;
        }
        if (Shadow.hasAny(src, "shadowZExtend", "zExtend") && (v7 = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)src, (String)"shadowZExtend", (double)LuaCfg.num((LuaValueRef)src, (String)"zExtend", (double)this.shadowZExtend)))) != this.shadowZExtend) {
            this.shadowZExtend = v7;
            changed = true;
        }
        if (Shadow.hasAny(src, "shadowZFadeLength", "zFadeLength") && (v7 = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)src, (String)"shadowZFadeLength", (double)LuaCfg.num((LuaValueRef)src, (String)"zFadeLength", (double)this.shadowZFadeLength)))) != this.shadowZFadeLength) {
            this.shadowZFadeLength = v7;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"snap") && (v6 = LuaCfg.bool((LuaValueRef)src, (String)"snap", (boolean)this.snapEnabled)) != this.snapEnabled) {
            this.snapEnabled = v6;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"snapFirstCascades") && (v5 = Shadow.clampInt((int)LuaCfg.num((LuaValueRef)src, (String)"snapFirstCascades", (double)this.snapFirstCascades), 0, 8, this.snapFirstCascades)) != this.snapFirstCascades) {
            this.snapFirstCascades = v5;
            changed = true;
        }
        if (Shadow.hasAny(src, "extentsPadding", "pad") && (v4 = (float)Math.max(1.0, LuaCfg.num((LuaValueRef)src, (String)"extentsPadding", (double)LuaCfg.num((LuaValueRef)src, (String)"pad", (double)this.extentsPadding)))) != this.extentsPadding) {
            this.extentsPadding = v4;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"splitHysteresis") && (v3 = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)src, (String)"splitHysteresis", (double)this.splitHysteresis))) != this.splitHysteresis) {
            this.splitHysteresis = v3;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"splitSmoothing") && (v2 = (float)Shadow.clampDouble(LuaCfg.num((LuaValueRef)src, (String)"splitSmoothing", (double)this.splitSmoothing), 0.0, 1.0)) != this.splitSmoothing) {
            this.splitSmoothing = v2;
            changed = true;
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"fixedSplits")) {
            LuaValueRef arr = LuaCfg.member((LuaValueRef)src, (String)"fixedSplits");
            if (arr == null || arr.isNull()) {
                if (this.fixedSplitDistances != null) {
                    this.fixedSplitDistances = null;
                    changed = true;
                }
            } else if (arr.hasArrayElements()) {
                int n = (int)arr.getArraySize();
                float[] out = new float[n];
                for (int i = 0; i < n; ++i) {
                    LuaValueRef e = arr.getArrayElement((long)i);
                    out[i] = (float)(e != null && e.isNumber() ? e.asDouble() : 0.0);
                }
                Arrays.sort(out);
                if (!Arrays.equals(out, this.fixedSplitDistances)) {
                    this.fixedSplitDistances = out;
                    changed = true;
                }
            }
        }
        if (LuaCfg.has((LuaValueRef)src, (String)"pcss") && (v = LuaCfg.bool((LuaValueRef)src, (String)"pcss", (boolean)this.usePcss)) != this.usePcss) {
            this.usePcss = v;
            changed = true;
        }
        String string = newKey = (newDef = this.registry.parsePipeline(log, src, this.splits)) == null ? "" : newDef.key;
        if (!Objects.equals(newKey, this.pipelineKey)) {
            this.pipelineKey = newKey;
            this.pipelineDef = newDef;
            changed = true;
        }
        if (changed) {
            this.requestRebuild();
        }
    }

    private void rebuildNow(boolean fullReload) {
        ViewPort vp = this.vp();
        if (!this.enabled) {
            DirectionalLightShadowRenderer old = this.detachOnly();
            this.deferCleanup(old);
            log.info("[shadow][cfg] state=disabled");
            return;
        }
        if (this.lights.primaryLight() == null) {
            DirectionalLightShadowRenderer old = this.detachOnly();
            this.deferCleanup(old);
            log.warn("[shadow][cfg] state=blocked reason=primaryLightNull");
            return;
        }
        this.sanitize();
        int reqMap = this.mapSize;
        this.mapSize = this.clampShadowMapSizeToGpu(this.mapSize);
        if (this.mapSize != reqMap) {
            log.warn("[shadow][cfg] mapSize clamped requested={} actual={}", (Object)reqMap, (Object)this.mapSize);
        }
        DirectionalLightShadowRenderer old = this.detachOnly();
        PipelineDirectionalLightShadowRenderer r = new PipelineDirectionalLightShadowRenderer(this.assets, this.mapSize, this.splits);
        if (this.fixedSplitDistances != null && this.fixedSplitDistances.length == this.splits + 1) {
            r.setFixedSplitDistances(this.fixedSplitDistances);
        }
        if (this.pipelineDef != null) {
            this.buildPipelineFromRegistry(r, this.pipelineDef);
        } else {
            CascadeHysteresisFilter hyst = new CascadeHysteresisFilter();
            hyst.hysteresis = this.splitHysteresis <= 0.0f ? 10.0f : this.splitHysteresis;
            hyst.smoothing = this.splitSmoothing <= 0.0f ? 0.1f : this.splitSmoothing;
            StableLightBasisFilter basis = new StableLightBasisFilter();
            StableFitShadowCamFilter fit = new StableFitShadowCamFilter();
            fit.extentsPadding = Math.max(0.0f, this.extentsPadding - 1.0f);
            fit.forceSquare = true;
            fit.sizeQuantizeTexels = 1.0f;
            fit.minNear = 0.5f;
            fit.casterBackBase = 140.0f;
            fit.casterBackCascadeMul = 0.9f;
            fit.receiverFrontBase = 40.0f;
            TemporalSnapGateFilter gate = new TemporalSnapGateFilter();
            gate.setEnabled(true);
            gate.setMinRotateDeg(0.25f);
            gate.setMinMoveTexels(1.25f);
            gate.setTeleportMoveTexels(24.0f);
            gate.setGatedFirstCascades(Math.min(1, this.splits));
            ShadowSnapperFilter snap = new ShadowSnapperFilter();
            snap.setEnabled(this.snapEnabled);
            snap.setSnapFirstCascades(this.snapFirstCascades);
            snap.setHoldEnabled(true);
            snap.setHoldThresholdTexels(1.25f);
            ShadowTraceFilter trace = new ShadowTraceFilter();
            trace.setEveryFrames(60);
            r.pipeline().add(hyst).add(basis).add(fit).add(gate).add(snap).add(trace);
        }
        r.setLight(this.lights.primaryLight());
        r.setLambda(this.lambda);
        r.setShadowIntensity(this.intensity);
        r.setShadowZExtend(this.shadowZExtend);
        r.setShadowZFadeLength(this.shadowZFadeLength);
        this.dlsr = r;
        if (!vp.getProcessors().contains((Object)this.dlsr)) {
            vp.addProcessor((SceneProcessor)this.dlsr);
        }
        this.deferCleanup(old);
        log.info("[shadow][cfg] reload={} type={} map={} splits={} lambda={} intensity={} snap={} snapCascades={} pad={} hyst={} smooth={} zExtend={} zFade={} fixedSplits={} pipeline={}", new Object[]{fullReload, this.dlsr.getClass().getSimpleName(), this.mapSize, this.splits, Float.valueOf(this.lambda), Float.valueOf(this.intensity), this.snapEnabled, this.snapFirstCascades, Float.valueOf(this.extentsPadding), Float.valueOf(this.splitHysteresis), Float.valueOf(this.splitSmoothing), Float.valueOf(this.shadowZExtend), Float.valueOf(this.shadowZFadeLength), this.fixedSplitDistances == null ? "null" : Arrays.toString(this.fixedSplitDistances), this.pipelineDef == null ? "default" : this.pipelineDef.key});
    }

    private void buildPipelineFromRegistry(PipelineDirectionalLightShadowRenderer r, ShadowPipelineRegistry.PipelineDef def) {
        ShadowPipelineRegistry.Runtime rt = new ShadowPipelineRegistry.Runtime(this.mapSize, this.splits);
        for (ShadowFilter f : this.registry.build(log, rt, def)) {
            r.pipeline().add(f);
        }
    }

    public Set<String> pipelineKnownTypes() {
        return this.registry.knownTypes();
    }

    public List<ShadowPipelineRegistry.OptionSpec> pipelineSchema(String type) {
        return this.registry.schemaFor(type);
    }

    public Set<String> pipelinePresetNames() {
        return this.presets.names();
    }
}

