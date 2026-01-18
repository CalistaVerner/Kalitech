package org.foxesworld.kalitech.engine.ecs;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Component storage optimized for hot iteration.
 *
 * <p>Design goals:
 * <ul>
 *   <li>O(1) add/remove/get/has</li>
 *   <li>Dense iteration (no full-array scans)</li>
 *   <li>No boxing for entity ids</li>
 * </ul>
 *
 * <p>Implementation is a classic sparse-set:
 * <ul>
 *   <li><b>sparse</b>: entityId -&gt; denseIndex+1</li>
 *   <li><b>denseEntities</b>: denseIndex -&gt; entityId</li>
 *   <li><b>denseValues</b>: denseIndex -&gt; component</li>
 * </ul>
 */
public final class ComponentStore {

    private final Map<Class<?>, Pool> typed = new IdentityHashMap<>();
    private final Map<String, Pool> named = new HashMap<>();

    /**
     * Current maximum entity capacity for sparse arrays (exclusive).
     * Entity ids are expected to start at 1.
     */
    private int entityCapacity;

    public ComponentStore() {
        this.entityCapacity = 0;
    }

    private static void requireEntity(int entity) {
        if (entity <= 0) throw new IllegalArgumentException("entity must be > 0");
    }

    private static String requireTypeName(String type) {
        if (type == null) throw new NullPointerException("type");
        String t = type.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("type is blank");
        return t;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(int entity, Class<T> type) {
        Pool p = typed.get(type);
        if (p == null) return null;
        return (T) p.get(entity);
    }

    public <T> void put(int entity, Class<T> type, T value) {
        requireEntity(entity);
        ensureEntityCapacity(entity);
        Pool p = typed.computeIfAbsent(type, k -> new Pool(entityCapacity));
        p.ensureSparse(entityCapacity);
        p.put(entity, value);
    }

    public <T> boolean has(int entity, Class<T> type) {
        Pool p = typed.get(type);
        return p != null && p.has(entity);
    }

    public <T> void remove(int entity, Class<T> type) {
        Pool p = typed.get(type);
        if (p == null) return;
        p.remove(entity);
    }

    @SuppressWarnings("unchecked")
    public <T> void forEach(Class<T> type, BiConsumer<Integer, T> fn) {
        Objects.requireNonNull(fn, "fn");
        Pool p = typed.get(type);
        if (p == null) return;
        p.forEach((e, v) -> fn.accept(e, (T) v));
    }

    @SuppressWarnings("unchecked")
    public <T> Map<Integer, T> view(Class<T> type) {
        Pool p = typed.get(type);
        if (p == null) return Map.of();
        HashMap<Integer, T> out = new HashMap<>(Math.max(16, p.size()));
        p.forEach((e, v) -> out.put(e, (T) v));
        return Collections.unmodifiableMap(out);
    }

    public Object getByName(int entity, String type) {
        String key = requireTypeName(type);
        Pool p = named.get(key);
        if (p == null) return null;
        return p.get(entity);
    }

    public void putByName(int entity, String type, Object value) {
        requireEntity(entity);
        String key = requireTypeName(type);
        ensureEntityCapacity(entity);
        Pool p = named.computeIfAbsent(key, k -> new Pool(entityCapacity));
        p.ensureSparse(entityCapacity);
        p.put(entity, value);
    }

    public boolean hasByName(int entity, String type) {
        String key = requireTypeName(type);
        Pool p = named.get(key);
        return p != null && p.has(entity);
    }

    public void removeByName(int entity, String type) {
        String key = requireTypeName(type);
        Pool p = named.get(key);
        if (p == null) return;
        p.remove(entity);
    }

    public void forEachByName(String type, BiConsumer<Integer, Object> fn) {
        Objects.requireNonNull(fn, "fn");
        String key = requireTypeName(type);
        Pool p = named.get(key);
        if (p == null) return;
        p.forEach(fn);
    }

    public Map<Integer, Object> viewByName(String type) {
        String key = requireTypeName(type);
        Pool p = named.get(key);
        if (p == null) return Map.of();
        HashMap<Integer, Object> out = new HashMap<>(Math.max(16, p.size()));
        p.forEach(out::put);
        return Collections.unmodifiableMap(out);
    }

    /**
     * Removes all components of an entity from all pools.
     *
     * <p>This operation is O(number of registered component types). In exchange,
     * per-component iteration is hot and dense.
     */
    public void removeAll(int entity) {
        requireEntity(entity);
        for (Pool p : typed.values()) {
            p.remove(entity);
        }
        for (Pool p : named.values()) {
            p.remove(entity);
        }
    }

    private static int nextPow2(int v) {
        int x = 1;
        while (x < v) x <<= 1;
        return x;
    }

    public void reset() {
        typed.clear();
        named.clear();
        entityCapacity = 0;
    }

    private void ensureEntityCapacity(int entityId) {
        if (entityId < entityCapacity) return;
        int newCap = nextPow2(entityId + 1);
        if (newCap <= entityCapacity) newCap = entityId + 1;

        entityCapacity = newCap;
        for (Pool p : typed.values()) {
            p.ensureSparse(entityCapacity);
        }
        for (Pool p : named.values()) {
            p.ensureSparse(entityCapacity);
        }
    }

    /**
     * Sparse-set pool for a single component type.
     */
    static final class Pool {
        private int[] sparse;              // entityId -> denseIndex+1
        private int[] denseEntities;       // denseIndex -> entityId
        private Object[] denseValues;      // denseIndex -> value
        private int size;

        Pool(int entityCapacity) {
            this.sparse = new int[Math.max(16, entityCapacity)];
            this.denseEntities = new int[16];
            this.denseValues = new Object[16];
            this.size = 0;
        }

        int size() {
            return size;
        }

        void ensureSparse(int entityCapacity) {
            if (entityCapacity <= sparse.length) return;
            int[] n = new int[entityCapacity];
            System.arraycopy(sparse, 0, n, 0, sparse.length);
            sparse = n;
        }

        boolean has(int entity) {
            if (entity <= 0 || entity >= sparse.length) return false;
            return sparse[entity] != 0;
        }

        Object get(int entity) {
            if (entity <= 0 || entity >= sparse.length) return null;
            int di1 = sparse[entity];
            if (di1 == 0) return null;
            return denseValues[di1 - 1];
        }

        void put(int entity, Object value) {
            if (entity <= 0) throw new IllegalArgumentException("entity must be > 0");
            if (entity >= sparse.length) {
                throw new IllegalStateException("sparse capacity is not enough: entity=" + entity);
            }

            int di1 = sparse[entity];
            if (di1 != 0) {
                denseValues[di1 - 1] = value;
                return;
            }

            ensureDense(size + 1);
            int di = size++;
            denseEntities[di] = entity;
            denseValues[di] = value;
            sparse[entity] = di + 1;
        }

        void remove(int entity) {
            if (entity <= 0 || entity >= sparse.length) return;
            int di1 = sparse[entity];
            if (di1 == 0) return;

            int di = di1 - 1;
            int last = size - 1;
            sparse[entity] = 0;

            if (di != last) {
                int movedEntity = denseEntities[last];
                Object movedValue = denseValues[last];
                denseEntities[di] = movedEntity;
                denseValues[di] = movedValue;
                sparse[movedEntity] = di + 1;
            }

            denseEntities[last] = 0;
            denseValues[last] = null;
            size = last;
        }

        void forEach(BiConsumer<Integer, Object> fn) {
            for (int i = 0; i < size; i++) {
                fn.accept(denseEntities[i], denseValues[i]);
            }
        }

        private void ensureDense(int min) {
            if (min <= denseEntities.length) return;
            int newCap = denseEntities.length;
            while (newCap < min) newCap <<= 1;

            int[] ne = new int[newCap];
            Object[] nv = new Object[newCap];
            System.arraycopy(denseEntities, 0, ne, 0, denseEntities.length);
            System.arraycopy(denseValues, 0, nv, 0, denseValues.length);
            denseEntities = ne;
            denseValues = nv;
        }
    }
}