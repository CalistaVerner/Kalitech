package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.graalvm.polyglot.HostAccess;

import java.util.Objects;

public final class EntityApiImpl extends AbstractApiModule implements EntityApi {

    private static final Logger log = LogManager.getLogger(EntityApiImpl.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    public EntityApiImpl() {
        super("entity", "Entity", "3.0.0"); // UUID-only
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.engine = Objects.requireNonNull(ctx.engine, "ctx.engine");
        this.ecs = Objects.requireNonNull(ctx.ecs, "ctx.ecs");
    }

    @Override
    public void detach() {
        this.ecs = null;
        this.engine = null;
        super.detach();
    }

    // -------------------------
    // lifecycle (UUID-only)
    // -------------------------

    private static String requireType(String type, String op) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException(op + ": type is blank");
        return type.trim();
    }

    @HostAccess.Export
    @Override
    public String create(String name) {
        String uuid = ecs.createEntity();

        if (log.isDebugEnabled()) {
            String n = (name == null || name.isBlank()) ? "entity" : name.trim();
            log.debug("[entity] created uuid={} name='{}'", uuid, n);
        }
        return uuid;
    }

    @HostAccess.Export
    @Override
    public void destroy(String uuid) {
        EngineApiImpl e = this.engine;
        if (e != null) {
            e.__surfaceCleanupOnEntityDestroy(uuid);
        }
        ecs.destroyEntity(uuid);
        if (log.isDebugEnabled()) log.debug("[entity] destroyed uuid={}", uuid);
    }

    // -------------------------
    // components (UUID-only)
    // -------------------------

    @HostAccess.Export
    @Override
    public boolean exists(String uuid) {
        return ecs.exists(uuid);
    }

    @HostAccess.Export
    @Override
    public void setComponent(String uuid, String type, Object value) {
        String t = requireType(type, "[entity] setComponent");
        ecs.putComponentByName(uuid, t, value);
    }

    @HostAccess.Export
    @Override
    public Object getComponent(String uuid, String type) {
        String t = requireType(type, "[entity] getComponent");
        return ecs.getComponentByName(uuid, t);
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(String uuid, String type) {
        String t = requireType(type, "[entity] hasComponent");
        return ecs.hasComponentByName(uuid, t);
    }

    @HostAccess.Export
    @Override
    public void removeComponent(String uuid, String type) {
        String t = requireType(type, "[entity] removeComponent");
        ecs.removeComponentByName(uuid, t);
    }
}