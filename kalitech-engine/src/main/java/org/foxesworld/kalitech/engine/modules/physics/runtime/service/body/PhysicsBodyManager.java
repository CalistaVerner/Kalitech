// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsBodyManager.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.AddToSpaceQueue;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsEventSink;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsSpaceProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CachedCollisionShapeProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CollisionShapeProvider;

import java.util.Objects;
import java.util.function.IntFunction;

public final class PhysicsBodyManager {

    private final Logger log;
    private final PhysicsRegistry registry;

    private final PhysicsBodyConfigParser parser;
    private final CollisionShapeProvider shapeProvider;

    private final PhysicsEventSink events;
    private final PhysicsSpaceProvider spaceProvider;

    private final IntFunction<String> entityResolver;

    private volatile SurfaceRegistry surfaces;

    public PhysicsBodyManager(
            Logger log,
            PhysicsRegistry registry,
            PhysicsBodyConfigParser parser,
            CollisionShapeProvider shapeProvider,
            PhysicsEventSink events,
            PhysicsSpaceProvider spaceProvider,
            IntFunction<String> entityResolver
    ) {
        this.log = Objects.requireNonNull(log, "log");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.shapeProvider = Objects.requireNonNull(shapeProvider, "shapeProvider");
        this.events = Objects.requireNonNull(events, "events");
        this.spaceProvider = Objects.requireNonNull(spaceProvider, "spaceProvider");
        this.entityResolver = Objects.requireNonNull(entityResolver, "entityResolver");
    }

    private static RigidBodyControl safeRaw(PhysicsBodyHandle h) {
        try {
            return h.__raw();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void bindSurfaces(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    public PhysicsBodyHandle createBody(Object cfgObj) {
        spaceProvider.requireSpace();

        PhysicsBodyConfig cfg = parser.parse(cfgObj);

        SurfaceRegistry sr = this.surfaces;
        if (sr == null) throw new IllegalStateException("physics.body: SurfaceRegistry not bound");

        Spatial spatial = sr.get(cfg.surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + cfg.surfaceId);

        PhysicsBodyHandle existing = registry.getExistingBySurface(cfg.surfaceId);
        if (existing != null) return existing;

        CollisionShape shape = shapeProvider.resolveShape(cfg, spatial);

        RigidBodyControl rb = new RigidBodyControl(shape, cfg.mass);
        rb.setFriction(cfg.friction);
        rb.setRestitution(cfg.restitution);
        rb.setDamping(cfg.dampingLinear, cfg.dampingAngular);

        rb.setKinematic(cfg.kinematic);
        if (cfg.lockRotation) rb.setAngularFactor(0f);

        if (cfg.dynamic && !cfg.kinematic) {
            rb.setCcdMotionThreshold(cfg.ccdMotionThreshold);
            rb.setCcdSweptSphereRadius(cfg.ccdSweptSphereRadius);
        }

        spatial.addControl(rb);

        int id = registry.nextId();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, cfg.surfaceId, rb);

        registry.put(handle);
        registry.indexCollisionObject(handle);

        events.emitBodyCreated(handle, cfg.mass, cfg.kinematic, cfg.lockRotation, entityResolver.apply(cfg.surfaceId));

        return handle;
    }

    public PhysicsBodyHandle removeBodyById(int id) {
        if (id <= 0) return null;

        PhysicsBodyHandle h = registry.remove(id);
        if (h == null) return null;

        registry.unindexCollisionObject(h);
        registry.removeSurfaceBinding(h.surfaceId, h.id);

        RigidBodyControl rb = safeRaw(h);
        if (rb != null) {
            PhysicsSpace sp = spaceProvider.getSpaceOrNull();
            if (sp != null) {
                try {
                    sp.remove(rb);
                } catch (Throwable t) {
                    log.debug("[physics] removeFromSpace failed bodyId={}", h.id, t);
                }
            }

            SurfaceRegistry sr = surfaces;
            if (sr != null) {
                try {
                    Spatial spx = sr.get(h.surfaceId);
                    if (spx != null) spx.removeControl(rb);
                } catch (Throwable t) {
                    log.debug("[physics] removeControl failed bodyId={} surfaceId={}", h.id, h.surfaceId, t);
                }
            }
        }

        events.emitBodyRemoved(h, entityResolver.apply(h.surfaceId));
        return h;
    }

    public void clearAll(AddToSpaceQueue addQueue) {
        if (addQueue != null) addQueue.clear();

        if (shapeProvider instanceof CachedCollisionShapeProvider cached) {
            cached.clearCache();
        }

        PhysicsSpace sp = spaceProvider.getSpaceOrNull();
        SurfaceRegistry sr = this.surfaces;

        try {
            for (var e : registry.entries()) {
                PhysicsBodyHandle h = e.value();
                if (h == null) continue;

                RigidBodyControl rb = safeRaw(h);
                if (rb != null) {
                    if (addQueue != null) addQueue.remove(rb);

                    if (sp != null) {
                        try {
                            sp.remove(rb);
                        } catch (Throwable t) {
                            log.debug("[physics] clearAll removeFromSpace failed bodyId={}", h.id, t);
                        }
                    }

                    if (sr != null) {
                        try {
                            Spatial spx = sr.get(h.surfaceId);
                            if (spx != null) spx.removeControl(rb);
                        } catch (Throwable t) {
                            log.debug("[physics] clearAll removeControl failed bodyId={}", h.id, t);
                        }
                    }
                }
            }
        } finally {
            registry.clearAll();
        }
    }
}