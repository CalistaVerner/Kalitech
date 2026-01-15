// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/Shadow.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.modules.render.light.LightRigModule;
import org.foxesworld.kalitech.engine.modules.render.shadow.filters.*;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.graalvm.polyglot.Value;

import java.util.*;
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

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;

    // Pending application flags
    private final AtomicBoolean pendingRebuild = new AtomicBoolean(false);
    private int splits = 4;
    private float lambda = 0.72f;
    private float intensity = 0.75f;
    private float shadowZExtend = 1000f;
    private float shadowZFadeLength = 0f;

    private boolean enabled = true;
    private final AtomicBoolean pendingFullReload = new AtomicBoolean(false);

    // Base values (defaults)
    private int mapSize = 8192;
    private int snapFirstCascades = 2;
    private float extentsPadding = 1.02f;
    private int glMaxTexSize = 0;
    private float splitSmoothing = 0.10f;

    // Knobs
    private boolean snapEnabled = true;

    // PCSS toggle (wired later via material/shader filter)
    private boolean usePcss = false;

    // Anti-popping
    private float splitHysteresis = 10.0f;

    // Optional fixed splits (null = disabled)
    private float[] fixedSplitDistances = null;

    // JS-defined pipeline (null => use default hardcoded pipeline)
    private ShadowPipelineDef pipelineDef = null;
    private String pipelineKey = "";

    public Shadow(RenderThread thread, SimpleApplication app, AssetManager assets, Logger log, LightRigModule lights) {
        this.thread = Objects.requireNonNull(thread, "thread");
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.log = Objects.requireNonNull(log, "log");
        this.lights = Objects.requireNonNull(lights, "lights");
    }

    // ---------------------------------------------------------------------
    // Engine API
    // ---------------------------------------------------------------------

    private static boolean has(Value v, String name) {
        try {
            return v != null && v.hasMember(name);
        } catch (Throwable t) {
            return false;
        }
    }

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
     * Requests a rebuild if config changed; may be coalesced.
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
        if (!pendingRebuild.getAndSet(false)) {
            return;
        }
        boolean full = pendingFullReload.getAndSet(false);
        rebuildNow(full);
    }

    private static String safeStr(Value v) {
        try {
            if (v == null || v.isNull()) return null;
            return v.isString() ? v.asString() : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        requestRebuild();
    }

    public void setSnapFirstCascades(int count) {
        this.snapFirstCascades = clampInt(count, 0, 8, this.snapFirstCascades);
        requestRebuild();
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        requestRebuild();
    }

    public void setSplitHysteresis(float hysteresis) {
        this.splitHysteresis = Math.max(0f, hysteresis);
        requestRebuild();
    }

    public void setSplitSmoothing(float smoothing) {
        this.splitSmoothing = Math.max(0f, Math.min(1f, smoothing));
        requestRebuild();
    }

    public void setUsePcss(boolean enabled) {
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
        this.shadowZExtend = Math.max(0f, zExtend);
        thread.onJme(() -> {
            if (dlsr != null) dlsr.setShadowZExtend(this.shadowZExtend);
        });
        log.info("[shadow] zExtend={}", this.shadowZExtend);
    }

    public void setShadowZFadeLength(float zFadeLength) {
        this.shadowZFadeLength = Math.max(0f, zFadeLength);
        thread.onJme(() -> {
            if (dlsr != null) dlsr.setShadowZFadeLength(this.shadowZFadeLength);
        });
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

    private RenderManager rm() {
        return app.getRenderManager();
    }

    private static String stablePrimitiveMembersKey(Value cfg) {
        try {
            if (cfg == null || cfg.isNull()) return "";
            if (!cfg.hasMembers()) return "";

            Set<String> keys = cfg.getMemberKeys();
            if (keys == null || keys.isEmpty()) return "";

            String[] kk = keys.toArray(new String[0]);
            Arrays.sort(kk);

            StringBuilder sb = new StringBuilder(64);
            for (String k : kk) {
                Value v = cfg.getMember(k);
                if (v == null || v.isNull()) continue;
                if (v.isNumber() || v.isBoolean() || v.isString()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(k).append("=").append(v.toString());
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
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
            log.warn("[shadow] removeProcessor failed: {}", t.toString());
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
                log.warn("[shadow] deferred cleanup failed: {}", t.toString());
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
            return 8192;
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

    // ---------------------------------------------------------------------
    // JS-defined pipeline types
    // ---------------------------------------------------------------------

    private static boolean vBool(Value cfg, String k, boolean def) {
        try {
            if (cfg != null && !cfg.isNull() && cfg.hasMember(k)) return cfg.getMember(k).asBoolean();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static int vInt(Value cfg, String k, int def) {
        try {
            if (cfg != null && !cfg.isNull() && cfg.hasMember(k)) return cfg.getMember(k).asInt();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static float vFloat(Value cfg, String k, float def) {
        try {
            if (cfg != null && !cfg.isNull() && cfg.hasMember(k)) return (float) cfg.getMember(k).asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }

    public void applyCfg(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        Value src = cfg;
        Value nested = member(cfg, "shadows");
        if (nested != null && !nested.isNull()) {
            src = nested;
        }

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

        // JS-defined pipeline
        ShadowPipelineDef newDef = parsePipeline(src, this.splits);
        String newKey = (newDef == null) ? "" : newDef.key;
        if (!Objects.equals(newKey, this.pipelineKey)) {
            this.pipelineKey = newKey;
            this.pipelineDef = newDef;
            changed = true;
        }

        if (changed) {
            requestRebuild();
        }
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
            log.info("[shadow] disabled");
            return;
        }

        if (lights.primaryLight() == null) {
            DirectionalLightShadowRenderer old = detachOnly();
            deferCleanup(old);
            log.warn("[shadow] cannot enable: primary directional light is null");
            return;
        }

        sanitize();
        int reqMap = mapSize;
        mapSize = clampShadowMapSizeToGpu(mapSize);
        if (mapSize != reqMap) {
            log.warn("[shadow] mapSize clamped by GPU: requested={} -> {}", reqMap, mapSize);
        }

        DirectionalLightShadowRenderer old = detachOnly();

        PipelineDirectionalLightShadowRenderer r =
                new PipelineDirectionalLightShadowRenderer(assets, mapSize, splits);

        if (fixedSplitDistances != null && fixedSplitDistances.length == (splits + 1)) {
            r.setFixedSplitDistances(fixedSplitDistances);
        }

        // ----- Build pipeline -----
        if (pipelineDef != null) {
            buildPipelineFromDef(r, pipelineDef);
        } else {
            // Backward compatible default pipeline
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

            fit.lockNearCascadeSize = true;
            fit.nearTierTexels = 128f;
            fit.nearShrinkHysteresisTiers = 1.0f;

            TemporalSnapGateFilter gate = new TemporalSnapGateFilter();
            gate.setMinRotateDeg(0.25f);
            gate.setMinMoveTexels(1.25f);
            gate.setTeleportMoveTexels(24.0f);
            gate.setGatedFirstCascades(Math.min(1, splits));

            TexelSnapFilter snap = new TexelSnapFilter();
            snap.enabled = snapEnabled;
            snap.snapFirstCascades = snapFirstCascades;
            snap.gate = gate;

            r.pipeline()
                    .add(hyst)
                    .add(basis)
                    .add(fit)
                    .add(gate)
                    .add(snap);
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
                "[shadow] reload={} type={} map={} splits={} lambda={} intensity={} snap={} snapCascades={} pad={} hyst={} smooth={} zExtend={} zFade={} fixedSplits={} pipeline={}",
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

    private ShadowPipelineDef parsePipeline(Value src, int splits) {
        Value arr = member(src, "pipeline");
        if (arr == null || arr.isNull() || !arr.hasArrayElements()) {
            return null;
        }

        int n = (int) arr.getArraySize();
        Step[] out = new Step[n];

        StringBuilder key = new StringBuilder(128);
        key.append("splits=").append(splits).append("|");

        for (int i = 0; i < n; i++) {
            Value e = arr.getArrayElement(i);
            if (e == null || e.isNull()) {
                out[i] = new Step("noop", null);
                key.append("noop;");
                continue;
            }

            String type = null;
            if (e.hasMember("type")) type = safeStr(e.getMember("type"));
            if ((type == null || type.isEmpty()) && e.hasMember("id")) type = safeStr(e.getMember("id"));
            if (type == null || type.isEmpty()) type = "noop";

            Value cfg = e.hasMember("cfg") ? e.getMember("cfg") : e;

            out[i] = new Step(type, cfg);

            key.append(type);
            if (cfg != null && !cfg.isNull()) {
                String mk = stablePrimitiveMembersKey(cfg);
                if (!mk.isEmpty()) key.append("(").append(mk).append(")");
            }
            key.append(";");
        }

        return new ShadowPipelineDef(out, key.toString());
    }

    private Map<String, StepFactory> buildFactoryRegistry() {
        HashMap<String, StepFactory> m = new HashMap<>();

        m.put("noop", (rt, step) -> null);

        m.put("hysteresis", (rt, step) -> {
            CascadeHysteresisFilter f = new CascadeHysteresisFilter();
            Value c = step.cfg;
            f.hysteresis = vFloat(c, "hysteresis", rt.splitHysteresis <= 0f ? 10.0f : rt.splitHysteresis);
            f.smoothing = vFloat(c, "smoothing", rt.splitSmoothing <= 0f ? 0.10f : rt.splitSmoothing);
            return f;
        });

        m.put("basis", (rt, step) -> new StableLightBasisFilter());

        m.put("tightFit", (rt, step) -> {
            TightStableFitShadowCamFilter f = new TightStableFitShadowCamFilter();
            Value c = step.cfg;

            f.xyPadding = vFloat(c, "pad", vFloat(c, "extentsPadding", rt.extentsPadding));
            f.forceSquare = vBool(c, "forceSquare", true);

            f.sizeQuantizeTexels = vFloat(c, "sizeQuantizeTexels", 1.0f);
            f.minNear = vFloat(c, "minNear", 0.5f);

            f.casterBackBase = vFloat(c, "casterBackBase", 140f);
            f.casterBackCascadeMul = vFloat(c, "casterBackCascadeMul", 0.9f);
            f.receiverFrontBase = vFloat(c, "receiverFrontBase", 40f);

            f.lockNearCascadeSize = vBool(c, "lockNearCascadeSize", true);
            f.nearTierTexels = vFloat(c, "nearTierTexels", 128f);
            f.nearShrinkHysteresisTiers = vFloat(c, "nearShrinkHysteresisTiers", 1.0f);

            return f;
        });

        m.put("temporalGate", (rt, step) -> {
            TemporalSnapGateFilter g = new TemporalSnapGateFilter();
            Value c = step.cfg;

            g.setEnabled(vBool(c, "enabled", true));
            g.setMinRotateDeg(vFloat(c, "minRotateDeg", 0.25f));
            g.setMinMoveTexels(vFloat(c, "minMoveTexels", 1.25f));
            g.setTeleportMoveTexels(vFloat(c, "teleportMoveTexels", 24.0f));
            g.setGatedFirstCascades(vInt(c, "gatedFirstCascades", Math.min(1, rt.splits)));

            return g;
        });

        m.put("texelSnap", (rt, step) -> {
            TexelSnapFilter s = new TexelSnapFilter();
            Value c = step.cfg;

            s.enabled = vBool(c, "enabled", rt.snapEnabled);
            s.snapFirstCascades = vInt(c, "snapFirstCascades", rt.snapFirstCascades);

            return s;
        });

        m.put("trace", (rt, step) -> {
            ShadowTraceFilter t = new ShadowTraceFilter();
            Value c = step.cfg;

            t.setEnabled(vBool(c, "enabled", true));
            t.setEveryFrames(vInt(c, "everyFrames", 60));
            t.setAllSplits(vBool(c, "allSplits", false));
            return t;
        });

        return m;
    }

    private void buildPipelineFromDef(PipelineDirectionalLightShadowRenderer r, ShadowPipelineDef def) {
        ShadowPipelineRuntime rt = new ShadowPipelineRuntime(
                mapSize, splits,
                snapEnabled, snapFirstCascades,
                extentsPadding,
                splitHysteresis, splitSmoothing
        );

        Map<String, StepFactory> reg = buildFactoryRegistry();

        TemporalSnapGateFilter gate = null;
        TexelSnapFilter snap = null;

        for (Step step : def.steps) {
            StepFactory fac = reg.get(step.type);
            if (fac == null) {
                log.warn("[shadow] unknown pipeline step type='{}' => skipped", step.type);
                continue;
            }

            ShadowFilter f = fac.create(rt, step);
            if (f == null) continue;

            r.pipeline().add(f);

            if (f instanceof TemporalSnapGateFilter g) gate = g;
            if (f instanceof TexelSnapFilter s) snap = s;
        }

        if (snap != null) {
            snap.gate = gate;
        }
    }

    private interface StepFactory {
        ShadowFilter create(ShadowPipelineRuntime rt, Step step);
    }

    private static final class ShadowPipelineDef {
        final Step[] steps;
        final String key;

        ShadowPipelineDef(Step[] steps, String key) {
            this.steps = steps;
            this.key = key;
        }
    }

    private static final class Step {
        final String type;
        final Value cfg;

        Step(String type, Value cfg) {
            this.type = type;
            this.cfg = cfg;
        }
    }

    private static final class ShadowPipelineRuntime {
        final int mapSize;
        final int splits;
        final boolean snapEnabled;
        final int snapFirstCascades;
        final float extentsPadding;
        final float splitHysteresis;
        final float splitSmoothing;

        ShadowPipelineRuntime(int mapSize, int splits,
                              boolean snapEnabled, int snapFirstCascades,
                              float extentsPadding,
                              float splitHysteresis, float splitSmoothing) {
            this.mapSize = mapSize;
            this.splits = splits;
            this.snapEnabled = snapEnabled;
            this.snapFirstCascades = snapFirstCascades;
            this.extentsPadding = extentsPadding;
            this.splitHysteresis = splitHysteresis;
            this.splitSmoothing = splitSmoothing;
        }
    }
}