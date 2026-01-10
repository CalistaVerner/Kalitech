// FILE: org/foxesworld/kalitech/engine/api/impl/EntityApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.EntityId;
import org.foxesworld.kalitech.engine.ecs.EntityUuids;
import org.graalvm.polyglot.HostAccess;

import java.util.Objects;

public final class EntityApiImpl extends AbstractApiModule implements EntityApi {

    private static final Logger log = LogManager.getLogger(EntityApiImpl.class);

    private EcsWorld ecs;
    private EntityUuids uuids;

    public EntityApiImpl() {
        super("entity", "Entity", "2.1.0"); // UUID-only + components
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.ecs = Objects.requireNonNull(ctx.ecs, "ctx.ecs");
        this.uuids = Objects.requireNonNull(ctx.ecs.uuids(), "ecs.uuids()");
    }

    @Override
    public void detach() {
        this.uuids = null;
        this.ecs = null;
        super.detach();
    }

    // -------------------------
    // lifecycle
    // -------------------------

    @HostAccess.Export
    @Override
    public String create(String name) {
        final String n = (name == null || name.isBlank()) ? "entity" : name.trim();

        final int id = ecs.createEntity();
        final String uuid = uuids.uuidStringOf(id);

        if (uuid == null || uuid.isEmpty()) {
            throw new IllegalStateException("[entity] create: UUID was not assigned for entityId=" + id);
        }

        if (log.isDebugEnabled()) log.debug("[entity] created uuid={} name='{}'", uuid, n);
        return uuid;
    }

    @HostAccess.Export
    @Override
    public void destroy(String uuid) {
        final int id = entityIdOf(uuid);
        if (id == EntityId.NULL) return;
        ecs.destroyEntity(id);
        if (log.isDebugEnabled()) log.debug("[entity] destroyed uuid={}", uuid);
    }

    @HostAccess.Export
    @Override
    public boolean exists(String uuid) {
        final int id = entityIdOf(uuid);
        return id != EntityId.NULL && ecs.entities().isAlive(id);
    }

    // -------------------------
    // bridge
    // -------------------------

    @HostAccess.Export
    @Override
    public int entityIdOf(String uuid) {
        if (uuid == null || uuid.isBlank()) return EntityId.NULL;
        return uuids.entityIdOf(uuid);
    }

    @HostAccess.Export
    @Override
    public String uuidOf(int entityId) {
        if (entityId <= 0) return "";
        final String u = uuids.uuidStringOf(entityId);
        return (u == null) ? "" : u;
    }

    // -------------------------
    // components (UUID-only)
    // -------------------------

    @HostAccess.Export
    @Override
    public void setComponent(String uuid, String type, Object value) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("[entity] setComponent: type is blank");
        final int id = entityIdOf(uuid);
        if (id == EntityId.NULL) throw new IllegalArgumentException("[entity] setComponent: unknown uuid=" + uuid);
        ecs.components().putByName(id, type, value);
    }

    @HostAccess.Export
    @Override
    public Object getComponent(String uuid, String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("[entity] getComponent: type is blank");
        final int id = entityIdOf(uuid);
        if (id == EntityId.NULL) return null;
        return ecs.components().getByName(id, type);
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(String uuid, String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("[entity] hasComponent: type is blank");
        final int id = entityIdOf(uuid);
        if (id == EntityId.NULL) return false;
        return ecs.components().hasByName(id, type);
    }

    @HostAccess.Export
    @Override
    public void removeComponent(String uuid, String type) {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("[entity] removeComponent: type is blank");
        final int id = entityIdOf(uuid);
        if (id == EntityId.NULL) return;
        ecs.components().removeByName(id, type);
    }
}