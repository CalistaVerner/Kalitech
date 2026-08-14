package org.foxesworld.kalitech.engine.script.lua;

import java.util.Arrays;
import java.util.List;

/**
 * Mutable zero-based array bridge exposed to Lua as a one-based table.
 */
public interface LuaArray {

    Object get(long index);

    void set(long index, LuaValueRef value);

    long getSize();

    default boolean remove(long index) {
        return false;
    }

    static LuaArray fromArray(Object... values) {
        final Object[] copy = values == null ? new Object[0] : Arrays.copyOf(values, values.length);
        return new LuaArray() {
            @Override
            public Object get(long index) {
                int i = Math.toIntExact(index);
                return i >= 0 && i < copy.length ? copy[i] : null;
            }

            @Override
            public void set(long index, LuaValueRef value) {
                int i = Math.toIntExact(index);
                if (i < 0 || i >= copy.length) throw new IndexOutOfBoundsException(i);
                copy[i] = value == null ? null : value.as(Object.class);
            }

            @Override
            public long getSize() {
                return copy.length;
            }
        };
    }

    static LuaArray fromList(List<?> values) {
        return fromArray(values == null ? new Object[0] : values.toArray());
    }
}
