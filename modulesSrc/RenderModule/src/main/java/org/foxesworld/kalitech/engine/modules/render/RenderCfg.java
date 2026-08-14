/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render;

import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class RenderCfg {
    private RenderCfg() {
    }

    public static double numPath(LuaValueRef cfg, String objKey, String key, double def) {
        LuaValueRef o = LuaCfg.member((LuaValueRef)cfg, (String)objKey);
        if (o == null) {
            return def;
        }
        return LuaCfg.num((LuaValueRef)o, (String)key, (double)def);
    }

    public static float vec3x(LuaValueRef v, float def) {
        LuaValueRef m;
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.hasMember("x") && (m = v.getMember("x")) != null && !m.isNull()) {
            return (float)m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 0L) {
            return (float)v.getArrayElement(0L).asDouble();
        }
        return def;
    }

    public static float vec3y(LuaValueRef v, float def) {
        LuaValueRef m;
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.hasMember("y") && (m = v.getMember("y")) != null && !m.isNull()) {
            return (float)m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 1L) {
            return (float)v.getArrayElement(1L).asDouble();
        }
        return def;
    }

    public static float vec3z(LuaValueRef v, float def) {
        LuaValueRef m;
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.hasMember("z") && (m = v.getMember("z")) != null && !m.isNull()) {
            return (float)m.asDouble();
        }
        if (v.hasArrayElements() && v.getArraySize() > 2L) {
            return (float)v.getArrayElement(2L).asDouble();
        }
        return def;
    }

    public static boolean approx(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return false;
        }
        return Math.abs(a - b) <= 1.0E-6f;
    }

    public static boolean approx3(float ax, float ay, float az, float bx, float by, float bz) {
        if (Float.isNaN(ax) || Float.isNaN(ay) || Float.isNaN(az)) {
            return false;
        }
        if (Float.isNaN(bx) || Float.isNaN(by) || Float.isNaN(bz)) {
            return false;
        }
        return Math.abs(ax - bx) <= 1.0E-6f && Math.abs(ay - by) <= 1.0E-6f && Math.abs(az - bz) <= 1.0E-6f;
    }

    public static float clamp01(float v) {
        if (v < 0.0f) {
            return 0.0f;
        }
        if (v > 1.0f) {
            return 1.0f;
        }
        return v;
    }

    public static float clamp(float v, float min, float max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}

