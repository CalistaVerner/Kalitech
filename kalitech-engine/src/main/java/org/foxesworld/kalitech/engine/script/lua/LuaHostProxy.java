package org.foxesworld.kalitech.engine.script.lua;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Restricted Java-to-Lua bridge.
 *
 * <p>Only members marked with {@link LuaExport}, directly or through an
 * interface, are visible to scripts. All conversion is handled by LuaJ.</p>
 */
public final class LuaHostProxy {

    private static final Map<LuaValue, Object> HOSTS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private LuaHostProxy() {
    }

    public static LuaValue wrap(Object value) {
        return wrap(value, new IdentityHashMap<>());
    }

    public static Object unwrapHost(LuaValue value) {
        if (value == null) return null;
        Object host = HOSTS.get(value);
        if (host != null) return host;
        return value.isuserdata() ? value.touserdata() : null;
    }

    private static LuaValue wrap(Object value, IdentityHashMap<Object, LuaValue> seen) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof LuaValue lua) return lua;
        if (value instanceof LuaValueRef ref) return ref.asLuaValue();
        if (value instanceof Boolean b) return LuaValue.valueOf(b);
        if (value instanceof Byte n) return LuaValue.valueOf(n.intValue());
        if (value instanceof Short n) return LuaValue.valueOf(n.intValue());
        if (value instanceof Integer n) return LuaValue.valueOf(n);
        if (value instanceof Long n) return LuaValue.valueOf(n.doubleValue());
        if (value instanceof Float n) return LuaValue.valueOf(n.doubleValue());
        if (value instanceof Double n) return LuaValue.valueOf(n);
        if (value instanceof Number n) return LuaValue.valueOf(n.doubleValue());
        if (value instanceof Character c) return LuaValue.valueOf(c.toString());
        if (value instanceof CharSequence s) return LuaValue.valueOf(s.toString());
        if (value instanceof Enum<?> e) return LuaValue.valueOf(e.name());

        LuaValue known = seen.get(value);
        if (known != null) return known;

        if (value instanceof LuaArray proxyArray) {
            LuaTable table = new LuaTable();
            seen.put(value, table);
            long size = proxyArray.getSize();
            for (long i = 0; i < size && i < Integer.MAX_VALUE; i++) {
                table.set((int) i + 1, wrap(proxyArray.get(i), seen));
            }
            return table;
        }

        if (value instanceof LuaObject proxyObject) {
            LuaTable table = new LuaTable();
            seen.put(value, table);
            for (String key : proxyKeys(proxyObject.getMemberKeys())) {
                table.set(key, wrap(proxyObject.getMember(key), seen));
            }
            HOSTS.put(table, value);
            return table;
        }

        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            seen.put(value, table);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) table.set(String.valueOf(entry.getKey()), wrap(entry.getValue(), seen));
            }
            return table;
        }

        if (value instanceof Iterable<?> iterable) {
            LuaTable table = new LuaTable();
            seen.put(value, table);
            int i = 1;
            for (Object item : iterable) table.set(i++, wrap(item, seen));
            return table;
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            LuaTable table = new LuaTable();
            seen.put(value, table);
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) table.set(i + 1, wrap(Array.get(value, i), seen));
            return table;
        }

        return proxyJavaObject(value, seen);
    }

    private static LuaValue proxyJavaObject(Object host, IdentityHashMap<Object, LuaValue> seen) {
        LuaTable proxy = new LuaTable();
        seen.put(host, proxy);
        HOSTS.put(proxy, host);

        Class<?> type = host.getClass();
        for (Field field : type.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && isExported(field)) {
                try {
                    proxy.set(field.getName(), wrap(field.get(host), seen));
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }

        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())) continue;
            if (!isExported(method)) continue;
            methods.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
        }

        for (Map.Entry<String, List<Method>> entry : methods.entrySet()) {
            List<Method> overloads = entry.getValue();
            overloads.sort(Comparator.comparingInt(Method::getParameterCount));
            proxy.set(entry.getKey(), new HostMethod(proxy, host, overloads));
        }
        return proxy;
    }

    private static boolean isExported(Field field) {
        return field.isAnnotationPresent(LuaExport.class);
    }

    private static boolean isExported(Method method) {
        if (method.isAnnotationPresent(LuaExport.class)) return true;
        Class<?> type = method.getDeclaringClass();

        for (Class<?> iface : allInterfaces(type)) {
            try {
                Method declared = iface.getMethod(method.getName(), method.getParameterTypes());
                if (declared.isAnnotationPresent(LuaExport.class)) return true;
            } catch (NoSuchMethodException ignored) {
            }
        }

        Class<?> parent = type.getSuperclass();
        while (parent != null) {
            try {
                Method declared = parent.getMethod(method.getName(), method.getParameterTypes());
                if (declared.isAnnotationPresent(LuaExport.class)) return true;
            } catch (NoSuchMethodException ignored) {
            }
            parent = parent.getSuperclass();
        }
        return false;
    }

    private static Set<Class<?>> allInterfaces(Class<?> type) {
        java.util.LinkedHashSet<Class<?>> out = new java.util.LinkedHashSet<>();
        collectInterfaces(type, out);
        return out;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> out) {
        if (type == null) return;
        for (Class<?> iface : type.getInterfaces()) {
            if (out.add(iface)) collectInterfaces(iface, out);
        }
        collectInterfaces(type.getSuperclass(), out);
    }

    private static Collection<String> proxyKeys(Object raw) {
        ArrayList<String> keys = new ArrayList<>();
        if (raw == null) return keys;
        if (raw instanceof LuaArray array) {
            for (long i = 0; i < array.getSize(); i++) {
                Object item = array.get(i);
                if (item != null) keys.add(String.valueOf(item));
            }
            return keys;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (item != null) keys.add(String.valueOf(item));
            return keys;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(raw, i);
                if (item != null) keys.add(String.valueOf(item));
            }
            return keys;
        }
        keys.add(String.valueOf(raw));
        return keys;
    }

    private static final class HostMethod extends VarArgFunction {
        private final LuaTable proxy;
        private final Object host;
        private final List<Method> overloads;

        private HostMethod(LuaTable proxy, Object host, List<Method> overloads) {
            this.proxy = proxy;
            this.host = host;
            this.overloads = List.copyOf(overloads);
        }

        @Override
        public Varargs invoke(Varargs args) {
            int offset = args.narg() > 0 && args.arg1().raweq(proxy) ? 1 : 0;
            Throwable last = null;

            for (Method method : overloads) {
                try {
                    Object[] converted = convertArguments(method, args, offset);
                    Object result = method.invoke(host, converted);
                    return wrap(result);
                } catch (IllegalArgumentException ex) {
                    last = ex;
                } catch (InvocationTargetException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    throw new LuaError(cause);
                } catch (ReflectiveOperationException ex) {
                    throw new LuaError(ex);
                }
            }

            String name = overloads.isEmpty() ? "<unknown>" : overloads.get(0).getName();
            throw new LuaError("No matching overload for " + host.getClass().getName() + "." + name
                    + " with " + Math.max(0, args.narg() - offset) + " argument(s)"
                    + (last == null ? "" : ": " + last.getMessage()));
        }
    }

    private static Object[] convertArguments(Method method, Varargs args, int offset) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean varArgs = method.isVarArgs();
        int provided = Math.max(0, args.narg() - offset);

        if (!varArgs && provided != parameterTypes.length) throw new IllegalArgumentException("argument count");
        if (varArgs && provided < parameterTypes.length - 1) throw new IllegalArgumentException("argument count");

        Object[] converted = new Object[parameterTypes.length];
        int fixed = varArgs ? parameterTypes.length - 1 : parameterTypes.length;
        for (int i = 0; i < fixed; i++) converted[i] = convert(args.arg(offset + i + 1), parameterTypes[i]);

        if (varArgs) {
            Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
            int count = provided - fixed;
            Object array = Array.newInstance(componentType, count);
            for (int i = 0; i < count; i++) {
                Array.set(array, i, convert(args.arg(offset + fixed + i + 1), componentType));
            }
            converted[converted.length - 1] = array;
        }
        return converted;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(LuaValue lua, Class<?> type) {
        Objects.requireNonNull(type, "type");
        Object host = unwrapHost(lua);
        if (host != null && type.isInstance(host)) return host;

        if (type == Object.class) return toJavaObject(lua);
        if (type == LuaValue.class
                || (LuaValue.class.isAssignableFrom(type) && type.isInstance(lua))) return lua;
        if (type == LuaValueRef.class) return LuaValueRef.of(lua);
        if (lua.isnil()) {
            if (type.isPrimitive()) throw new IllegalArgumentException("nil for primitive");
            return null;
        }

        if (type == String.class || type == CharSequence.class) return lua.tojstring();
        if (type == boolean.class || type == Boolean.class) return lua.toboolean();
        if (type == byte.class || type == Byte.class) return (byte) lua.checkint();
        if (type == short.class || type == Short.class) return (short) lua.checkint();
        if (type == int.class || type == Integer.class) return lua.checkint();
        if (type == long.class || type == Long.class) return lua.checklong();
        if (type == float.class || type == Float.class) return (float) lua.checkdouble();
        if (type == double.class || type == Double.class) return lua.checkdouble();
        if (type == char.class || type == Character.class) {
            String s = lua.checkjstring();
            if (s.isEmpty()) throw new IllegalArgumentException("empty char");
            return s.charAt(0);
        }
        if (type.isEnum()) return Enum.valueOf((Class<? extends Enum>) type, lua.checkjstring().toUpperCase());

        if (type.isArray() && lua.istable()) {
            int length = tableLength(lua.checktable());
            Class<?> component = type.getComponentType();
            Object array = Array.newInstance(component, length);
            for (int i = 0; i < length; i++) Array.set(array, i, convert(lua.get(i + 1), component));
            return array;
        }

        if (Map.class.isAssignableFrom(type) && lua.istable()) return toMap(lua.checktable());
        if (Collection.class.isAssignableFrom(type) && lua.istable()) return toList(lua.checktable());

        if (host != null && type.isInstance(host)) return host;
        throw new IllegalArgumentException("cannot convert " + lua.typename() + " to " + type.getName());
    }

    private static Object toJavaObject(LuaValue lua) {
        Object host = unwrapHost(lua);
        if (host != null) return host;
        if (lua.isnil()) return null;
        if (lua.isboolean()) return lua.toboolean();
        if (lua.isint()) return lua.toint();
        if (lua.isnumber()) return lua.todouble();
        if (lua.isstring()) return lua.tojstring();
        if (lua.isfunction()) return LuaValueRef.of(lua);
        if (lua.isuserdata()) return lua.touserdata();
        if (lua.istable()) {
            LuaTable table = lua.checktable();
            int length = tableLength(table);
            if (length > 0 && stringKeys(table).isEmpty()) return toList(table);
            return toMap(table);
        }
        return lua.tojstring();
    }

    private static List<Object> toList(LuaTable table) {
        int length = tableLength(table);
        ArrayList<Object> out = new ArrayList<>(length);
        for (int i = 1; i <= length; i++) out.add(toJavaObject(table.get(i)));
        return out;
    }

    private static Map<String, Object> toMap(LuaTable table) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String key : stringKeys(table)) out.put(key, toJavaObject(table.get(key)));
        return out;
    }

    private static List<String> stringKeys(LuaTable table) {
        ArrayList<String> keys = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (key instanceof LuaString) keys.add(key.tojstring());
        }
        return keys;
    }

    private static int tableLength(LuaTable table) {
        int n = 0;
        while (!table.get(n + 1).isnil()) n++;
        return n;
    }
}
