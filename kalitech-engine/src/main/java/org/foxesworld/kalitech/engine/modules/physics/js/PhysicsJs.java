// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsJs.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.js;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JS payload builders for GraalJS:
 * - evtJs(...) returns ProxyObject (real JS value)
 * - jsVec3Live(...) returns live Vec3 ProxyObject
 */
public final class PhysicsJs {

    private PhysicsJs() {
    }

    /**
     * Legacy Java payload (kept for internal uses).
     */
    public static Map<String, Object> evtMap(Object... kv) {
        HashMap<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    /**
     * Real JS object payload.
     */
    public static ProxyObject evtJs(Object... kv) {
        HashMap<String, Object> out = new HashMap<>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                out.put(String.valueOf(kv[i]), js(kv[i + 1]));
            }
        }
        return ProxyObject.fromMap(out);
    }

    /**
     * Best-effort conversion into JS-friendly values.
     */
    public static Object js(Object v) {
        if (v == null) return null;

        if (v instanceof ProxyObject) return v;
        if (v instanceof Vector3f vec) return jsVec3(vec);
        if (v instanceof Quaternion q) return jsQuat(q);

        if (v instanceof Map<?, ?> map) {
            HashMap<String, Object> m = new HashMap<>(Math.max(16, map.size() * 2));
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                m.put(String.valueOf(e.getKey()), js(e.getValue()));
            }
            return ProxyObject.fromMap(m);
        }

        // primitives are ok
        if (v instanceof Number || v instanceof String || v instanceof Boolean) return v;

        return v;
    }

    public static ProxyObject jsVec3(Vector3f v) {
        HashMap<String, Object> m = new HashMap<>(4);
        m.put("x", v.x);
        m.put("y", v.y);
        m.put("z", v.z);
        return ProxyObject.fromMap(m);
    }

    public static ProxyObject jsQuat(Quaternion q) {
        HashMap<String, Object> m = new HashMap<>(6);
        m.put("x", q.getX());
        m.put("y", q.getY());
        m.put("z", q.getZ());
        m.put("w", q.getW());
        return ProxyObject.fromMap(m);
    }

    /**
     * Live vec3 proxy: reads Vector3f components on every access.
     */
    public static ProxyObject jsVec3Live(Object ref) {
        Objects.requireNonNull(ref, "ref");

        final Map<String, Field> fields = new LinkedHashMap<>();

        for (Field f : ref.getClass().getFields()) {
            if (f.getType() == float.class || f.getType() == double.class) {
                fields.put(f.getName(), f);
            }
        }

        final String[] keys = fields.keySet().toArray(new String[0]);

        return new ProxyObject() {

            @Override
            public Object getMember(String key) {
                Field f = fields.get(key);
                if (f == null) return null;
                try {
                    return f.getType() == float.class
                            ? f.getFloat(ref)
                            : f.getDouble(ref);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }

            @Override
            public Object getMemberKeys() {
                return keys;
            }

            @Override
            public boolean hasMember(String key) {
                return fields.containsKey(key);
            }

            @Override
            public void putMember(String key, Value value) {
                Field f = fields.get(key);
                if (f == null || value == null) return;

                try {
                    if (f.getType() == float.class && value.fitsInFloat()) {
                        f.setFloat(ref, value.asFloat());
                    } else if (f.getType() == double.class && value.fitsInDouble()) {
                        f.setDouble(ref, value.asDouble());
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        };
    }

}