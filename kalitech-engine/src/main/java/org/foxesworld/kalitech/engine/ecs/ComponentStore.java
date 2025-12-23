package org.foxesworld.kalitech.engine.ecs;

import java.util.HashMap;
import java.util.Map;

public final class ComponentStore {

    // typed storage
    private final Map<Class<?>, Map<Integer, Object>> data = new HashMap<>();

    // name-keyed storage (JS-friendly)
    private final Map<String, Map<Integer, Object>> byName = new HashMap<>();

    // ---------------- typed API (compatible with your current code) ----------------

    @SuppressWarnings("unchecked")
    public <T> T get(int entity, Class<T> type) {
        Map<Integer, Object> map = data.get(type);
        if (map == null) return null;
        return (T) map.get(entity);
    }

    public <T> void put(int entity, Class<T> type, T value) {
        data.computeIfAbsent(type, k -> new HashMap<>()).put(entity, value);
    }

    public <T> boolean has(int entity, Class<T> type) {
        Map<Integer, Object> map = data.get(type);
        return map != null && map.containsKey(entity);
    }

    public <T> void remove(int entity, Class<T> type) {
        Map<Integer, Object> map = data.get(type);
        if (map != null) map.remove(entity);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<Integer, T> view(Class<T> type) {
        Map<Integer, Object> map = data.get(type);
        return map != null ? (Map<Integer, T>) map : Map.of();
    }

    // ---------------- name-keyed API (NEW) ----------------

    public Object getByName(int entity, String type) {
        Map<Integer, Object> map = byName.get(type);
        return map != null ? map.get(entity) : null;
    }

    public void putByName(int entity, String type, Object value) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is blank");
        byName.computeIfAbsent(type, k -> new HashMap<>()).put(entity, value);
    }

    public boolean hasByName(int entity, String type) {
        Map<Integer, Object> map = byName.get(type);
        return map != null && map.containsKey(entity);
    }

    public void removeByName(int entity, String type) {
        Map<Integer, Object> map = byName.get(type);
        if (map != null) map.remove(entity);
    }

    public Map<Integer, Object> viewByName(String type) {
        Map<Integer, Object> map = byName.get(type);
        return map != null ? map : Map.of();
    }
}