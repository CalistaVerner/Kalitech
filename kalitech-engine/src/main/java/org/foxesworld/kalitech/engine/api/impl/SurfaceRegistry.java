// FILE: org/foxesworld/kalitech/engine/api/impl/SurfaceRegistry.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SurfaceRegistry {

    private static final Logger log = LogManager.getLogger(SurfaceRegistry.class);

    private final SimpleApplication app;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();

    // attachment maps
    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> entityToSurface = new ConcurrentHashMap<>();

    // --- scene attach batching ---
    private final ConcurrentLinkedQueue<Integer> pendingAttach = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean attachFlushScheduled = new AtomicBoolean(false);

    public SurfaceRegistry(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");
    }

    /**
     * Register a Spatial and return a host-safe handle.
     * IMPORTANT: Registry does NOT keep SurfaceApi reference (no bind), caller provides it explicitly.
     */
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

        // Keep current contract: SurfaceHandle still contains api reference (if your SurfaceHandle class expects it).
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
    }

    /** detach by surface; returns detached entityId (or null) */
    public Integer detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);
        return ent;
    }

    /** detach by entity; returns detached surfaceId (or null) */
    public Integer detachEntity(int entityId) {
        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);
        return surf;
    }

    public void attachToRoot(int id) {
        if (!exists(id)) throw new IllegalArgumentException("attachToRoot: unknown surface id=" + id);
        pendingAttach.add(id);
        scheduleAttachFlush();
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
        Spatial s = byId.get(id);
        if (s == null) return;
        if (s.getParent() != null) s.removeFromParent();
    }

    public void destroy(int id) {
        Spatial s = byId.remove(id);
        kindById.remove(id);

        detachSurface(id);

        if (s != null) {
            try {
                if (s.getParent() != null) s.removeFromParent();
            } catch (Throwable t) {
                log.warn("destroy: failed to detach surface id={}", id, t);
            }
        }
    }
}