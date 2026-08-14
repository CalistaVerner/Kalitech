package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.*;
import org.foxesworld.kalitech.engine.api.interfaces.EntityApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
public final class EntityApiImpl extends AbstractApiModule implements EntityApi {

    private static final Logger log = LogManager.getLogger(EntityApiImpl.class);

    private static final Method M_CREATE =
            method(EntityApiImpl.class, "create", String.class);

    private static final Method M_DESTROY =
            method(EntityApiImpl.class, "destroy", String.class);

    private static final Method M_EXISTS =
            method(EntityApiImpl.class, "exists", String.class);

    private static final Method M_SET_COMPONENT =
            method(EntityApiImpl.class, "setComponent", String.class, String.class, Object.class);

    private static final Method M_GET_COMPONENT =
            method(EntityApiImpl.class, "getComponent", String.class, String.class);

    private static final Method M_HAS_COMPONENT =
            method(EntityApiImpl.class, "hasComponent", String.class, String.class);

    private static final Method M_REMOVE_COMPONENT =
            method(EntityApiImpl.class, "removeComponent", String.class, String.class);

    private static final Method M_SNAPSHOT =
            method(EntityApiImpl.class, "snapshot", String.class);

    private static final Method M_LIST =
            method(EntityApiImpl.class, "list", int.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    public EntityApiImpl() {
        super("entity", "Entity", "3.1.0");
    }

    private static String requireType(String type, String op) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException(op + ": type is blank");
        return type.trim();
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

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public String create(String name) {
        return profiled(() ->
                apiCall(M_CREATE, new Object[]{name}, () -> {
                    String uuid = ecs.createEntity();

                    if (log.isDebugEnabled()) {
                        String n = (name == null || name.isBlank()) ? "entity" : name.trim();
                        log.debug("[entity] created uuid={} name='{}'", uuid, n);
                    }
                    return uuid;
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public void destroy(@NotNull String uuid) {
        profiledVoid(() ->
                apiVoid(M_DESTROY, new Object[]{uuid}, () -> {
                    EngineApiImpl e = this.engine;
                    if (e != null) {
                        try {
                            e.__surfaceCleanupOnEntityDestroy(uuid);
                        } catch (RuntimeException ex) {
                            log.warn("[entity] surface cleanup failed for uuid={} (continuing destroy)", uuid, ex);
                        }
                    }

                    ecs.destroyEntity(uuid);
                    if (log.isDebugEnabled()) log.debug("[entity] destroyed uuid={}", uuid);
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public boolean exists(@NotNull String uuid) {
        return profiled(() ->
                apiCall(M_EXISTS, new Object[]{uuid}, () -> ecs.exists(uuid))
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setComponent(@NotNull String uuid, @NotNull String type, Object value) {
        profiledVoid(() ->
                apiVoid(M_SET_COMPONENT, new Object[]{uuid, type, value}, () -> {
                    String t = requireType(type, "[entity] setComponent");
                    ecs.putComponentByName(uuid, t, value);
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object getComponent(@NotNull String uuid, @NotNull String type) {
        return profiled(() ->
                apiCall(M_GET_COMPONENT, new Object[]{uuid, type}, () -> {
                    String t = requireType(type, "[entity] getComponent");
                    return ecs.getComponentByName(uuid, t);
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public boolean hasComponent(@NotNull String uuid, @NotNull String type) {
        return profiled(() ->
                apiCall(M_HAS_COMPONENT, new Object[]{uuid, type}, () -> {
                    String t = requireType(type, "[entity] hasComponent");
                    return ecs.hasComponentByName(uuid, t);
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void removeComponent(@NotNull String uuid, @NotNull String type) {
        profiledVoid(() ->
                apiVoid(M_REMOVE_COMPONENT, new Object[]{uuid, type}, () -> {
                    String t = requireType(type, "[entity] removeComponent");
                    ecs.removeComponentByName(uuid, t);
                })
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.NORMAL
    )
    public Map<String, Object> snapshot(@NotNull String uuid) {
        return profiled(() ->
                apiCall(M_SNAPSHOT, new Object[]{uuid}, () -> ecs.snapshotByUuid(uuid))
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.NORMAL
    )
    public String[] list(int limit) {
        return profiled(() ->
                apiCall(M_LIST, new Object[]{limit}, () -> ecs.listUuids(limit))
        );
    }
}
