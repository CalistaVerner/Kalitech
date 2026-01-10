// FILE: org/foxesworld/kalitech/engine/api/services/SurfaceRegistry.java
package org.foxesworld.kalitech.engine.api.services;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.EngineService;
import org.foxesworld.kalitech.engine.ecs.EntityId;
import org.foxesworld.kalitech.engine.ecs.EntityUuids;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class SurfaceRegistry implements EngineService {

    private static final Logger log = LogManager.getLogger(SurfaceRegistry.class);

    private SimpleApplication app;

    /**
     * IMPORTANT: bus can be null early-boot; resolve dynamically.
     */
    private Supplier<ScriptEventBus> busSupplier;

    /**
     * Internal mapping uses dense entityId for speed, but PUBLIC API / EVENTS / LOGS are UUID-only.
     */
    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();
    /**
     * Bound from ApiContext (ECS UUID registry). Required for UUID-only behavior.
     */
    private EntityUuids uuids;
    private final ConcurrentHashMap<Integer, Integer> entityToSurface = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<Integer> pendingAttach = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean attachFlushScheduled = new AtomicBoolean(false);

    // ---------------------------------------------------------------------
    // Service identity
    // ---------------------------------------------------------------------

    @Override
    public String id() {
        return "surfaceRegistry";
    }

    // ---------------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------------

    public SurfaceRegistry(SimpleApplication app) {
        this(app, (Supplier<ScriptEventBus>) () -> null);
    }

    /** Legacy ctor (keeps compatibility), but if you pass null here — it will stay null. Prefer Supplier ctor. */
    public SurfaceRegistry(SimpleApplication app, ScriptEventBus bus) {
        this(app, () -> bus);
    }

    /**
     * Preferred: dynamic bus resolve.
     */
    public SurfaceRegistry(SimpleApplication app, Supplier<ScriptEventBus> busSupplier) {
        this.app = Objects.requireNonNull(app, "app");
        this.busSupplier = (busSupplier != null) ? busSupplier : () -> null;
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @Override
    public void attach(ApiContext ctx) {
        if (ctx == null) throw new NullPointerException("ctx");

        this.app = ctx.app;
        this.busSupplier = ctx.engine::getBus;

        if (ctx.ecs == null) throw new IllegalStateException("SurfaceRegistry: ctx.ecs is null");
        this.uuids = ctx.ecs.uuids();
        if (this.uuids == null) throw new IllegalStateException("SurfaceRegistry: ctx.ecs.uuids() is null");

        if (log.isDebugEnabled()) {
            log.debug("[service] attached id='{}' uuidsBound=true", id());
        }
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
        if (log.isDebugEnabled()) log.debug("[service] detached id='{}'", id());
    }

    // ---------------------------------------------------------------------
    // Registry
    // ---------------------------------------------------------------------

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

    /**
     * Internal only (dense-id).
     */
    public Integer attachedEntity(int surfaceId) { return surfaceToEntity.get(surfaceId);
    }

    /** Internal only (dense-id). */
    public Integer attachedSurface(int entityId) { return entityToSurface.get(entityId); }

    // ---------------------------------------------------------------------
    // UUID helpers (STRICT)
    // ---------------------------------------------------------------------

    private int requireEntityIdFromUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid is blank");
        EntityUuids u = uuids;
        if (u == null)
            throw new IllegalStateException("UUID registry is not bound (SurfaceRegistry.attach(ctx) not called?)");
        int id = u.entityIdOf(uuid);
        if (id == EntityId.NULL) throw new IllegalArgumentException("unknown entity uuid=" + uuid);
        return id;
    }

    private String requireUuidOfEntity(int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
        EntityUuids u = uuids;
        if (u == null)
            throw new IllegalStateException("UUID registry is not bound (SurfaceRegistry.attach(ctx) not called?)");
        String uuid = u.uuidStringOf(entityId);
        if (uuid == null || uuid.isBlank()) throw new IllegalStateException("no uuid for entityId=" + entityId);
        return uuid;
    }

    // ---------------------------------------------------------------------
    // Attach / Detach
    // ---------------------------------------------------------------------

    /**
     * INTERNAL attach by dense entityId (engine-internal).
     * Events & logs are UUID-only.
     */
    public void attach(int surfaceId, int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("attach: entityId must be > 0");
        if (!exists(surfaceId)) throw new IllegalStateException("attach: unknown surfaceId=" + surfaceId);

        Integer oldSurface = entityToSurface.put(entityId, surfaceId);
        if (oldSurface != null && oldSurface != surfaceId) surfaceToEntity.remove(oldSurface);

        surfaceToEntity.put(surfaceId, entityId);

        String uuid = requireUuidOfEntity(entityId);

        if (log.isDebugEnabled()) {
            log.debug("[surface] attached surfaceId={} uuid={}", surfaceId, uuid);
        }

        emit("engine.surface.attached", "surfaceId", surfaceId, "uuid", uuid);
    }

    /**
     * PUBLIC attach: UUID-only.
     */
    public void attachUuid(int surfaceId, String entityUuid) {
        int entityId = requireEntityIdFromUuid(entityUuid);
        attach(surfaceId, entityId);
    }

    /**
     * Detach mapping by surface id.
     * Event payload & logs are UUID-only.
     */
    public String detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);

        if (ent != null) {
            String uuid = requireUuidOfEntity(ent);

            if (log.isDebugEnabled()) {
                log.debug("[surface] detached surfaceId={} uuid={}", surfaceId, uuid);
            }

            emit("engine.surface.detached", "surfaceId", surfaceId, "uuid", uuid);
            return uuid;
        }
        return "";
    }

    /**
     * INTERNAL detach by dense entityId (engine-internal).
     * Event payload & logs are UUID-only.
     */
    public Integer detachEntity(int entityId) {
        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);

        if (surf != null) {
            String uuid = requireUuidOfEntity(entityId);

            if (log.isDebugEnabled()) {
                log.debug("[surface] detached surfaceId={} uuid={}", surf, uuid);
            }

            emit("engine.surface.detached", "surfaceId", surf, "uuid", uuid);
        }
        return surf;
    }

    /**
     * PUBLIC detach: UUID-only.
     */
    public Integer detachEntityUuid(String entityUuid) {
        int entityId = requireEntityIdFromUuid(entityUuid);
        return detachEntity(entityId);
    }

    /**
     * PUBLIC query: UUID-only.
     */
    public String attachedEntityUuid(int surfaceId) {
        Integer ent = attachedEntity(surfaceId);
        if (ent == null || ent <= 0) return "";
        return requireUuidOfEntity(ent);
    }

    // ---------------------------------------------------------------------
    // Scene graph ops
    // ---------------------------------------------------------------------

    public void attachToRoot(int id) {
        if (!exists(id)) throw new IllegalArgumentException("attachToRoot: unknown surface id=" + id);
        pendingAttach.add(id);
        scheduleAttachFlush();

        emit("engine.surface.attachToRoot", "surfaceId", id);
    }

    private void scheduleAttachFlush() {
        if (!attachFlushScheduled.compareAndSet(false, true)) return;

        app.enqueue(() -> {
            try {
                flushPendingAttach();
            } finally {
                attachFlushScheduled.set(false);
                if (!pendingAttach.isEmpty()) scheduleAttachFlush();
            }
            return null;
        });
    }

    private void flushPendingAttach() {
        Integer id;
        while ((id = pendingAttach.poll()) != null) {
            Spatial s = byId.get(id);
            if (s == null) continue;
            if (s.getParent() == null) app.getRootNode().attachChild(s);
        }
    }

    public void detachFromParent(int id) {
        app.enqueue(() -> {
            Spatial s = byId.get(id);
            if (s == null) return null;
            try {
                if (s.getParent() != null) s.removeFromParent();
            } catch (Throwable t) {
                log.warn("detachFromParent: failed id={}", id, t);
            }
            emit("engine.surface.detachedFromParent", "surfaceId", id);
            return null;
        });
    }

    public void destroy(int id) {
        final Spatial s = byId.remove(id);
        kindById.remove(id);

        // detach mapping first (emits+logs detached if there was one)
        detachSurface(id);

        if (log.isDebugEnabled()) {
            log.debug("[surface] destroyed surfaceId={}", id);
        }

        emit("engine.surface.destroyed", "surfaceId", id);

        if (s != null) {
            app.enqueue(() -> {
                try {
                    if (s.getParent() != null) s.removeFromParent();
                } catch (Throwable t) {
                    log.warn("destroy: failed to detach surface id={}", id, t);
                }
                return null;
            });
        }
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    private void emit(String topic, Object... kv) {
        ScriptEventBus bus;
        try {
            bus = (busSupplier != null) ? busSupplier.get() : null;
        } catch (Throwable ignored) {
            return;
        }
        if (bus == null) return;

        try {
            java.util.HashMap<String, Object> m = new java.util.HashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                Object k = kv[i];
                if (k == null) continue;
                m.put(String.valueOf(k), kv[i + 1]);
            }
            bus.emit(topic, m);
        } catch (Throwable ignored) {
        }
    }
}