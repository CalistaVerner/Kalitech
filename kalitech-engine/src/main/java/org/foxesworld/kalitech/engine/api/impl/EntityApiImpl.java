package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.graalvm.polyglot.HostAccess;

import java.util.Objects;

/**
 * Entity API (UUID-only).
 *
 * <p>Script-facing contract:
 * <ul>
 *   <li>Entities are created and destroyed using UUID strings only.</li>
 *   <li>Components are addressed by string type keys.</li>
 *   <li>No dense ids are ever exposed to scripts.</li>
 * </ul>
 */
public final class EntityApiImpl extends AbstractApiModule implements EntityApi {

    private EcsWorld ecs;

    public EntityApiImpl() {
        super("entity", "Entity", "3.0.0");
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.ecs = Objects.requireNonNull(ctx.ecs, "ctx.ecs");
    }

    @Override
    public void detach() {
        this.ecs = null;
        super.detach();
    }

    private static String requireType(String type, String op) {
        if (type == null) throw new IllegalArgumentException(op + ": type is null");
        String t = type.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(op + ": type is blank");
        return t;
    }

    @HostAccess.Export
    @Override
    public String create(String name) {
        String uuid = ecs.createEntity();

        if (log != null && log.isDebugEnabled()) {
            String n = (name == null || name.isBlank()) ? "entity" : name.trim();
            log.debug("[entity] created uuid={} name='{}'", uuid, n);
        }
        return uuid;
    }

    @HostAccess.Export
    @Override
    public boolean exists(String uuid) {
        return ecs.exists(uuid);
    }

    @HostAccess.Export
    @Override
    public void destroy(String uuid) {
        EngineApiImpl e = engine;
        if (e != null) {
            e.__surfaceCleanupOnEntityDestroy(uuid);
        }

        ecs.destroyEntity(uuid);

        if (log != null && log.isDebugEnabled()) {
            log.debug("[entity] destroyed uuid={}", uuid);
        }
    }

    @HostAccess.Export
    @Override
    public void setComponent(String uuid, String type, Object value) {
        ecs.putComponentByName(uuid, requireType(type, "setComponent"), value);
    }

    @HostAccess.Export
    @Override
    public Object getComponent(String uuid, String type) {
        return ecs.getComponentByName(uuid, requireType(type, "getComponent"));
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(String uuid, String type) {
        return ecs.hasComponentByName(uuid, requireType(type, "hasComponent"));
    }

    @HostAccess.Export
    @Override
    public void removeComponent(String uuid, String type) {
        ecs.removeComponentByName(uuid, requireType(type, "removeComponent"));
    }
}