// FILE: org/foxesworld/kalitech/engine/modules/sound/FloatRange.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.has;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;

public final class FloatRange {

    public final float min;
    public final float max;

    public FloatRange(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public static FloatRange parse(Value cfg, String key, float def, float clampMin, float clampMax) {
        if (cfg == null || cfg.isNull() || !has(cfg, key)) {
            float v = clamp(def, clampMin, clampMax);
            return new FloatRange(v, v);
        }

        Value v = member(cfg, key);
        if (v == null || v.isNull()) {
            float x = clamp(def, clampMin, clampMax);
            return new FloatRange(x, x);
        }

        if (v.hasArrayElements() && v.getArraySize() >= 2) {
            float a = (float) v.getArrayElement(0).asDouble();
            float b = (float) v.getArrayElement(1).asDouble();
            float lo = clamp(Math.min(a, b), clampMin, clampMax);
            float hi = clamp(Math.max(a, b), clampMin, clampMax);
            return new FloatRange(lo, hi);
        }

        float x = clamp((float) v.asDouble(), clampMin, clampMax);
        return new FloatRange(x, x);
    }

    private static float clamp(float v, float lo, float hi) {
        return RenderCfg.clamp(v, lo, hi);
    }

    public float sample(long seed, int salt) {
        if (min == max) return min;
        float t = SoundDeterminism.nextFloat01(seed, salt);
        return min + (max - min) * t;
    }
}