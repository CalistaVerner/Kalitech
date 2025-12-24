package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;

import java.util.Objects;

public final class EntityApiImpl implements EntityApi {

    private final EngineApiImpl engine;
    private final EcsWorld ecs;

    public EntityApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.ecs = engineApi.getEcs();
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
        // ✅ surface cleanup BEFORE ecs entity is gone
        try {
            engine.__surfaceCleanupOnEntityDestroy(id);
        } catch (Throwable ignored) {}

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