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

    public void destroyEntity(int id) {
        uuids.onDestroy(id);
        entities.destroy(id);
        components.removeAll(id); // IMPORTANT: prevent leaks & stale data
    }

    /** Full reset for hot-reload rebuilds. */
    public void reset() {
        entities.reset();
        components.reset();
        uuids.reset();
    }
}