package org.foxesworld.kalitech.engine.ecs;

import java.util.*;
import java.util.function.BiConsumer;

public final class ComponentStore {

    // typed storage: Class -> Object[] (entityId as index)
    private final Map<Class<?>, Object[]> typed = new IdentityHashMap<>();

    // name-keyed storage (JS-friendly): String -> Object[]
    private final Map<String, Object[]> named = new HashMap<>();

    private int capacity = 0;

    // ---------------- typed API ----------------

    @SuppressWarnings("unchecked")
    public <T> T get(int entity, Class<T> type) {
        Object[] arr = typed.get(type);
        if (arr == null || entity <= 0 || entity >= arr.length) return null;
        return (T) arr[entity];
    }

    public <T> void put(int entity, Class<T> type, T value) {
        if (entity <= 0) throw new IllegalArgumentException("entity must be > 0");
        ensureCapacity(entity);
        Object[] arr = typed.computeIfAbsent(type, k -> new Object[capacity]);
        arr[entity] = value;
    }

    public <T> boolean has(int entity, Class<T> type) {
        Object[] arr = typed.get(type);
        return arr != null && entity > 0 && entity < arr.length && arr[entity] != null;
    }

    public <T> void remove(int entity, Class<T> type) {
        Object[] arr = typed.get(type);
        if (arr == null || entity <= 0 || entity >= arr.length) return;
        arr[entity] = null;
    }

    /** Fast iteration without allocations. */
    @SuppressWarnings("unchecked")
    public <T> void forEach(Class<T> type, BiConsumer<Integer, T> fn) {
        Object[] arr = typed.get(type);
        if (arr == null) return;
        for (int e = 1; e < arr.length; e++) {
            Object v = arr[e];
            if (v != null) fn.accept(e, (T) v);
        }
    }

    /** Compatibility/debug snapshot (avoid per frame). */
    @SuppressWarnings("unchecked")
    public <T> Map<Integer, T> view(Class<T> type) {
        Object[] arr = typed.get(type);
        if (arr == null) return Map.of();
        HashMap<Integer, T> out = new HashMap<>();
        for (int e = 1; e < arr.length; e++) {
            Object v = arr[e];
            if (v != null) out.put(e, (T) v);
        }
        return Collections.unmodifiableMap(out);
    }

    // ---------------- name-keyed API ----------------

    public Object getByName(int entity, String type) {
        Object[] arr = named.get(type);
        if (arr == null || entity <= 0 || entity >= arr.length) return null;
        return arr[entity];
    }

    public void putByName(int entity, String type, Object value) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is blank");
        if (entity <= 0) throw new IllegalArgumentException("entity must be > 0");
        ensureCapacity(entity);
        Object[] arr = named.computeIfAbsent(type, k -> new Object[capacity]);
        arr[entity] = value;
    }

    public boolean hasByName(int entity, String type) {
        Object[] arr = named.get(type);
        return arr != null && entity > 0 && entity < arr.length && arr[entity] != null;
    }

    public void removeByName(int entity, String type) {
        Object[] arr = named.get(type);
        if (arr == null || entity <= 0 || entity >= arr.length) return;
        arr[entity] = null;
    }

    /** Fast iteration for name-keyed components without allocations. */
    public void forEachByName(String type, BiConsumer<Integer, Object> fn) {
        if (type == null || type.isBlank()) return;
        Object[] arr = named.get(type);
        if (arr == null) return;
        for (int e = 1; e < arr.length; e++) {
            Object v = arr[e];
            if (v != null) fn.accept(e, v);
        }
    }

    /** Optional compatibility/debug snapshot (avoid per frame). */
    public Map<Integer, Object> viewByName(String type) {
        if (type == null || type.isBlank()) return Map.of();
        Object[] arr = named.get(type);
        if (arr == null) return Map.of();
        HashMap<Integer, Object> out = new HashMap<>();
        for (int e = 1; e < arr.length; e++) {
            Object v = arr[e];
            if (v != null) out.put(e, v);
        }
        return Collections.unmodifiableMap(out);
    }

    /** Remove ALL components for an entity (critical for destroyEntity). */
    public void removeAll(int entity) {
        if (entity <= 0) return;
        for (Object[] arr : typed.values()) {
            if (entity < arr.length) arr[entity] = null;
        }
        for (Object[] arr : named.values()) {
            if (entity < arr.length) arr[entity] = null;
        }
    }

    /** Full reset for hot-reload rebuilds. */
    public void reset() {
        typed.clear();
        named.clear();
        capacity = 0;
    }

    // ---------------- internals ----------------

    private void ensureCapacity(int entityId) {
        if (entityId < capacity) return;

        int newCap = nextPow2(entityId + 1);
        if (newCap <= capacity) newCap = entityId + 1;

        for (Map.Entry<Class<?>, Object[]> e : typed.entrySet()) {
            e.setValue(Arrays.copyOf(e.getValue(), newCap));
        }
        for (Map.Entry<String, Object[]> e : named.entrySet()) {
            e.setValue(Arrays.copyOf(e.getValue(), newCap));
        }

        capacity = newCap;
    }

    private static int nextPow2(int v) {
        int x = 1;
        while (x < v) x <<= 1;
        return x;
    }
}