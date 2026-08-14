package org.foxesworld.kalitech.engine.script.util;

import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State capsule sanitizer: converts Lua Values into JSON-safe data.
 * Host objects/functions are stripped to avoid keeping live engine references.
 */
public final class StateCapsule {

    private static final int MAX_DEPTH = 24;

    private StateCapsule() {
    }

    public static Object toState(LuaValueRef v) {
        return toState(v, 0);
    }

    private static Object toState(LuaValueRef v, int depth) {
        if (v == null || v.isNull()) return null;
        if (depth > MAX_DEPTH) return null;

        if (v.isHostObject()) return null;
        if (v.canExecute()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();

        if (v.hasArrayElements()) {
            int len = (int) Math.min(v.getArraySize(), Integer.MAX_VALUE);
            Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) {
                arr[i] = toState(v.getArrayElement(i), depth + 1);
            }
            return LuaArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) {
                out.put(k, toState(v.getMember(k), depth + 1));
            }
            return LuaObject.fromMap(out);
        }

        return null;
    }
}
