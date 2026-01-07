// FILE: org/foxesworld/kalitech/engine/api/impl/SurfaceRegistry.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class SurfaceRegistry {

    private static final Logger log = LogManager.getLogger(SurfaceRegistry.class);

    private final SimpleApplication app;

    /**
     * IMPORTANT: bus can be null early-boot; resolve dynamically.
     */
    private final Supplier<ScriptEventBus> busSupplier;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> entityToSurface = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<Integer> pendingAttach = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean attachFlushScheduled = new AtomicBoolean(false);

    public SurfaceRegistry(SimpleApplication app) {
        this(app, (Supplier<ScriptEventBus>) () -> null);
    }

    /** Legacy ctor (keeps compatibility), but if you pass null here — it will stay null. Prefer Supplier ctor. */
    public SurfaceRegistry(SimpleApplication app, ScriptEventBus bus) {
        this(app, () -> bus);
    }

    /**
     * ✅ Preferred: dynamic bus resolve.
     */
    public SurfaceRegistry(SimpleApplication app, Supplier<ScriptEventBus> busSupplier) {
        this.app = Objects.requireNonNull(app, "app");
        this.busSupplier = (busSupplier != null) ? busSupplier : () -> null;
    }

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

    public void attach(int surfaceId, int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("attach: entityId must be > 0");
        if (!exists(surfaceId)) throw new IllegalStateException("attach: unknown surfaceId=" + surfaceId);

        Integer oldSurface = entityToSurface.put(entityId, surfaceId);
        if (oldSurface != null && oldSurface != surfaceId) surfaceToEntity.remove(oldSurface);

        surfaceToEntity.put(surfaceId, entityId);

        emit("engine.surface.attached", "surfaceId", surfaceId, "entityId", entityId);
    }

    public Integer detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);
        if (ent != null) emit("engine.surface.detached", "surfaceId", surfaceId, "entityId", ent);
        return ent;
    }

    public Integer detachEntity(int entityId) {
        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);
        if (surf != null) emit("engine.surface.detached", "surfaceId", surf, "entityId", entityId);
        return surf;
    }

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