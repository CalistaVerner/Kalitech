package org.foxesworld.kalitech.engine.ecs;

public final class EcsWorld {

    private final EntityManager entities = new EntityManager();
    private final ComponentStore components = new ComponentStore();

    public EntityManager entities() { return entities; }
    public ComponentStore components() { return components; }

    public int createEntity() { return entities.create(); }
    public void destroyEntity(int id) { entities.destroy(id); }
}