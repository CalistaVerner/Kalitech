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
     * Optional: bound from ApiContext (ECS UUID registry).
     * If null - UUID-related methods still work only if callers pass entityId.
     */
    private EntityUuids uuids;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();
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
        if (ctx != null) {
            this.app = ctx.app;
            this.busSupplier = ctx.engine::getBus;
            this.uuids = (ctx.ecs != null) ? ctx.ecs.uuids() : null;
        }
        if (log.isDebugEnabled()) {
            log.debug("[service] attached id='{}' app={} busSupplier={} uuids={}",
                    id(),
                    (app != null ? app.getClass().getSimpleName() : "null"),
                    (busSupplier != null ? busSupplier.getClass().getName() : "null"),
                    (uuids != null));
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
                log.debug("Registered spatial id={} kind={} name={} worldPos={}",
                        id, k, spatial.getName(), spatial.getWorldTranslation());
            } catch (Throwable ignored) {
                log.debug("Registered spatial id={} kind={} name={}", id, k, spatial.getName());
            }
        }

        emit("engine.surface.registered", "surfaceId", id, "kind", k, "name", spatial.getName());
        return new SurfaceApi.SurfaceHandle(id, k, api);
    }

    public Spatial get(int id) { return byId.get(id); }
    public String kind(int id) { return kindById.get(id); }
    public boolean exists(int id) { return byId.containsKey(id); }

    public Integer attachedEntity(int surfaceId) { return surfaceToEntity.get(surfaceId); }
    public Integer attachedSurface(int entityId) { return entityToSurface.get(entityId); }

    // ---------------------------------------------------------------------
    // UUID helpers
    // ---------------------------------------------------------------------

    private int resolveEntityIdFromUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return 0;
        if (uuids == null)
            throw new IllegalStateException("UUID registry is not bound (SurfaceRegistry.attach(ctx) not called?)");
        int id = uuids.entityIdOf(uuid);
        return (id == EntityId.NULL) ? 0 : id;
    }

    private String uuidOfEntity(int entityId) {
        if (entityId <= 0) return "";
        EntityUuids u = uuids;
        if (u == null) return "";
        return u.uuidStringOf(entityId);
    }

    // ---------------------------------------------------------------------
    // Attach / Detach (entityId core)
    // ---------------------------------------------------------------------

    public void attach(int surfaceId, int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("attach: entityId must be > 0");
        if (!exists(surfaceId)) throw new IllegalStateException("attach: unknown surfaceId=" + surfaceId);

        Integer oldSurface = entityToSurface.put(entityId, surfaceId);
        if (oldSurface != null && oldSurface != surfaceId) surfaceToEntity.remove(oldSurface);

        surfaceToEntity.put(surfaceId, entityId);

        String uuid = uuidOfEntity(entityId);
        if (!uuid.isEmpty()) {
            emit("engine.surface.attached", "surfaceId", surfaceId, "entityId", entityId, "uuid", uuid);
        } else {
            emit("engine.surface.attached", "surfaceId", surfaceId, "entityId", entityId);
        }
    }

    /**
     * UUID-first attach (public API). Internally still maps to entityId.
     */
    public void attachUuid(int surfaceId, String entityUuid) {
        int entityId = resolveEntityIdFromUuid(entityUuid);
        if (entityId <= 0) throw new IllegalArgumentException("attachUuid: cannot resolve entity uuid=" + entityUuid);
        attach(surfaceId, entityId);
    }

    public Integer detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);

        if (ent != null) {
            String uuid = uuidOfEntity(ent);
            if (!uuid.isEmpty()) {
                emit("engine.surface.detached", "surfaceId", surfaceId, "entityId", ent, "uuid", uuid);
            } else {
                emit("engine.surface.detached", "surfaceId", surfaceId, "entityId", ent);
            }
        }
        return ent;
    }

    public Integer detachEntity(int entityId) {
        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);

        if (surf != null) {
            String uuid = uuidOfEntity(entityId);
            if (!uuid.isEmpty()) {
                emit("engine.surface.detached", "surfaceId", surf, "entityId", entityId, "uuid", uuid);
            } else {
                emit("engine.surface.detached", "surfaceId", surf, "entityId", entityId);
            }
        }
        return surf;
    }

    /**
     * UUID-first detach: remove mapping by UUID.
     */
    public Integer detachEntityUuid(String entityUuid) {
        int entityId = resolveEntityIdFromUuid(entityUuid);
        if (entityId <= 0) return null;
        return detachEntity(entityId);
    }

    /**
     * Convenience: get attached entity UUID for a surface.
     */
    public String attachedEntityUuid(int surfaceId) {
        Integer ent = attachedEntity(surfaceId);
        if (ent == null || ent <= 0) return "";
        return uuidOfEntity(ent);
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
        detachSurface(id);
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
        ScriptEventBus bus = null;
        try {
            bus = busSupplier.get();
        } catch (Throwable ignored) {}
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