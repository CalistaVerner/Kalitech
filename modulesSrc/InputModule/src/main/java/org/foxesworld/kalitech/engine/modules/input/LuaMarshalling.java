/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaArray
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.input;

import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class LuaMarshalling {
    private static final LuaArray EMPTY_ARRAY = LuaArray.fromArray((Object[])new Object[0]);
    private static final LuaArray VEC2_KEYS = LuaArray.fromArray((Object[])new Object[]{"x", "y"});
    private static final LuaArray DELTA2_KEYS = LuaArray.fromArray((Object[])new Object[]{"dx", "dy"});

    private LuaMarshalling() {
    }

    public static Object vec2(double x, double y) {
        return new Vec2Proxy(x, y);
    }

    public static Object delta2(double dx, double dy) {
        return new Delta2Proxy(dx, dy);
    }

    static Object intArray(int[] a) {
        return a == null || a.length == 0 ? EMPTY_ARRAY : new IntArrayProxy(a);
    }

    private record Vec2Proxy(double x, double y) implements LuaObject
    {
        public Object getMember(String key) {
            return switch (key) {
                case "x" -> this.x;
                case "y" -> this.y;
                default -> null;
            };
        }

        public Object getMemberKeys() {
            return VEC2_KEYS;
        }

        public boolean hasMember(String key) {
            return "x".equals(key) || "y".equals(key);
        }

        public void putMember(String key, LuaValueRef value) {
        }
    }

    private record Delta2Proxy(double dx, double dy) implements LuaObject
    {
        public Object getMember(String key) {
            return switch (key) {
                case "dx" -> this.dx;
                case "dy" -> this.dy;
                default -> null;
            };
        }

        public Object getMemberKeys() {
            return DELTA2_KEYS;
        }

        public boolean hasMember(String key) {
            return "dx".equals(key) || "dy".equals(key);
        }

        public void putMember(String key, LuaValueRef value) {
        }
    }

    private static final class IntArrayProxy
    implements LuaArray {
        private final int[] a;

        private IntArrayProxy(int[] a) {
            this.a = a;
        }

        public long getSize() {
            return this.a.length;
        }

        public Object get(long index) {
            int i = (int)index;
            if (i < 0 || i >= this.a.length) {
                return 0;
            }
            return this.a[i];
        }

        public void set(long index, LuaValueRef value) {
        }

        public boolean remove(long index) {
            return false;
        }
    }
}

