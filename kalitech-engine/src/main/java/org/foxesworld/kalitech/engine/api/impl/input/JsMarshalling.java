package org.foxesworld.kalitech.engine.api.impl.input;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;

final class JsMarshalling {

    static Object vec2(double x, double y) {
        Map<String, Object> m = new HashMap<>(2);
        m.put("x", x);
        m.put("y", y);
        return ProxyObject.fromMap(m);
    }

    static Object delta2(double dx, double dy) {
        Map<String, Object> m = new HashMap<>(2);
        m.put("dx", dx);
        m.put("dy", dy);
        return ProxyObject.fromMap(m);
    }

    // ✅ expose Java int[] to JS as real array-like
    static Object intArray(int[] a) {
        return (a == null || a.length == 0) ? ProxyArray.fromArray() : new IntArrayProxy(a);
    }

    private static final class IntArrayProxy implements ProxyArray {
        private final int[] a;
        private IntArrayProxy(int[] a) { this.a = a; }

        @Override public long getSize() { return a.length; }

        @Override public Object get(long index) {
            int i = (int) index;
            if (i < 0 || i >= a.length) return 0;
            return a[i];
        }

        @Override public void set(long index, Value value) {
            // immutable snapshot — ignore
        }

        @Override public boolean remove(long index) { return false; }
    }
}