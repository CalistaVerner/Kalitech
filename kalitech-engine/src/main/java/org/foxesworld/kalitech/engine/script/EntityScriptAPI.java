package org.foxesworld.kalitech.engine.script;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.TransformComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

public final class EntityScriptAPI {

    private static final Logger log = LogManager.getLogger(EntityScriptAPI.class);

    private final int entityId;
    private final EcsWorld ecs;
    private final SimpleApplication app;
    private final ScriptEventBus events;

    public EntityScriptAPI(int entityId, EcsWorld ecs, SimpleApplication app, ScriptEventBus events) {
        this.entityId = entityId;
        this.ecs = ecs;
        this.app = app;
        this.events = events;
    }

    @HostAccess.Export
    public int id() {
        return entityId;
    }

    @HostAccess.Export
    public void info(String msg) {
        log.info("[JS:e{}] {}", entityId, msg);
    }

    // ---------- Events ----------
    @HostAccess.Export
    public void emit(String eventName, Object payload) {
        events.emit(eventName, payload);
    }

    // ---------- Transform ----------
    @HostAccess.Export
    public void setPos(float x, float y, float z) {
        TransformComponent t = ecs.components().get(entityId, TransformComponent.class);
        if (t == null) {
            t = new TransformComponent();
            ecs.components().put(entityId, TransformComponent.class, t);
        }
        t.x = x; t.y = y; t.z = z;
    }

    @HostAccess.Export
    public float getX() {
        TransformComponent t = ecs.components().get(entityId, TransformComponent.class);
        return t != null ? t.x : 0f;
    }

    @HostAccess.Export
    public void rotateY(float radians) {
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