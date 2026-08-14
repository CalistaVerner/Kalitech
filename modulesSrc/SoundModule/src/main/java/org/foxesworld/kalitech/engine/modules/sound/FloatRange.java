/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.sound;

import org.foxesworld.kalitech.engine.modules.sound.SoundDeterminism;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class FloatRange {
    public final float min;
    public final float max;

    public FloatRange(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public static FloatRange parse(LuaValueRef cfg, String key, float def, float clampMin, float clampMax) {
        if (cfg == null || cfg.isNull() || !LuaCfg.has((LuaValueRef)cfg, (String)key)) {
            float v = FloatRange.clamp(def, clampMin, clampMax);
            return new FloatRange(v, v);
        }
        LuaValueRef v = LuaCfg.member((LuaValueRef)cfg, (String)key);
        if (v == null || v.isNull()) {
            float x = FloatRange.clamp(def, clampMin, clampMax);
            return new FloatRange(x, x);
        }
        if (v.hasArrayElements() && v.getArraySize() >= 2L) {
            float a = (float)v.getArrayElement(0L).asDouble();
            float b = (float)v.getArrayElement(1L).asDouble();
            float lo = FloatRange.clamp(Math.min(a, b), clampMin, clampMax);
            float hi = FloatRange.clamp(Math.max(a, b), clampMin, clampMax);
            return new FloatRange(lo, hi);
        }
        float x = FloatRange.clamp((float)v.asDouble(), clampMin, clampMax);
        return new FloatRange(x, x);
    }

    private static float clamp(float v, float lo, float hi) {
        if (lo > hi) {
            float t = lo;
            lo = hi;
            hi = t;
        }
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    public float sample(long seed, int salt) {
        if (this.min == this.max) {
            return this.min;
        }
        float t = SoundDeterminism.nextFloat01(seed, salt);
        return this.min + (this.max - this.min) * t;
    }
}

