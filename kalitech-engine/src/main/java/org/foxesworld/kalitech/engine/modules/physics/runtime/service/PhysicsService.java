// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsService.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsEntityResolver;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfigParser;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyManager;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CachedCollisionShapeProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CollisionShapeProvider;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Physics runtime facade.
 *
 * <p>Public API remains stable; internal responsibilities are delegated to dedicated components.</p>
 */
public final class PhysicsService {

    private final EngineApiImpl engine;
    private final Logger log;

    private final PhysicsRegistry registry;

    private final PhysicsSpaceProvider spaceProvider;
    private final PhysicsEventSink eventSink;

    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);

    private volatile SimpleApplication app;
    private volatile SurfaceRegistry surfaces;
    private volatile IntConsumer onBodyRemoved;

    private final AddToSpaceQueue addQueue;
    private final PhysicsBodyConfigParser configParser;
    private final CollisionShapeProvider shapeProvider;
    private final PhysicsBodyManager bodyManager;

    public PhysicsService(EngineApiImpl engine, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = Objects.requireNonNull(log, "log");

        this.registry = new PhysicsRegistry(log);

        this.spaceProvider = new EnginePhysicsSpaceProvider(engine);
        this.eventSink = new EnginePhysicsEventSink(engine, log);

        this.addQueue = new AddToSpaceQueue(log);
        this.configParser = new PhysicsBodyConfigParser();
        this.shapeProvider = new CachedCollisionShapeProvider(log);
        this.bodyManager = new PhysicsBodyManager(
                log,
                registry,
                configParser,
                shapeProvider,
                eventSink,
                spaceProvider,
                this::entityOfSurface
        );
    }

    /**
     * Sets a callback invoked after a body is removed from the registry.
     */
    public void setOnBodyRemoved(IntConsumer onBodyRemoved) {
        this.onBodyRemoved = onBodyRemoved;
    }

    public void bind(SimpleApplication app, SurfaceRegistry surfaces) {
        this.app = app;
        this.surfaces = surfaces;
        this.bodyManager.bindSurfaces(surfaces);
    }

    public void unbind() {
        this.app = null;
        this.surfaces = null;
        this.bodyManager.bindSurfaces(null);
    }

    public EngineApiImpl engine() {
        return engine;
    }

    public SurfaceRegistry surfaces() {
        return surfaces;
    }

    public PhysicsRegistry registry() {
        return registry;
    }

    public PhysicsSpace requireSpace() {
        return spaceProvider.requireSpace();
    }

    public void enqueueAddToSpace(RigidBodyControl rb) {
        if (rb == null) return;
        addQueue.enqueue(rb);
        scheduleAddFlush();
    }

    public void flushPendingAddNow() {
        PhysicsSpace sp = spaceProvider.getSpaceOrNull();
        if (sp == null) return;

        SurfaceRegistry sr = this.surfaces;

        addQueue.flushTo(sp, bodyId -> {
            PhysicsBodyHandle h = registry.get(bodyId);
            if (h == null) return;

            eventSink.emitBodyAdded(h, sr, entityOfSurface(h.surfaceId));
        });
    }

    private void scheduleAddFlush() {
        SimpleApplication a = this.app;
        if (a == null) return;
        if (!addFlushScheduled.compareAndSet(false, true)) return;

        a.enqueue(() -> {
            try {
                flushPendingAddNow();
            } finally {
                addFlushScheduled.set(false);
                if (!addQueue.isEmpty()) scheduleAddFlush();
            }
            return null;
        });
    }

    public PhysicsBodyHandle createBody(Object cfg) {
        PhysicsBodyHandle h = bodyManager.createBody(cfg);
        if (h != null) enqueueAddToSpace(h.__raw());
        return h;
    }

    public void removeBody(Object handleOrId) {
        int id = registry.resolveBodyId(handleOrId);
        if (id <= 0) return;
        removeBodyById(id);
    }

    public void removeBodyById(int id) {
        PhysicsBodyHandle removed = bodyManager.removeBodyById(id);
        if (removed == null) return;

        IntConsumer cb = this.onBodyRemoved;
        if (cb != null) {
            try {
                cb.accept(removed.id);
            } catch (Throwable t) {
                log.debug("[physics] onBodyRemoved callback failed bodyId={}", removed.id, t);
            }
        }
    }

    public void clearAll() {
        bodyManager.clearAll(addQueue);
    }

    private String entityOfSurface(int surfaceId) {
        SurfaceRegistry sr = this.surfaces;
        if (sr == null || surfaceId <= 0) return null;
        return PhysicsEntityResolver.entityOfSpatial(sr.get(surfaceId));
    }
}