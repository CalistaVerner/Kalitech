/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.AddToSpaceQueue;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.EnginePhysicsEventSink;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.EnginePhysicsSpaceProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsEventSink;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsSpaceProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfigParser;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyManager;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CachedCollisionShapeProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CollisionShapeProvider;

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
        this.bodyManager = new PhysicsBodyManager(log, this.registry, this.configParser, this.shapeProvider, this.eventSink, this.spaceProvider, this::entityOfSurface);
    }

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
        return this.engine;
    }

    public SurfaceRegistry surfaces() {
        return this.surfaces;
    }

    public PhysicsRegistry registry() {
        return this.registry;
    }

    public PhysicsSpace requireSpace() {
        return this.spaceProvider.requireSpace();
    }

    public void enqueueAddToSpace(RigidBodyControl rb) {
        if (rb == null) {
            return;
        }
        this.addQueue.enqueue(0, rb);
        this.scheduleAddFlush();
    }

    public void enqueueAddToSpace(PhysicsBodyHandle h) {
        RigidBodyControl rb;
        if (h == null) {
            return;
        }
        try {
            rb = h.__raw();
        }
        catch (Throwable t) {
            this.log.debug("[physics] enqueueAddToSpace failed to access raw control bodyId={}", (Object)h.id, (Object)t);
            return;
        }
        if (rb == null) {
            return;
        }
        this.addQueue.enqueue(h.id, rb);
        this.scheduleAddFlush();
    }

    public void flushPendingAddNow() {
        PhysicsSpace sp = this.spaceProvider.getSpaceOrNull();
        if (sp == null) {
            return;
        }
        SurfaceRegistry sr = this.surfaces;
        this.addQueue.flushTo(sp, bodyId -> {
            if (bodyId <= 0) {
                return;
            }
            PhysicsBodyHandle h = this.registry.get(bodyId);
            if (h == null) {
                return;
            }
            this.eventSink.emitBodyAdded(h, sr, this.entityOfSurface(h.surfaceId));
        });
    }

    private void scheduleAddFlush() {
        SimpleApplication a = this.app;
        if (a == null) {
            return;
        }
        if (!this.addFlushScheduled.compareAndSet(false, true)) {
            return;
        }
        a.enqueue(() -> {
            try {
                this.flushPendingAddNow();
            }
            finally {
                this.addFlushScheduled.set(false);
                if (!this.addQueue.isEmpty()) {
                    this.scheduleAddFlush();
                }
            }
            return null;
        });
    }

    public PhysicsBodyHandle createBody(Object cfg) {
        PhysicsBodyHandle h = this.bodyManager.createBody(cfg);
        if (h != null) {
            this.enqueueAddToSpace(h);
        }
        return h;
    }

    public void removeBody(Object handleOrId) {
        int id = this.registry.resolveBodyId(handleOrId);
        if (id <= 0) {
            return;
        }
        this.removeBodyById(id);
    }

    public void removeBodyById(int id) {
        this.addQueue.removeByBodyId(id);
        PhysicsBodyHandle removed = this.bodyManager.removeBodyById(id);
        if (removed == null) {
            return;
        }
        IntConsumer cb = this.onBodyRemoved;
        if (cb != null) {
            try {
                cb.accept(removed.id);
            }
            catch (Throwable t) {
                this.log.debug("[physics] onBodyRemoved callback failed bodyId={}", (Object)removed.id, (Object)t);
            }
        }
    }

    public void clearAll() {
        this.bodyManager.clearAll(this.addQueue);
    }

    private String entityOfSurface(int surfaceId) {
        SurfaceRegistry sr = this.surfaces;
        if (sr == null || surfaceId <= 0) {
            return null;
        }
        String uuid = sr.attachedEntityUuid(surfaceId);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return uuid;
    }
}

