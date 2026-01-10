package org.foxesworld.kalitech.engine.ecs;

import java.util.Objects;

/**
 * UUID-first ECS facade.
 * <p>
 * Public contract:
 * - Create/destroy/exists by UUID only.
 * - int entityId stays internal (dense index for stores).
 */
public final class EcsWorld {

    private final EntityManager entities = new EntityManager();
    private final ComponentStore components = new ComponentStore();
    private final EntityUuids uuids = new EntityUuids();

    public ComponentStore components() { return components;
    }

    public EntityUuids uuids() {
        return uuids; }

    // ------------------------------------------------------------
    // UUID-only public lifecycle
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // Internal bridge (engine modules): UUID -> entityId
    // ------------------------------------------------------------

    /**
     * INTERNAL: resolve UUID to dense id or throw. Not for scripts.
     */
    public int resolveEntityId(String uuid) {
        return requireEntityId(uuid, "resolveEntityId");
    }

    /**
     * INTERNAL: resolve UUID to dense id or NULL. Not for scripts.
     */
    public int resolveEntityIdOrNull(String uuid) {
        return entityIdOrNull(uuid);
    }

    /**
     * INTERNAL
     */
    public boolean isAliveEntityId(int entityId) {
        return entities.isAlive(entityId);
    }

    public void reset() {
        entities.reset();
        components.reset();
        uuids.reset();
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    private void destroyInternal(int id) {
        uuids.onDestroy(id);
        entities.destroy(id);
        components.removeAll(id);
    }

    private int entityIdOrNull(String uuid) {
        if (uuid == null || uuid.isBlank()) return EntityId.NULL;
        return uuids.entityIdOf(uuid);
    }

    private int requireEntityId(String uuid, String op) {
        Objects.requireNonNull(uuid, op + ": uuid is null");
        if (uuid.isBlank()) throw new IllegalArgumentException(op + ": uuid is blank");

        int id = uuids.entityIdOf(uuid);
        if (id == EntityId.NULL) throw new IllegalArgumentException(op + ": unknown uuid=" + uuid);
        if (!entities.isAlive(id)) throw new IllegalStateException(op + ": entity is not alive uuid=" + uuid);

        return id;
    }
}