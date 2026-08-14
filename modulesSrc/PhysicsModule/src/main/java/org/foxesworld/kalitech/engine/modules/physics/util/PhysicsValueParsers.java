/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.Vector3f;
import java.util.Map;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class PhysicsValueParsers {
    private PhysicsValueParsers() {
    }

    public static Object member(Object obj, String key) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LuaValueRef) {
            LuaValueRef v = (LuaValueRef)obj;
            return v.hasMember(key) ? v.getMember(key) : null;
        }
        if (obj instanceof Map) {
            Map m = (Map)obj;
            return m.get(key);
        }
        return null;
    }

    public static boolean asBool(Object v, boolean def) {
        if (v instanceof Boolean) {
            Boolean b = (Boolean)v;
            return b;
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef val = (LuaValueRef)v;
            return val.isBoolean() ? val.asBoolean() : def;
        }
        return def;
    }

    public static double asNum(Object v, double def) {
        if (v instanceof Number) {
            Number n = (Number)v;
            return n.doubleValue();
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef val = (LuaValueRef)v;
            return val.isNumber() ? val.asDouble() : def;
        }
        return def;
    }

    public static float asFloat(Object v, float def) {
        if (v instanceof Number) {
            Number n = (Number)v;
            return n.floatValue();
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef val = (LuaValueRef)v;
            return val.isNumber() ? (float)val.asDouble() : def;
        }
        return def;
    }

    public static int asInt(Object v, int def) {
        if (v instanceof Number) {
            Number n = (Number)v;
            return n.intValue();
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef val = (LuaValueRef)v;
            return val.isNumber() ? val.asInt() : def;
        }
        return def;
    }

    public static void vec3Into(Object v, Vector3f out, float dx, float dy, float dz) {
        if (out == null) {
            return;
        }
        if (v == null) {
            out.set(dx, dy, dz);
            return;
        }
        if (v instanceof Vector3f) {
            Vector3f vv = (Vector3f)v;
            out.set(vv);
            return;
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef val = (LuaValueRef)v;
            if (val.isNull()) {
                out.set(dx, dy, dz);
                return;
            }
            if (val.hasArrayElements() && val.getArraySize() >= 3L) {
                out.set(PhysicsValueParsers.asFloat(val.getArrayElement(0L), dx), PhysicsValueParsers.asFloat(val.getArrayElement(1L), dy), PhysicsValueParsers.asFloat(val.getArrayElement(2L), dz));
                return;
            }
            if (val.hasMembers()) {
                out.set(PhysicsValueParsers.asFloat(PhysicsValueParsers.member(val, "x"), dx), PhysicsValueParsers.asFloat(PhysicsValueParsers.member(val, "y"), dy), PhysicsValueParsers.asFloat(PhysicsValueParsers.member(val, "z"), dz));
                return;
            }
        }
        if (v instanceof Map) {
            Map m = (Map)v;
            out.set(PhysicsValueParsers.asFloat(m.get("x"), dx), PhysicsValueParsers.asFloat(m.get("y"), dy), PhysicsValueParsers.asFloat(m.get("z"), dz));
            return;
        }
        out.set(dx, dy, dz);
    }

    public static Vector3f vec3(Object v, float dx, float dy, float dz) {
        Vector3f out = new Vector3f();
        PhysicsValueParsers.vec3Into(v, out, dx, dy, dz);
        return out;
    }
}

