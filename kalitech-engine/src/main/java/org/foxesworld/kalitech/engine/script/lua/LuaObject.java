package org.foxesworld.kalitech.engine.script.lua;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable named-member bridge exposed to Lua as a table.
 */
public interface LuaObject {

    Object getMember(String key);

    Object getMemberKeys();

    boolean hasMember(String key);

    void putMember(String key, LuaValueRef value);

    default boolean removeMember(String key) {
        return false;
    }

    static LuaObject fromMap(Map<String, ?> values) {
        final Map<String, Object> map = values == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(values);
        return new LuaObject() {
            @Override
            public Object getMember(String key) {
                return map.get(key);
            }

            @Override
            public Object getMemberKeys() {
                return LuaArray.fromArray(map.keySet().toArray());
            }

            @Override
            public boolean hasMember(String key) {
                return map.containsKey(key);
            }

            @Override
            public void putMember(String key, LuaValueRef value) {
                map.put(key, value == null ? null : value.as(Object.class));
            }

            @Override
            public boolean removeMember(String key) {
                return map.remove(key) != null;
            }
        };
    }
}
