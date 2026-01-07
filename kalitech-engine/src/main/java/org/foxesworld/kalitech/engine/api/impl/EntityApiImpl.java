package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;

import java.util.HashMap;
import java.util.Map;

public final class EntityApiImpl extends AbstractApiModule implements EntityApi {

    private EcsWorld ecs;

    public EntityApiImpl() {
        super("entity", "Entity", "1.0.0");
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(String.valueOf(kv[i]), kv[i + 1]);
        return out;
    }

    private ScriptEventBus bus() {
        return engine.getBus();
    }

    private void emit(String topic, Map<String, Object> payload) {
        ScriptEventBus b = bus();
        if (b == null) return;
        try {
            b.emit(topic, payload);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.ecs = ctx.ecs;
    }

    @HostAccess.Export
    @Override
    public int create(String name) {
        return profiled(() -> {
            int id = ecs.createEntity();

            String safeName = (name == null) ? "" : name.trim();
            if (!safeName.isEmpty()) ecs.components().putByName(id, "Name", safeName);

            emit("engine.entity.create", m("entityId", id, "name", safeName));
            return id;
        });
    }

    @HostAccess.Export
    @Override
    public void destroy(int id) {
        profiledVoid(() -> {
            if (id <= 0) return;

            emit("engine.entity.destroy.before", m("entityId", id));

            try {
                engine.__surfaceCleanupOnEntityDestroy(id);
            } catch (Throwable ignored) {
            }

            ecs.destroyEntity(id);

            emit("engine.entity.destroy.after", m("entityId", id));
        });
    }

    @HostAccess.Export
    @Override
    public void setComponent(int id, String type, Object data) {
        profiledVoid(() -> {
            if (id <= 0) return;
            if (type == null || type.isBlank()) return;

            String t = type.trim();
            ecs.components().putByName(id, t, data);

            emit("engine.entity.component.set", m("entityId", id, "type", t, "data", data));
        });
    }

    @HostAccess.Export
    @Override
    public Object getComponent(int id, String type) {
        return profiled(() -> {
            if (id <= 0) return null;
            if (type == null || type.isBlank()) return null;
            return ecs.components().getByName(id, type.trim());
        });
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(int id, String type) {
        return profiled(() -> {
            if (id <= 0) return false;
            if (type == null || type.isBlank()) return false;
            return ecs.components().hasByName(id, type.trim());
        });
    }

    @HostAccess.Export
    @Override
    public void removeComponent(int id, String type) {
        profiledVoid(() -> {
            if (id <= 0) return;
            if (type == null || type.isBlank()) return;

            String t = type.trim();
            ecs.components().removeByName(id, t);

            emit("engine.entity.component.remove", m("entityId", id, "type", t));
        });
    }
}