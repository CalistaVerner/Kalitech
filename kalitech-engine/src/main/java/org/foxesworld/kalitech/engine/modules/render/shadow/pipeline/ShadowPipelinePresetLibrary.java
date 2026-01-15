// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowPipelinePresetLibrary.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Shadow pipeline preset library.
 * <p>
 * Presets are data-only: they define a list of {@code (type + defaultCfg)} steps.
 * JS may reference presets by name: {@code pipeline: "ultraStable"} or
 * {@code pipeline: {preset:"ultraStable", overrides:[...]}}.
 */
public final class ShadowPipelinePresetLibrary {

    private final Map<String, List<PresetStep>> presets = new HashMap<>();

    public ShadowPipelinePresetLibrary() {
        registerBuiltins();
    }

    /**
     * Optional hook: defaults are not applied automatically because we cannot construct a Graal Value here.
     * If you want defaults to apply even when JS provides only {@code pipeline: "preset"},
     * do one of these:
     * <ul>
     *   <li>Make JS expand preset into an array of steps with cfg objects.</li>
     *   <li>Extend the registry to accept a Java Map as cfg alongside Value.</li>
     * </ul>
     * <p>
     * This method is a no-op placeholder to keep API stable.
     */
    private static void attachDefaultsHint(List<ShadowPipelineRegistry.StepDef> steps) {
        // Intentionally empty.
    }

    public Set<String> names() {
        return new TreeSet<>(presets.keySet());
    }

    public List<PresetStep> get(String name) {
        List<PresetStep> p = presets.get(name);
        return p == null ? null : List.copyOf(p);
    }

    public void register(String name, List<PresetStep> steps) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(steps, "steps");
        presets.put(name, List.copyOf(steps));
    }

    /**
     * Expands a preset into registry-compatible StepDef list.
     * Defaults are emitted as a lightweight JS-like object (ProxyObject is not available here),
     * so we store defaults as a synthetic Value wrapper: we cannot instantiate Value on Java side.
     * Strategy: return StepDef with cfg=null, and let JS overrides supply cfg, OR allow inline defaults
     * by using a special adapter in the orchestrator.
     * <p>
     * For practicality: we return StepDef with cfg=null, and defaults are conveyed via special marker map
     * (see {@link #attachDefaultsHint(List)}). The orchestrator/registry does not depend on this; it is optional.
     */
    public List<ShadowPipelineRegistry.StepDef> expandPresetToSteps(Logger log, String presetName, int splits) {
        if (presetName == null || presetName.isEmpty()) presetName = "default";

        List<PresetStep> p = presets.get(presetName);
        if (p == null) {
            if (log != null) log.warn("[shadow] unknown pipeline preset='{}' => using default", presetName);
            p = presets.get("default");
        }
        if (p == null) {
            // Should never happen, but keep safe.
            return List.of();
        }

        ArrayList<ShadowPipelineRegistry.StepDef> out = new ArrayList<>(p.size());
        for (PresetStep s : p) {
            // cfg is null (defaults are handled via optional hint, or overridden by JS).
            out.add(new ShadowPipelineRegistry.StepDef(s.type, null));
        }

        // Optional: attach defaults hint for external tooling, not required for runtime.
        attachDefaultsHint(out);

        // Optional split-aware behavior can be implemented in presets if needed later.
        return out;
    }

    private void registerBuiltins() {
        // "default" matches your current tuned baseline ordering:
        // hysteresis -> basis -> tightFit -> temporalGate -> texelSnap
        register("default", List.of(
                PresetStep.of("hysteresis", Map.of(
                        "hysteresis", 10.0,
                        "smoothing", 0.10
                )),
                PresetStep.of("basis"),
                PresetStep.of("tightFit", Map.of(
                        "pad", 1.02,
                        "forceSquare", true,
                        "sizeQuantizeTexels", 1.0,
                        "minNear", 0.5,
                        "casterBackBase", 140.0,
                        "casterBackCascadeMul", 0.9,
                        "receiverFrontBase", 40.0,
                        "lockNearCascadeSize", true,
                        "nearTierTexels", 128.0,
                        "nearShrinkHysteresisTiers", 1.0
                )),
                PresetStep.of("temporalGate", Map.of(
                        "minRotateDeg", 0.25,
                        "minMoveTexels", 1.25,
                        "teleportMoveTexels", 24.0,
                        "gatedFirstCascades", 1
                )),
                PresetStep.of("texelSnap", Map.of(
                        "enabled", true,
                        "snapFirstCascades", 2
                ))
        ));

        // "ultraStable": stricter temporal gate + keep snap earlier to kill shimmer aggressively
        register("ultraStable", List.of(
                PresetStep.of("hysteresis", Map.of(
                        "hysteresis", 12.0,
                        "smoothing", 0.08
                )),
                PresetStep.of("basis"),
                PresetStep.of("tightFit", Map.of(
                        "pad", 1.03,
                        "forceSquare", true,
                        "sizeQuantizeTexels", 1.0,
                        "minNear", 0.5,
                        "casterBackBase", 160.0,
                        "casterBackCascadeMul", 0.95,
                        "receiverFrontBase", 45.0,
                        "lockNearCascadeSize", true,
                        "nearTierTexels", 192.0,
                        "nearShrinkHysteresisTiers", 1.0
                )),
                PresetStep.of("temporalGate", Map.of(
                        "minRotateDeg", 0.18,
                        "minMoveTexels", 1.0,
                        "teleportMoveTexels", 32.0,
                        "gatedFirstCascades", 2
                )),
                PresetStep.of("texelSnap", Map.of(
                        "enabled", true,
                        "snapFirstCascades", 3
                ))
        ));

        // "traceDebug": default + trace at the end
        register("traceDebug", List.of(
                PresetStep.of("hysteresis", Map.of(
                        "hysteresis", 10.0,
                        "smoothing", 0.10
                )),
                PresetStep.of("basis"),
                PresetStep.of("tightFit", Map.of(
                        "pad", 1.02,
                        "forceSquare", true,
                        "sizeQuantizeTexels", 1.0,
                        "minNear", 0.5
                )),
                PresetStep.of("temporalGate", Map.of(
                        "minRotateDeg", 0.25,
                        "minMoveTexels", 1.25,
                        "teleportMoveTexels", 24.0,
                        "gatedFirstCascades", 1
                )),
                PresetStep.of("texelSnap", Map.of(
                        "enabled", true,
                        "snapFirstCascades", 2
                )),
                PresetStep.of("trace", Map.of(
                        "enabled", true,
                        "everyFrames", 60,
                        "allSplits", false
                ))
        ));
    }

    /**
     * Data-only preset step.
     */
    public static final class PresetStep {
        public final String type;
        public final Map<String, Object> defaults;

        public PresetStep(String type, Map<String, Object> defaults) {
            this.type = Objects.requireNonNull(type, "type");
            this.defaults = defaults == null ? Map.of() : Map.copyOf(defaults);
        }

        public static PresetStep of(String type) {
            return new PresetStep(type, Map.of());
        }

        public static PresetStep of(String type, Map<String, Object> defaults) {
            return new PresetStep(type, defaults);
        }
    }
}