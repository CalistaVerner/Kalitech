// FILE: org/foxesworld/kalitech/engine/api/impl/SurfaceRegistry.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SurfaceRegistry {

    private static final Logger log = LogManager.getLogger(SurfaceRegistry.class);

    private final SimpleApplication app;

    private volatile SurfaceApi surfaceApi;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Spatial> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> kindById = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, Integer> surfaceToEntity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> entityToSurface = new ConcurrentHashMap<>();

    public SurfaceRegistry(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");
    }

    public void bindSurfaceApi(SurfaceApi api) {
        Objects.requireNonNull(api, "api");
        if (this.surfaceApi != null && this.surfaceApi != api) {
            throw new IllegalStateException("SurfaceRegistry.bindSurfaceApi: already bound to another SurfaceApi instance");
        }
        this.surfaceApi = api;
    }

    public SurfaceApi.SurfaceHandle register(Spatial spatial, String kind) {
        Objects.requireNonNull(spatial, "spatial");
        String k = (kind == null || kind.isBlank()) ? "surface" : kind.trim();
        log.debug("Registered {} of type {} located at {}", spatial.getName(), kind, spatial.getWorldTranslation());

        int id = ids.getAndIncrement();
        byId.put(id, spatial);
        kindById.put(id, k);

        SurfaceApi api = this.surfaceApi;
        if (api == null) {
            throw new IllegalStateException("SurfaceRegistry.register: SurfaceApi is not bound (call bindSurfaceApi)");
        }
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

    /** ✅ detach by surface; returns detached entityId (or null) */
    public Integer detachSurface(int surfaceId) {
        Integer ent = surfaceToEntity.remove(surfaceId);
        if (ent != null) entityToSurface.remove(ent);
        return ent;
    }

    /** ✅ detach by entity; returns detached surfaceId (or null) */
    public Integer detachEntity(int entityId) {
        Integer surf = entityToSurface.remove(entityId);
        if (surf != null) surfaceToEntity.remove(surf);
        return surf;
    }

    public void attachToRoot(int id) {
        Spatial s = byId.get(id);
        if (s == null) throw new IllegalArgumentException("attachToRoot: unknown surface id=" + id);
        if (s.getParent() == null) app.getRootNode().attachChild(s);
    }

    public void detachFromParent(int id) {
        Spatial s = byId.get(id);
        if (s == null) return;
        if (s.getParent() != null) s.removeFromParent();
    }

    public void destroy(int id) {
        Spatial s = byId.remove(id);
        kindById.remove(id);

        // ensure maps are clean (safe even if already detached)
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