package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.EditorLinesApi;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

    public SurfaceRegistry(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");

    }

    public SurfaceApi.SurfaceHandle register(Spatial spatial, String kind) {
        Objects.requireNonNull(spatial, "spatial");
        String k = (kind == null || kind.isBlank()) ? "surface" : kind.trim();

        int id = ids.getAndIncrement();
        byId.put(id, spatial);
        kindById.put(id, k);

        return new SurfaceApi.SurfaceHandle(id, k);
    }

    public Spatial get(int id) {
        return byId.get(id);
    }

    public String kind(int id) {
        return kindById.get(id);
    }

    public boolean exists(int id) {
        return byId.containsKey(id);
    }

    public int attachedEntity(int surfaceId) {
        return surfaceToEntity.getOrDefault(surfaceId, 0);
    }

    public void attach(int surfaceId, int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("attach: entityId must be > 0");
        if (!exists(surfaceId)) throw new IllegalStateException("attach: unknown surfaceId=" + surfaceId);

        // one surface per entity (simple, predictable)
        Integer oldSurface = entityToSurface.put(entityId, surfaceId);
        if (oldSurface != null && oldSurface != surfaceId) {
            surfaceToEntity.remove(oldSurface);
        }

        surfaceToEntity.put(surfaceId, entityId);
    }

    public void detachSurface(int surfaceId) {
        Integer e = surfaceToEntity.remove(surfaceId);
        if (e != null) {
            entityToSurface.remove(e, surfaceId);
        }
    }

    public Integer detachEntity(int entityId) {
        Integer s = entityToSurface.remove(entityId);
        if (s != null) {
            surfaceToEntity.remove(s, entityId);
        }
        return s;
    }

    public void attachToRoot(int surfaceId) {
        Spatial s = byId.get(surfaceId);
        if (s == null) return;
        if (s.getParent() == null) {
            app.getRootNode().attachChild(s);
        }
    }

    public void detachFromParent(int surfaceId) {
        Spatial s = byId.get(surfaceId);
        if (s != null) s.removeFromParent();
    }

    public Spatial destroy(int surfaceId) {
        detachSurface(surfaceId);

        Spatial s = byId.remove(surfaceId);
        kindById.remove(surfaceId);

        if (s != null) {
            try {
                s.removeFromParent();
            } catch (Throwable t) {
                log.warn("destroy: removeFromParent failed for id={}", surfaceId, t);
            }
        }
        return s;
    }
}