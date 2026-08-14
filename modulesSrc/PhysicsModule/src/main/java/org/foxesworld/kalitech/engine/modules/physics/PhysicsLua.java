/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class PhysicsLua {
    private PhysicsLua() {
    }

    public static Map<String, Object> evtMap(Object ... kv) {
        int cap = kv == null ? 16 : Math.max(16, kv.length / 2 * 2);
        HashMap<String, Object> out = new HashMap<String, Object>(cap);
        if (kv == null) {
            return out;
        }
        int i = 0;
        while (i + 1 < kv.length) {
            out.put(String.valueOf(kv[i]), kv[i + 1]);
            i += 2;
        }
        return out;
    }

    public static LuaObject evtLua(Object ... kv) {
        int cap = kv == null ? 16 : Math.max(16, kv.length / 2 * 2);
        HashMap<String, Object> out = new HashMap<String, Object>(cap);
        if (kv != null) {
            int i = 0;
            while (i + 1 < kv.length) {
                out.put(String.valueOf(kv[i]), PhysicsLua.luaValue(kv[i + 1]));
                i += 2;
            }
        }
        return LuaObject.fromMap(out);
    }

    public static Object luaValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LuaObject) {
            return v;
        }
        if (v instanceof Vector3f) {
            Vector3f vec = (Vector3f)v;
            return PhysicsLua.luaVec3(vec);
        }
        if (v instanceof Quaternion) {
            Quaternion q = (Quaternion)v;
            return PhysicsLua.luaQuat(q);
        }
        if (v instanceof LuaValueRef) {
            LuaValueRef gv = (LuaValueRef)v;
            if (gv.isHostObject()) {
                return gv.asHostObject();
            }
            if (gv.isNull()) {
                return null;
            }
            if (gv.isBoolean()) {
                return gv.asBoolean();
            }
            if (gv.isNumber()) {
                return gv.asDouble();
            }
            if (gv.isString()) {
                return gv.asString();
            }
            if (gv.hasMembers()) {
                return PhysicsLua.luaValueLive(gv);
            }
            return gv;
        }
        if (v instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) v;
            HashMap<String, Object> m = new HashMap<String, Object>(Math.max(16, map.size() * 2));
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                m.put(String.valueOf(e.getKey()), PhysicsLua.luaValue(e.getValue()));
            }
            return LuaObject.fromMap(m);
        }
        if (v instanceof Number || v instanceof String || v instanceof Boolean) {
            return v;
        }
        return v;
    }

    public static LuaObject luaVec3(Vector3f v) {
        HashMap<String, Float> m = new HashMap<String, Float>(4);
        m.put("x", Float.valueOf(v.x));
        m.put("y", Float.valueOf(v.y));
        m.put("z", Float.valueOf(v.z));
        return LuaObject.fromMap(m);
    }

    public static LuaObject luaQuat(Quaternion q) {
        HashMap<String, Float> m = new HashMap<String, Float>(6);
        m.put("x", Float.valueOf(q.getX()));
        m.put("y", Float.valueOf(q.getY()));
        m.put("z", Float.valueOf(q.getZ()));
        m.put("w", Float.valueOf(q.getW()));
        return LuaObject.fromMap(m);
    }

    public static LuaObject luaVec3Live(final Vector3f ref) {
        Objects.requireNonNull(ref, "ref");
        return new LuaObject(){
            private static final Set<String> KEYS = Set.of("x", "y", "z");

            public Object getMember(String key) {
                return switch (key) {
                    case "x" -> Float.valueOf(ref.x);
                    case "y" -> Float.valueOf(ref.y);
                    case "z" -> Float.valueOf(ref.z);
                    default -> null;
                };
            }

            public Object getMemberKeys() {
                return KEYS.toArray(new String[0]);
            }

            public boolean hasMember(String key) {
                return KEYS.contains(key);
            }

            public void putMember(String key, LuaValueRef value) {
            }
        };
    }

    public static LuaObject luaValueLive(final LuaValueRef v) {
        String[] keys;
        Objects.requireNonNull(v, "v");
        try {
            keys = v.getMemberKeys().toArray(new String[0]);
        }
        catch (Throwable ignored) {
            return LuaObject.fromMap(Map.of());
        }
        return new LuaObject(){

            public Object getMember(String key) {
                try {
                    if (!v.hasMember(key)) {
                        return null;
                    }
                    return PhysicsLua.luaValue(v.getMember(key));
                }
                catch (Throwable ignored) {
                    return null;
                }
            }

            public Object getMemberKeys() {
                return keys;
            }

            public boolean hasMember(String key) {
                if (key == null) {
                    return false;
                }
                for (String k : keys) {
                    if (!key.equals(k)) continue;
                    return true;
                }
                return false;
            }

            public void putMember(String key, LuaValueRef value) {
            }
        };
    }
}

