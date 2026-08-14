/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.scene.Spatial
 *  com.jme3.scene.control.Control
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.Objects;
import java.util.function.IntFunction;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.AddToSpaceQueue;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsEventSink;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsSpaceProvider;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfig;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.body.PhysicsBodyConfigParser;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.collision.CollisionShapeProvider;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;

public final class PhysicsBodyManager {
    private final Logger log;
    private final PhysicsRegistry registry;
    private final PhysicsBodyConfigParser parser;
    private final CollisionShapeProvider shapeProvider;
    private final PhysicsEventSink events;
    private final PhysicsSpaceProvider spaceProvider;
    private final IntFunction<String> entityResolver;
    private volatile SurfaceRegistry surfaces;

    public PhysicsBodyManager(Logger log, PhysicsRegistry registry, PhysicsBodyConfigParser parser, CollisionShapeProvider shapeProvider, PhysicsEventSink events, PhysicsSpaceProvider spaceProvider, IntFunction<String> entityResolver) {
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
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    public void bindSurfaces(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    public PhysicsBodyHandle createBody(Object cfgObj) {
        this.spaceProvider.requireSpace();
        PhysicsBodyConfig cfg = this.parser.parse(cfgObj);
        SurfaceRegistry sr = this.surfaces;
        if (sr == null) {
            throw new IllegalStateException("physics.body: SurfaceRegistry not bound");
        }
        Spatial spatial = sr.get(cfg.surfaceId);
        if (spatial == null) {
            throw new IllegalStateException("physics.body: unknown surfaceId=" + cfg.surfaceId);
        }
        PhysicsBodyHandle existing = this.registry.getExistingBySurface(cfg.surfaceId);
        if (existing != null) {
            return existing;
        }
        CollisionShape shape = this.shapeProvider.resolveShape(cfg, spatial);
        RigidBodyControl rb = new RigidBodyControl(shape, cfg.mass);
        rb.setFriction(cfg.friction);
        rb.setRestitution(cfg.restitution);
        rb.setDamping(cfg.dampingLinear, cfg.dampingAngular);
        rb.setKinematic(cfg.kinematic);
        if (cfg.lockRotation) {
            rb.setAngularFactor(0.0f);
        }
        if (cfg.dynamic && !cfg.kinematic) {
            rb.setCcdMotionThreshold(cfg.ccdMotionThreshold);
            rb.setCcdSweptSphereRadius(cfg.ccdSweptSphereRadius);
        }
        spatial.addControl((Control)rb);
        int id = this.registry.nextId();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, cfg.surfaceId, rb);
        this.registry.put(handle);
        this.registry.indexCollisionObject(handle);
        this.events.emitBodyCreated(handle, cfg.mass, cfg.kinematic, cfg.lockRotation, this.entityResolver.apply(cfg.surfaceId));
        return handle;
    }

    public PhysicsBodyHandle removeBodyById(int id) {
        if (id <= 0) {
            return null;
        }
        PhysicsBodyHandle h = this.registry.remove(id);
        if (h == null) {
            return null;
        }
        this.registry.unindexCollisionObject(h);
        this.registry.removeSurfaceBinding(h.surfaceId, h.id);
        RigidBodyControl rb = PhysicsBodyManager.safeRaw(h);
        if (rb != null) {
            SurfaceRegistry sr;
            PhysicsSpace sp = this.spaceProvider.getSpaceOrNull();
            if (sp != null) {
                try {
                    sp.remove(rb);
                }
                catch (Throwable t) {
                    this.log.debug("[physics] removeFromSpace failed bodyId={}", (Object)h.id, (Object)t);
                }
            }
            if ((sr = this.surfaces) != null) {
                try {
                    Spatial spx = sr.get(h.surfaceId);
                    if (spx != null) {
                        spx.removeControl((Control)rb);
                    }
                }
                catch (Throwable t) {
                    this.log.debug("[physics] removeControl failed bodyId={} surfaceId={}", (Object)h.id, (Object)h.surfaceId, (Object)t);
                }
            }
        }
        this.events.emitBodyRemoved(h, this.entityResolver.apply(h.surfaceId));
        return h;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clearAll(AddToSpaceQueue addQueue) {
        if (addQueue != null) {
            addQueue.clear();
        }
        this.shapeProvider.clear();
        PhysicsSpace sp = this.spaceProvider.getSpaceOrNull();
        SurfaceRegistry sr = this.surfaces;
        try {
            for (IntObjectMap.Entry<PhysicsBodyHandle> e : this.registry.entries()) {
                RigidBodyControl rb;
                PhysicsBodyHandle h = e.value();
                if (h == null || (rb = PhysicsBodyManager.safeRaw(h)) == null) continue;
                if (addQueue != null) {
                    addQueue.remove(rb);
                }
                if (sp != null) {
                    try {
                        sp.remove(rb);
                    }
                    catch (Throwable t) {
                        this.log.debug("[physics] clearAll removeFromSpace failed bodyId={}", (Object)h.id, (Object)t);
                    }
                }
                if (sr == null) continue;
                try {
                    Spatial spx = sr.get(h.surfaceId);
                    if (spx == null) continue;
                    spx.removeControl((Control)rb);
                }
                catch (Throwable t) {
                    this.log.debug("[physics] clearAll removeControl failed bodyId={}", (Object)h.id, (Object)t);
                }
            }
        }
        finally {
            this.registry.clearAll();
        }
    }
}

