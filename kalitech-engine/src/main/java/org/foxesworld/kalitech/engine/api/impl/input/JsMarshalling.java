package org.foxesworld.kalitech.engine.api.impl.input;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

final class JsMarshalling {

    static Object vec2(double x, double y) {
        return new Vec2Proxy(x, y);
    }

    static Object delta2(double dx, double dy) {
        return new Delta2Proxy(dx, dy);
    }

    static Object intArray(int[] a) {
        return (a == null || a.length == 0) ? ProxyArray.fromArray() : new IntArrayProxy(a);
    }

    private static final class IntArrayProxy implements ProxyArray {
        private final int[] a;
        private IntArrayProxy(int[] a) { this.a = a; }

        @Override public long getSize() { return a.length; }

        @Override public Object get(long index) {
            int i = (int) index;
            return (i < 0 || i >= a.length) ? 0 : a[i];
        }

        @Override public void set(long index, Value value) {}
        @Override public boolean remove(long index) { return false; }
    }

    private record Vec2Proxy(double x, double y) implements ProxyObject {
        @Override public Object getMember(String key) {
            return switch (key) { case "x" -> x; case "y" -> y; default -> null; };
        }
        @Override public Object getMemberKeys() { return ProxyArray.fromArray("x", "y"); }
        @Override public boolean hasMember(String key) { return "x".equals(key) || "y".equals(key); }
        @Override public void putMember(String key, Value value) {}
    }

    private record Delta2Proxy(double dx, double dy) implements ProxyObject {
        @Override public Object getMember(String key) {
            return switch (key) { case "dx" -> dx; case "dy" -> dy; default -> null; };
        }
        @Override public Object getMemberKeys() { return ProxyArray.fromArray("dx", "dy"); }
        @Override public boolean hasMember(String key) { return "dx".equals(key) || "dy".equals(key); }
        @Override public void putMember(String key, Value value) {}
    }
}