// FILE: org/foxesworld/kalitech/engine/api/services/SurfaceRegistry.java
package org.foxesworld.kalitech.engine.api.services;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.EngineService;
import org.foxesworld.kalitech.engine.ecs.EntityId;
import org.foxesworld.kalitech.engine.ecs.EntityUuids;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class SurfaceRegistry implements EngineService {

    private static final Logger log = LogManager.getLogger(SurfaceRegistry.class);

    private SimpleApplication app;
    private Supplier<ScriptEventBus> busSupplier;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();

    // surfaceId -> entityId (internal)
    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();
    // entityId -> surfaceId
    private final ConcurrentHashMap<Integer, Integer> entityToSurface = new ConcurrentHashMap<>();

    private EntityUuids uuids;

    private final ConcurrentLinkedQueue<Integer> pendingAttach = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean attachFlushScheduled = new AtomicBoolean(false);

    @Override
    public String id() {
        return "surfaceRegistry";
    }

    public SurfaceRegistry(SimpleApplication app) {
        this(app, () -> null);
    }

    public SurfaceRegistry(SimpleApplication app, Supplier<ScriptEventBus> busSupplier) {
        this.app = Objects.requireNonNull(app, "app");
        this.busSupplier = (busSupplier != null) ? busSupplier : () -> null;
    }

    @Override
    public void attach(ApiContext ctx) {
        if (ctx == null) throw new NullPointerException("ctx");
        this.app = Objects.requireNonNull(ctx.app, "ctx.app");
        this.busSupplier = ctx.engine::getBus;

        if (ctx.ecs == null) throw new IllegalStateException("SurfaceRegistry: ctx.ecs is null");
        this.uuids = Objects.requireNonNull(ctx.ecs.uuids(), "ctx.ecs.uuids()");
        if (log.isDebugEnabled()) log.debug("[service] attached id='{}' uuidsBound=true", id());
    }

    @Override
    public void detach() {
        pendingAttach.clear();
        byId.clear();
        kindById.clear();
        surfaceToEntity.clear();
        entityToSurface.clear();
        attachFlushScheduled.set(false);
        uuids = null;
        busSupplier = () -> null;
        if (log.isDebugEnabled()) log.debug("[service] detached id='{}'", id());
    }

    // ------------------------------------------------------------
    // Registry ops
    // ------------------------------------------------------------

    public SurfaceApi.SurfaceHandle register(Spatial spatial, String kind, SurfaceApi api) {
        Objects.requireNonNull(spatial, "spatial");
        Objects.requireNonNull(api, "api");

        String k = (kind == null || kind.isBlank()) ? "surface" : kind.trim();

        int id = ids.getAndIncrement();
        byId.put(id, spatial);
        kindById.put(id, k);

        if (log.isDebugEnabled()) {
            try {
                log.debug("[surface] registered surfaceId={} kind={} name={} worldPos={}",
                        id, k, spatial.getName(), spatial.getWorldTranslation());
            } catch (Throwable ignored) {
                log.debug("[surface] registered surfaceId={} kind={} name={}", id, k, spatial.getName());
            }
        }

        emit("engine.surface.registered", "surfaceId", id, "kind", k, "name", spatial.getName());
        return new SurfaceApi.SurfaceHandle(id, k, api);
    }

    public Spatial get(int id) { return byId.get(id); }
    public String kind(int id) { return kindById.get(id); }
    public boolean exists(int id) { return byId.containsKey(id); }

    // ------------------------------------------------------------
    // UUID-only attachment API
    // ------------------------------------------------------------

    public void attach(int surfaceId, String entityUuid) {
        if (!exists(surfaceId)) throw new IllegalStateException("attach: unknown surfaceId=" + surfaceId);
        int entityId = requireEntityIdFromUuid(entityUuid);

        Integer oldSurface = entityToSurface.put(entityId, surfaceId);
        if (oldSurface != null && oldSurface != surfaceId) surfaceToEntity.remove(oldSurface);

        surfaceToEntity.put(surfaceId, entityId);

        if (log.isDebugEnabled()) log.debug("[surface] attached surfaceId={} entityUuid={}", surfaceId, entityUuid);
        emit("engine.surface.attached", "surfaceId", surfaceId, "entityUuid", entityUuid);
    }

    /**
     * Detach binding by surfaceId. Returns detached uuid or "" if none.
     */
    public String detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);

        if (ent != null) {
            String uuid = requireUuidOfEntityId(ent);
            if (log.isDebugEnabled()) log.debug("[surface] detached surfaceId={} entityUuid={}", surfaceId, uuid);
            emit("engine.surface.detached", "surfaceId", surfaceId, "entityUuid", uuid);
            return uuid;
        }
        return "";
    }

    /**
     * Detach binding by entityUuid. Returns detached surfaceId or null.
     */
    public Integer detachEntity(String entityUuid) {
        int entityId = requireEntityIdFromUuid(entityUuid);

        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);

        if (surf != null) {
            if (log.isDebugEnabled()) log.debug("[surface] detached surfaceId={} entityUuid={}", surf, entityUuid);
            emit("engine.surface.detached", "surfaceId", surf, "entityUuid", entityUuid);
        }
        return surf;
    }

    public String attachedEntityUuid(int surfaceId) {
        Integer ent = surfaceToEntity.get(surfaceId);
        if (ent == null || ent <= 0) return "";
        return requireUuidOfEntityId(ent);
    }

    // ------------------------------------------------------------
    // Scene graph ops
    // ------------------------------------------------------------

    public void attachToRoot(int id) {
        if (!exists(id)) throw new IllegalArgumentException("attachToRoot: unknown surface id=" + id);
        pendingAttach.add(id);
        scheduleAttachFlush();
        emit("engine.surface.attachToRoot", "surfaceId", id);
    }

    /**
     * Detach Spatial from its parent Node (if any).
     */
    public void detachFromParent(int id) {
        if (!exists(id)) throw new IllegalArgumentException("detachFromParent: unknown surface id=" + id);

        Spatial s = byId.get(id);
        if (s == null) return;

        Node parent = s.getParent();
        if (parent != null) {
            parent.detachChild(s);
        }

        emit("engine.surface.detachedFromParent", "surfaceId", id);
    }

    /**
     * Destroy surface:
     * - detach entity binding (UUID-only)
     * - detach from parent
     * - remove from registry maps
     */
    public void destroy(int id) {
        if (!exists(id)) throw new IllegalArgumentException("destroy: unknown surface id=" + id);

        // 1) detach binding (UUID-only)
        String uuid = detachSurface(id);

        // 2) detach from parent node (scene graph)
        Spatial s = byId.get(id);
        if (s != null) {
            Node parent = s.getParent();
            if (parent != null) parent.detachChild(s);
        }

        // 3) drop registry entries
        byId.remove(id);
        kindById.remove(id);

        // (bindings already cleaned by detachSurface, but keep it sterile)
        surfaceToEntity.remove(id);

        emit("engine.surface.destroyed", "surfaceId", id, "entityUuid", uuid);
        if (log.isDebugEnabled()) log.debug("[surface] destroyed surfaceId={} entityUuid={}", id, uuid);
    }

    // ------------------------------------------------------------
    // attach-to-root batching
    // ------------------------------------------------------------

    private void scheduleAttachFlush() {
        if (!attachFlushScheduled.compareAndSet(false, true)) return;

        SimpleApplication a = app;
        if (a == null) return;

        a.enqueue(() -> {
            try {
                flushAttachToRoot();
            } finally {
                attachFlushScheduled.set(false);
            }
            return null;
        });
    }

    private void flushAttachToRoot() {
        SimpleApplication a = app;
        if (a == null) return;

        for (; ; ) {
            Integer id = pendingAttach.poll();
            if (id == null) break;
            Spatial s = byId.get(id);
            if (s == null) continue;
            a.getRootNode().attachChild(s);
        }
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    private int requireEntityIdFromUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid is blank");
        EntityUuids u = uuids;
        if (u == null)
            throw new IllegalStateException("UUID registry is not bound (SurfaceRegistry.attach(ctx) not called?)");
        int id = u.entityIdOf(uuid);
        if (id == EntityId.NULL) throw new IllegalArgumentException("unknown entityUuid=" + uuid);
        return id;
    }

    private String requireUuidOfEntityId(int entityId) {
        EntityUuids u = uuids;
        if (u == null)
            throw new IllegalStateException("UUID registry is not bound (SurfaceRegistry.attach(ctx) not called?)");
        String uuid = u.uuidStringOf(entityId);
        if (uuid == null || uuid.isBlank()) throw new IllegalStateException("no uuid for entityId=" + entityId);
        return uuid;
    }

    private ScriptEventBus bus() {
        Supplier<ScriptEventBus> s = busSupplier;
        return (s != null) ? s.get() : null;
    }

    private void emit(String topic, Object... kv) {
        ScriptEventBus b = bus();
        if (b == null) return;
        HashMap<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            if (k == null) continue;
            m.put(String.valueOf(k), kv[i + 1]);
        }
        try {
            b.emit(topic, m); } catch (Throwable ignored) { }
    }
}