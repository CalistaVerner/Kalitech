package org.foxesworld.kalitech.engine.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.TransformComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;

public final class EntityScriptAPI {

    private static final Logger log = LogManager.getLogger(EntityScriptAPI.class);

    private final String uuid;
    private final EcsWorld ecs;
    private final ScriptEventBus events;

    public EntityScriptAPI(String uuid, EcsWorld ecs, ScriptEventBus events) {
        this.uuid = uuid;
        this.ecs = ecs;
        this.events = events;
    }

    @HostAccess.Export
    public String uuid() {
        return uuid;
    }

    @HostAccess.Export
    public void info(String msg) {
        log.info("[JS:{}] {}", uuid, msg);
    }

    // ---------- Events ----------
    @HostAccess.Export
    public void emit(String eventName, Object payload) {
        events.emit(eventName, payload);
    }

    // ---------- Transform ----------
    @HostAccess.Export
    public void setPos(float x, float y, float z) {
        int entityId = ecs.resolveEntityId(uuid);
        TransformComponent t = ecs.components().get(entityId, TransformComponent.class);
        if (t == null) {
            t = new TransformComponent();
            ecs.components().put(entityId, TransformComponent.class, t);
        }
        t.x = x;
        t.y = y;
        t.z = z;
    }

    @HostAccess.Export
    public float getX() {
        int entityId = ecs.resolveEntityId(uuid);
        TransformComponent t = ecs.components().get(entityId, TransformComponent.class);
        return t != null ? t.x : 0f;
    }

    @HostAccess.Export
    public void rotateY(float radians) {
        int entityId = ecs.resolveEntityId(uuid);
        TransformComponent t = ecs.components().get(entityId, TransformComponent.class);
        if (t == null) {
            t = new TransformComponent();
            ecs.components().put(entityId, TransformComponent.class, t);
        }
        t.rotY += radians;
    }

    // Потом расширим:
    // - spawn/attach renderable
    // - input, timers, etc.
}
