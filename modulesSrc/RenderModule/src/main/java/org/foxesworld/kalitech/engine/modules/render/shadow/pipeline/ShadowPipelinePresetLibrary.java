/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelineRegistry;

public final class ShadowPipelinePresetLibrary {
    private final Map<String, List<PresetStep>> presets = new HashMap<String, List<PresetStep>>();

    public ShadowPipelinePresetLibrary() {
        this.registerBuiltins();
    }

    private static void attachDefaultsHint(List<ShadowPipelineRegistry.StepDef> steps) {
    }

    public Set<String> names() {
        return new TreeSet<String>(this.presets.keySet());
    }

    public List<PresetStep> get(String name) {
        List<PresetStep> p = this.presets.get(name);
        return p == null ? null : List.copyOf(p);
    }

    public void register(String name, List<PresetStep> steps) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(steps, "steps");
        this.presets.put(name, List.copyOf(steps));
    }

    public List<ShadowPipelineRegistry.StepDef> expandPresetToSteps(Logger log, String presetName, int splits) {
        List<PresetStep> p;
        if (presetName == null || presetName.isEmpty()) {
            presetName = "default";
        }
        if ((p = this.presets.get(presetName)) == null) {
            if (log != null) {
                log.warn("[shadow] unknown pipeline preset='{}' => using default", (Object)presetName);
            }
            p = this.presets.get("default");
        }
        if (p == null) {
            return List.of();
        }
        ArrayList<ShadowPipelineRegistry.StepDef> out = new ArrayList<ShadowPipelineRegistry.StepDef>(p.size());
        for (PresetStep s : p) {
            out.add(new ShadowPipelineRegistry.StepDef(s.type, null));
        }
        ShadowPipelinePresetLibrary.attachDefaultsHint(out);
        return out;
    }

    private void registerBuiltins() {
        this.register("default", List.of(PresetStep.of("hysteresis", Map.of("hysteresis", 10.0, "smoothing", 0.1)), PresetStep.of("basis"), PresetStep.of("tightFit", Map.of("pad", 1.02, "forceSquare", true, "sizeQuantizeTexels", 1.0, "minNear", 0.5, "casterBackBase", 140.0, "casterBackCascadeMul", 0.9, "receiverFrontBase", 40.0, "lockNearCascadeSize", true, "nearTierTexels", 128.0, "nearShrinkHysteresisTiers", 1.0)), PresetStep.of("temporalGate", Map.of("minRotateDeg", 0.25, "minMoveTexels", 1.25, "teleportMoveTexels", 24.0, "gatedFirstCascades", 1)), PresetStep.of("texelSnap", Map.of("enabled", true, "snapFirstCascades", 2))));
        this.register("ultraStable", List.of(PresetStep.of("hysteresis", Map.of("hysteresis", 12.0, "smoothing", 0.08)), PresetStep.of("basis"), PresetStep.of("tightFit", Map.of("pad", 1.03, "forceSquare", true, "sizeQuantizeTexels", 1.0, "minNear", 0.5, "casterBackBase", 160.0, "casterBackCascadeMul", 0.95, "receiverFrontBase", 45.0, "lockNearCascadeSize", true, "nearTierTexels", 192.0, "nearShrinkHysteresisTiers", 1.0)), PresetStep.of("temporalGate", Map.of("minRotateDeg", 0.18, "minMoveTexels", 1.0, "teleportMoveTexels", 32.0, "gatedFirstCascades", 2)), PresetStep.of("texelSnap", Map.of("enabled", true, "snapFirstCascades", 3))));
        this.register("traceDebug", List.of(PresetStep.of("hysteresis", Map.of("hysteresis", 10.0, "smoothing", 0.1)), PresetStep.of("basis"), PresetStep.of("tightFit", Map.of("pad", 1.02, "forceSquare", true, "sizeQuantizeTexels", 1.0, "minNear", 0.5)), PresetStep.of("temporalGate", Map.of("minRotateDeg", 0.25, "minMoveTexels", 1.25, "teleportMoveTexels", 24.0, "gatedFirstCascades", 1)), PresetStep.of("texelSnap", Map.of("enabled", true, "snapFirstCascades", 2)), PresetStep.of("trace", Map.of("enabled", true, "everyFrames", 60, "allSplits", false))));
    }

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

