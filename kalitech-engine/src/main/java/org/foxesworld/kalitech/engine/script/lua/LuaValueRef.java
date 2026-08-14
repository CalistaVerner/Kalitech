package org.foxesworld.kalitech.engine.script.lua;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Engine-owned handle for values crossing the Java/Lua boundary.
 */
public final class LuaValueRef {

    private final LuaValue value;

    private LuaValueRef(LuaValue value) {
        this.value = value == null ? LuaValue.NIL : value;
    }

    public static LuaValueRef of(LuaValue value) {
        return new LuaValueRef(value);
    }

    public static LuaValueRef fromJava(Object value) {
        if (value instanceof LuaValueRef ref) return ref;
        return new LuaValueRef(LuaHostProxy.wrap(value));
    }

    public LuaValue asLuaValue() {
        return value;
    }

    public boolean isNull() {
        return value.isnil();
    }

    public boolean isBoolean() {
        return value.isboolean();
    }

    public boolean isNumber() {
        return value.isnumber();
    }

    public boolean isString() {
        return value.isstring();
    }

    public boolean isHostObject() {
        return LuaHostProxy.unwrapHost(value) != null || value.isuserdata();
    }

    public boolean fitsInInt() {
        if (!value.isnumber()) return false;
        double number = value.todouble();
        return Double.isFinite(number)
                && number >= Integer.MIN_VALUE
                && number <= Integer.MAX_VALUE
                && number == Math.rint(number);
    }

    public boolean fitsInLong() {
        if (!value.isnumber()) return false;
        double number = value.todouble();
        return Double.isFinite(number)
                && number >= Long.MIN_VALUE
                && number <= Long.MAX_VALUE
                && number == Math.rint(number);
    }

    public boolean fitsInDouble() {
        return value.isnumber();
    }

    public boolean asBoolean() {
        return value.toboolean();
    }

    public double asDouble() {
        return value.checkdouble();
    }

    public int asInt() {
        return value.checkint();
    }

    public long asLong() {
        return value.checklong();
    }

    public String asString() {
        return value.checkjstring();
    }

    public boolean hasArrayElements() {
        return value.istable() && arraySize(value.checktable()) > 0;
    }

    public long getArraySize() {
        return value.istable() ? arraySize(value.checktable()) : 0L;
    }

    public LuaValueRef getArrayElement(long index) {
        if (!value.istable()) return of(LuaValue.NIL);
        long luaIndex = index + 1L;
        if (luaIndex > Integer.MAX_VALUE) return of(LuaValue.NIL);
        return of(value.get((int) luaIndex));
    }

    public void setArrayElement(long index, Object newValue) {
        if (!value.istable()) throw new IllegalStateException("Lua value has no array elements");
        long luaIndex = index + 1L;
        if (luaIndex > Integer.MAX_VALUE) throw new IndexOutOfBoundsException(Long.toString(index));
        value.set((int) luaIndex, LuaHostProxy.wrap(newValue));
    }

    public boolean hasMembers() {
        return value.istable();
    }

    public boolean hasMember(String key) {
        return key != null && value.istable() && !value.get(key).isnil();
    }

    public LuaValueRef getMember(String key) {
        if (key == null || !value.istable()) return of(LuaValue.NIL);
        return of(value.get(key));
    }

    public void putMember(String key, Object member) {
        if (key == null || !value.istable()) throw new IllegalStateException("Lua value has no members");
        value.set(key, LuaHostProxy.wrap(member));
    }

    public boolean removeMember(String key) {
        if (key == null || !value.istable() || value.get(key).isnil()) return false;
        value.set(key, LuaValue.NIL);
        return true;
    }

    public Set<String> getMemberKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (!value.istable()) return keys;

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = value.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (key instanceof LuaString) keys.add(key.tojstring());
        }
        return keys;
    }

    private static LuaValue callMetamethod(LuaValue candidate) {
        if (candidate == null || !candidate.istable()) return LuaValue.NIL;
        LuaValue metatable = candidate.getmetatable();
        if (metatable == null || metatable.isnil()) return LuaValue.NIL;
        LuaValue call = metatable.get("__call");
        return call != null && call.isfunction() ? call : LuaValue.NIL;
    }

    public boolean canExecute() {
        return value.isfunction() || callMetamethod(value).isfunction();
    }

    public LuaValueRef execute(Object... args) {
        try (LuaExecutionLimiter.Scope ignored =
                     LuaExecutionLimiter.enterCallback("value.execute")) {
            return executeValue(args);
        }
    }

    public LuaValueRef executeLifecycle(String label, Object... args) {
        try (LuaExecutionLimiter.Scope ignored =
                     LuaExecutionLimiter.enterLifecycle(label)) {
            return executeValue(args);
        }
    }

    public void executeVoid(Object... args) {
        execute(args);
    }

    public LuaValueRef invokeMember(String name, Object... args) {
        try (LuaExecutionLimiter.Scope ignored =
                     LuaExecutionLimiter.enterCallback("member:" + name)) {
            return invokeMemberValue(name, args);
        }
    }

    public LuaValueRef invokeMemberLifecycle(String label, String name, Object... args) {
        try (LuaExecutionLimiter.Scope ignored =
                     LuaExecutionLimiter.enterLifecycle(label)) {
            return invokeMemberValue(name, args);
        }
    }

    private LuaValueRef executeValue(Object[] args) {
        if (value.isfunction()) {
            LuaValue[] converted = convertArgs(args, 1, LuaValue.NIL);
            return of(value.invoke(LuaValue.varargsOf(converted)).arg1());
        }

        LuaValue call = callMetamethod(value);
        if (!call.isfunction()) throw new IllegalStateException("Lua value is not executable");
        LuaValue[] converted = convertArgs(args, 1, value);
        return of(call.invoke(LuaValue.varargsOf(converted)).arg1());
    }

    private LuaValueRef invokeMemberValue(String name, Object[] args) {
        if (!value.istable()) throw new IllegalStateException("Lua value has no members");
        LuaValue function = value.get(name);
        if (!function.isfunction()) throw new IllegalStateException("Lua member is not executable: " + name);
        LuaValue[] converted = convertArgs(args, 1, value);
        return of(function.invoke(LuaValue.varargsOf(converted)).arg1());
    }

    public Object asHostObject() {
        Object host = LuaHostProxy.unwrapHost(value);
        if (host != null) return host;
        return value.isuserdata() ? value.touserdata() : null;
    }

    public <T> T as(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("type is null");
        Object converted = toJava(value);
        if (type == Object.class) return type.cast(converted);
        if (converted == null) return null;
        if (type.isInstance(converted)) return type.cast(converted);

        if (type == String.class) return type.cast(value.tojstring());
        if ((type == Integer.class || type == int.class) && value.isnumber()) {
            @SuppressWarnings("unchecked") T out = (T) Integer.valueOf(value.toint());
            return out;
        }
        if ((type == Long.class || type == long.class) && value.isnumber()) {
            @SuppressWarnings("unchecked") T out = (T) Long.valueOf(value.tolong());
            return out;
        }
        if ((type == Double.class || type == double.class) && value.isnumber()) {
            @SuppressWarnings("unchecked") T out = (T) Double.valueOf(value.todouble());
            return out;
        }
        if ((type == Boolean.class || type == boolean.class) && value.isboolean()) {
            @SuppressWarnings("unchecked") T out = (T) Boolean.valueOf(value.toboolean());
            return out;
        }
        throw new ClassCastException("Cannot convert Lua value to " + type.getName());
    }

    private static LuaValue[] convertArgs(Object[] args, int prefix, LuaValue first) {
        Object[] safe = args == null ? new Object[0] : args;
        LuaValue[] converted = new LuaValue[safe.length + prefix];
        if (prefix != 0) converted[0] = first == null ? LuaValue.NIL : first;
        for (int i = 0; i < safe.length; i++) converted[i + prefix] = LuaHostProxy.wrap(safe[i]);
        return converted;
    }

    private static long arraySize(LuaTable table) {
        int length = 0;
        while (!table.get(length + 1).isnil()) length++;
        return length;
    }

    private static Object toJava(LuaValue lua) {
        Object host = LuaHostProxy.unwrapHost(lua);
        if (host != null) return host;
        if (lua.isnil()) return null;
        if (lua.isboolean()) return lua.toboolean();
        if (lua.isint()) return lua.toint();
        if (lua.isnumber()) return lua.todouble();
        if (lua.isstring()) return lua.tojstring();
        if (lua.isfunction()) return LuaValueRef.of(lua);
        if (lua.isuserdata()) return lua.touserdata();
        if (!lua.istable()) return lua.tojstring();

        LuaTable table = lua.checktable();
        long length = arraySize(table);
        Set<String> memberKeys = LuaValueRef.of(lua).getMemberKeys();
        if (length > 0 && memberKeys.isEmpty()) {
            List<Object> list = new ArrayList<>((int) length);
            for (int i = 1; i <= length; i++) list.add(toJava(table.get(i)));
            return list;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : memberKeys) map.put(key, toJava(table.get(key)));
        if (length > 0) {
            List<Object> array = new ArrayList<>((int) length);
            for (int i = 1; i <= length; i++) array.add(toJava(table.get(i)));
            map.put("_array", array);
        }
        return map;
    }

    @Override
    public String toString() {
        return value.tojstring();
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LuaValueRef ref && value.raweq(ref.value);
    }
}
