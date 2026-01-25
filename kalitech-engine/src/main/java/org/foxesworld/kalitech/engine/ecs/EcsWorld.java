package org.foxesworld.kalitech.engine.ecs;

import java.util.*;

/**
 * UUID-first ECS facade.
 *
 * <p>Public contract:
 * <ul>
 *   <li>Create/destroy/exists by UUID only</li>
 *   <li>Dense int entityId stays internal</li>
 * </ul>
 */
public final class EcsWorld {

    private final EntityManager entities = new EntityManager();
    private final ComponentStore components = new ComponentStore();
    private final EntityUuids uuids = new EntityUuids();

    public ComponentStore components() {
        return components;
    }

    public EntityUuids uuids() {
        return uuids;
    }

    public void putComponentByName(String uuid, String type, Object value) {
        int id = requireEntityId(uuid, "putComponentByName");
        components.putByName(id, type, value);
    }

    public Object getComponentByName(String uuid, String type) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return null;
        return components.getByName(id, type);
    }

    public boolean hasComponentByName(String uuid, String type) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return false;
        return components.hasByName(id, type);
    }

    public void removeComponentByName(String uuid, String type) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return;
        components.removeByName(id, type);
    }

    public <T> void putComponent(String uuid, Class<T> type, T value) {
        int id = requireEntityId(uuid, "putComponent");
        components.put(id, type, value);
    }

    public <T> T getComponent(String uuid, Class<T> type) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return null;
        return components.get(id, type);
    }

    public <T> void removeComponent(String uuid, Class<T> type) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return;
        components.remove(id, type);
    }

    public String createEntity() {
        int id = entities.create();
        uuids.onCreate(id);

        String uuid = uuids.uuidStringOf(id);
        if (uuid == null || uuid.isEmpty()) {
            throw new IllegalStateException("UUID was not assigned for entityId=" + id);
        }
        return uuid;
    }

    public void destroyEntity(String uuid) {
        int id = requireEntityId(uuid, "destroyEntity");
        destroyInternal(id);
    }

    public boolean exists(String uuid) {
        int id = entityIdOrNull(uuid);
        return id != EntityId.NULL && entities.isAlive(id);
    }

    public int resolveEntityId(String uuid) {
        return requireEntityId(uuid, "resolveEntityId");
    }

    public long resolveEntityHandle(String uuid) {
        int id = requireEntityId(uuid, "resolveEntityHandle");
        int gen = entities.generationOf(id);
        return EntityId.packHandle(id, gen);
    }

    public boolean isAliveHandle(long handle) {
        int id = EntityId.unpackEntityId(handle);
        int gen = EntityId.unpackGeneration(handle);
        return entities.isAlive(id, gen);
    }

    public int resolveEntityIdOrNull(String uuid) {
        return entityIdOrNull(uuid);
    }

    public boolean isAliveEntityId(int entityId) {
        return entities.isAlive(entityId);
    }

    /**
     * Returns an immutable UI snapshot for a UUID or null if not alive.
     *
     * <p>Cost is O(number of registered named component types). Intended for UI/editor.</p>
     */
    public Map<String, Object> snapshotByUuid(String uuid) {
        int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL || !entities.isAlive(id)) return null;

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", uuids.uuidStringOf(id));
        out.put("alive", true);
        out.put("componentTypes", components.namedComponentTypesOf(id));
        out.put("componentsByName", components.namedComponentsOf(id));
        return Collections.unmodifiableMap(out);
    }

    /**
     * Lists alive UUIDs (up to limit). Intended for UI/editor.
     */
    public String[] listUuids(int limit) {
        int lim = Math.max(0, limit);
        if (lim == 0) return new String[0];

        ArrayList<String> out = new ArrayList<>(Math.min(256, lim));
        entities.forEachAlive(id -> {
            if (out.size() >= lim) return;
            String u = uuids.uuidStringOf(id);
            if (u != null && !u.isEmpty()) out.add(u);
        });
        return out.toArray(new String[0]);
    }

    public void reset() {
        entities.reset();
        components.reset();
        uuids.reset();
    }

    private void destroyInternal(int id) {
        components.removeAll(id);
        uuids.onDestroy(id);
        entities.destroy(id);
    }

    private int entityIdOrNull(String uuid) {
        String normalized = normalizeUuid(uuid);
        if (normalized == null || normalized.isBlank()) return EntityId.NULL;
        return uuids.entityIdOf(normalized);
    }

    private int requireEntityId(String uuid, String op) {
        Objects.requireNonNull(uuid, op + ": uuid is null");
        String normalized = normalizeUuid(uuid);
        if (normalized == null || normalized.isBlank()) throw new IllegalArgumentException(op + ": uuid is blank");

        int id = uuids.entityIdOf(normalized);
        if (id == EntityId.NULL) throw new IllegalArgumentException(op + ": unknown uuid=" + uuid);
        if (!entities.isAlive(id)) throw new IllegalStateException(op + ": entity is not alive uuid=" + uuid);

        return id;
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        return uuid.trim();
    }
}
