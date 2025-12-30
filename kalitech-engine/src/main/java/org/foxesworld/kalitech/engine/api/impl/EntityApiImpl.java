// FILE: EntityApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Entity API + engine events.
 *
 * Events emitted:
 *  - engine.entity.create
 *  - engine.entity.destroy.before
 *  - engine.entity.destroy.after
 *  - engine.entity.component.set
 *  - engine.entity.component.remove
 */
public final class EntityApiImpl implements EntityApi {

    private final EngineApiImpl engine;
    private final EcsWorld ecs;
    private final ScriptEventBus bus;

    public EntityApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.ecs = Objects.requireNonNull(engineApi.getEcs(), "engineApi.getEcs()");
        this.bus = Objects.requireNonNull(engineApi.getBus(), "engineApi.getBus()");
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return out;
    }

    @HostAccess.Export
    @Override
    public int create(String name) {
        int id = ecs.createEntity();

        String safeName = (name == null) ? "" : name.trim();
        if (!safeName.isEmpty()) {
            ecs.components().putByName(id, "Name", safeName);
        }

        bus.emit("engine.entity.create", m(
                "entityId", id,
                "name", safeName
        ));
        return id;
    }

    @HostAccess.Export
    @Override
    public void destroy(int id) {
        if (id <= 0) return;

        bus.emit("engine.entity.destroy.before", m("entityId", id));

        // ✅ surface cleanup BEFORE ecs entity is gone
        try {
            engine.__surfaceCleanupOnEntityDestroy(id);
        } catch (Throwable ignored) {}

        ecs.destroyEntity(id);

        bus.emit("engine.entity.destroy.after", m("entityId", id));
    }

    @HostAccess.Export
    @Override
    public void setComponent(int id, String type, Object data) {
        if (id <= 0) return;
        if (type == null || type.isBlank()) return;

        String t = type.trim();
        ecs.components().putByName(id, t, data);

        bus.emit("engine.entity.component.set", m(
                "entityId", id,
                "type", t,
                "data", data
        ));
    }

    @HostAccess.Export
    @Override
    public Object getComponent(int id, String type) {
        if (id <= 0) return null;
        if (type == null || type.isBlank()) return null;
        return ecs.components().getByName(id, type.trim());
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(int id, String type) {
        if (id <= 0) return false;
        if (type == null || type.isBlank()) return false;
        return ecs.components().hasByName(id, type.trim());
    }

    @HostAccess.Export
    @Override
    public void removeComponent(int id, String type) {
        if (id <= 0) return;
        if (type == null || type.isBlank()) return;

        String t = type.trim();
        ecs.components().removeByName(id, t);

        bus.emit("engine.entity.component.remove", m(
                "entityId", id,
                "type", t
        ));
    }
}