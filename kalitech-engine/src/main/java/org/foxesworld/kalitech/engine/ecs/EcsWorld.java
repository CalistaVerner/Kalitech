// FILE: org/foxesworld/kalitech/engine/ecs/EcsWorld.java
package org.foxesworld.kalitech.engine.ecs;

public final class EcsWorld {

    private final EntityManager entities = new EntityManager();
    private final ComponentStore components = new ComponentStore();
    private final EntityUuids uuids = new EntityUuids();

    public EntityManager entities() { return entities; }
    public ComponentStore components() { return components; }

    public EntityUuids uuids() {
        return uuids;
    }

    public int createEntity() {
        int id = entities.create();
        uuids.onCreate(id);
        return id;
    }

    public String createEntityUuid() {
        int id = createEntity();
        return uuids.uuidStringOf(id);
    }

    public void destroyEntity(int id) {
        uuids.onDestroy(id);
        entities.destroy(id);
        components.removeAll(id);
    }

    public void destroyEntityUuid(String uuid) {
        int id = uuids.entityIdOf(uuid);
        if (id == EntityId.NULL) return;
        destroyEntity(id);
    }

    public boolean isAliveUuid(String uuid) {
        int id = uuids.entityIdOf(uuid);
        return id != EntityId.NULL && entities.isAlive(id);
    }

    public int entityIdOfUuid(String uuid) {
        int id = uuids.entityIdOf(uuid);
        return (id == EntityId.NULL) ? 0 : id;
    }

    public void reset() {
        entities.reset();
        components.reset();
        uuids.reset();
    }
}