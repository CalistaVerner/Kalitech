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
        super("entity", "Entity", "3.0.0"); // UUID-only, no bridges, no legacy
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

    private static String requireType(String type, String op) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(op + ": type is blank");
        }
        return type.trim();
    }

    @HostAccess.Export
    @Override
    public void destroy(String uuid) {
        final int id = requireEntityId(uuid, "[entity] destroy");
        ecs.destroyEntity(id);
        if (log.isDebugEnabled()) log.debug("[entity] destroyed uuid={}", uuid);
    }

    // -------------------------
    // components (UUID-only)
    // -------------------------

    @HostAccess.Export
    @Override
    public boolean exists(String uuid) {
        final int id = entityIdOrNull(uuid);
        return id != EntityId.NULL && ecs.entities().isAlive(id);
    }

    @HostAccess.Export
    @Override
    public void setComponent(String uuid, String type, Object value) {
        final String t = requireType(type, "[entity] setComponent");
        final int id = requireEntityId(uuid, "[entity] setComponent");
        ecs.components().putByName(id, t, value);
    }

    @HostAccess.Export
    @Override
    public Object getComponent(String uuid, String type) {
        final String t = requireType(type, "[entity] getComponent");
        final int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL) return null;
        return ecs.components().getByName(id, t);
    }

    @HostAccess.Export
    @Override
    public boolean hasComponent(String uuid, String type) {
        final String t = requireType(type, "[entity] hasComponent");
        final int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL) return false;
        return ecs.components().hasByName(id, t);
    }

    // -------------------------
    // internals (NO EXPORT)
    // -------------------------

    @HostAccess.Export
    @Override
    public void removeComponent(String uuid, String type) {
        final String t = requireType(type, "[entity] removeComponent");
        final int id = entityIdOrNull(uuid);
        if (id == EntityId.NULL) return;
        ecs.components().removeByName(id, t);
    }

    private int entityIdOrNull(String uuid) {
        if (uuid == null || uuid.isBlank()) return EntityId.NULL;
        return uuids.entityIdOf(uuid);
    }

    private int requireEntityId(String uuid, String op) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException(op + ": uuid is blank");
        }
        final int id = uuids.entityIdOf(uuid);
        if (id == EntityId.NULL) {
            throw new IllegalArgumentException(op + ": unknown uuid=" + uuid);
        }
        return id;
    }
}
