// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsJs.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        final int cap = (kv == null) ? 16 : Math.max(16, (kv.length / 2) * 2);
        HashMap<String, Object> out = new HashMap<>(cap);
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    /**
     * Real JS object payload.
     */
    public static ProxyObject evtJs(Object... kv) {
        final int cap = (kv == null) ? 16 : Math.max(16, (kv.length / 2) * 2);
        HashMap<String, Object> out = new HashMap<>(cap);
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                out.put(String.valueOf(kv[i]), js(kv[i + 1]));
            }
        }
        return ProxyObject.fromMap(out);
    }

    /**
     * Best-effort conversion into JS-friendly values.
     * Avoids deep/recursive conversions except for Map (shallow).
     */
    public static Object js(Object v) {
        if (v == null) return null;

        if (v instanceof ProxyObject) return v;
        if (v instanceof Vector3f vec) return jsVec3(vec);
        if (v instanceof Quaternion q) return jsQuat(q);

        if (v instanceof Value gv) {
            if (gv.isHostObject()) return gv.asHostObject();
            if (gv.isNull()) return null;
            if (gv.isBoolean()) return gv.asBoolean();
            if (gv.isNumber()) return gv.asDouble();
            if (gv.isString()) return gv.asString();
            // For unknown shapes: provide a dynamic live view.
            if (gv.hasMembers()) return jsValueLive(gv);
            return gv;
        }

        if (v instanceof Map<?, ?> map) {
            HashMap<String, Object> m = new HashMap<>(Math.max(16, map.size() * 2));
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                m.put(String.valueOf(e.getKey()), js(e.getValue()));
            }
            return ProxyObject.fromMap(m);
        }

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
    public static ProxyObject jsVec3Live(Vector3f ref) {
        Objects.requireNonNull(ref, "ref");
        return new ProxyObject() {
            private static final Set<String> KEYS = Set.of("x", "y", "z");

            @Override
            public Object getMember(String key) {
                return switch (key) {
                    case "x" -> ref.x;
                    case "y" -> ref.y;
                    case "z" -> ref.z;
                    default -> null;
                };
            }

            @Override
            public Object getMemberKeys() {
                return KEYS.toArray(new String[0]);
            }

            @Override
            public boolean hasMember(String key) {
                return KEYS.contains(key);
            }

            @Override
            public void putMember(String key, Value value) {
                // Immutable view on purpose.
            }
        };
    }

    /**
     * Dynamic live view over a Graal {@link Value} object with unknown keys.
     * Keys are snapshotted once to avoid per-access enumeration cost.
     */
    public static ProxyObject jsValueLive(Value v) {
        Objects.requireNonNull(v, "v");
        final String[] keys;
        try {
            keys = v.getMemberKeys().toArray(new String[0]);
        } catch (Throwable ignored) {
            return ProxyObject.fromMap(Map.of());
        }
        return new ProxyObject() {
            @Override
            public Object getMember(String key) {
                try {
                    if (!v.hasMember(key)) return null;
                    return js(v.getMember(key));
                } catch (Throwable ignored) {
                    return null;
                }
            }

            @Override
            public Object getMemberKeys() {
                return keys;
            }

            @Override
            public boolean hasMember(String key) {
                if (key == null) return false;
                for (String k : keys) {
                    if (key.equals(k)) return true;
                }
                return false;
            }

            @Override
            public void putMember(String key, Value value) {
                // Immutable view on purpose.
            }
        };
    }
}