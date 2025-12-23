package org.foxesworld.kalitech.engine.api.impl;

import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.EntityApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;

import java.util.Objects;

public final class EntityApiImpl implements EntityApi {

    private final EcsWorld ecs;

    public EntityApiImpl(EcsWorld ecs) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
    }

    @HostAccess.Export
    @Override
    public int create(String name) {
        int id = ecs.createEntity();
        if (name != null && !name.isBlank()) {
            ecs.components().putByName(id, "Name", name);
        }
        return id;
    }

    @HostAccess.Export
    @Override
    public void destroy(int id) {
        ecs.destroyEntity(id);
    }

    @HostAccess.Export
    @Override
    public void setComponent(int id, String type, Object data) {
        ecs.components().putByName(id, type, data);
    }

    @HostAccess.Export
    @Override
    public Object getComponent(int id, String type) {
        return ecs.components().getByName(id, type);
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(int id, String type) {
        return ecs.components().hasByName(id, type);
    }

    @HostAccess.Export
    @Override
    public void removeComponent(int id, String type) {
        ecs.components().removeByName(id, type);
    }
}