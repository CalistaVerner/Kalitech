// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsModule.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.collision.PhysicsCollisionTracker;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsBodies;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsEmitter;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsWorld;
import org.foxesworld.kalitech.engine.modules.physics.query.PhysicsRaycasts;
import org.foxesworld.kalitech.engine.modules.physics.shapes.PhysicsShapes;
import org.foxesworld.kalitech.engine.modules.physics.shapes.ShapeCache;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Physics facade.
 * Public surface stays stable; heavy logic is delegated to internal subsystems.
 */
public final class PhysicsModule {

    private static final Logger log = LogManager.getLogger(PhysicsModule.class);

    private final EngineApiImpl engine;
    private final PhysicsRegistry registry = new PhysicsRegistry(log);
    private final PhysicsWorld world;

    private final AtomicLong physicsStepCounter = new AtomicLong(0);
    private final PhysicsEmitter emitter;
    private final PhysicsBodies bodies;
    private final PhysicsRaycasts raycasts;
    private final PhysicsCollisionTracker.Emitter collisionEmitter = new PhysicsCollisionTracker.Emitter() {
        @Override
        public void onBegin(long s, float d, long pairKey, ContactAgg agg) {
            emitter.emitCollision(PhysicsEmitter.TOPIC_COLL_BEGIN, s, d, pairKey, agg);
            emitter.emitImpact(s, d, pairKey, agg);
        }

        @Override
        public void onStay(long s, float d, long pairKey, ContactAgg agg) {
            emitter.emitCollision(PhysicsEmitter.TOPIC_COLL_STAY, s, d, pairKey, agg);
        }

        @Override
        public void onEnd(long s, float d, long pairKey) {
            emitter.emitCollision(PhysicsEmitter.TOPIC_COLL_END, s, d, pairKey, null);
        }
    };

    private final ShapeCache shapeCache = new ShapeCache(4096);
    private final PhysicsShapes shapes = new PhysicsShapes(shapeCache);
    private volatile SimpleApplication app;
    private volatile SurfaceRegistry surfaces;
    private volatile float lastDt = 0f;
    private volatile boolean dbg = false;
    private volatile int dbgEverySteps = 120;

    private final PhysicsCollisionTracker collisions = new PhysicsCollisionTracker(4096, 4096);
    private volatile int dbgEveryAddFlush = 60;

    public PhysicsModule(EngineApiImpl engine, SimpleApplication app, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");

        this.world = new PhysicsWorld(engine, app, log);
        this.emitter = new PhysicsEmitter(engine, registry, surfaces);
        this.bodies = new PhysicsBodies(engine, log, registry, world, emitter, surfaces, shapes);

        this.raycasts = new PhysicsRaycasts(new PhysicsRaycasts.Access() {
            @Override
            public void flushPendingAdd() {
                PhysicsModule.this.flushPendingAdd();
            }

            @Override
            public PhysicsSpace requireSpace() {
                return PhysicsModule.this.requireSpace();
            }

            @Override
            public PhysicsBodyHandle findHandleByCollisionObject(Object collisionObject) {
                return registry.findHandleByCollisionObject(collisionObject);
            }
        });
    }

    public void attach(SimpleApplication app, SurfaceRegistry surfaces) {
        this.app = Objects.requireNonNull(app, "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");

        this.world.attach(app);
        this.emitter.attach(surfaces);
        this.bodies.attach(surfaces);
    }

    public void detach() {
        try {
            clearAll();
        } catch (Throwable ignored) {
        }

        try {
            this.world.detach();
        } catch (Throwable ignored) {
        }

        this.app = null;
        this.surfaces = null;
    }

    public void setDebug(boolean enabled) {
        setDebug(enabled, this.dbgEverySteps);
    }

    public void setDebug(boolean enabled, int everySteps) {
        this.dbg = enabled;
        this.dbgEverySteps = Math.max(1, everySteps);

        this.world.setDebug(enabled, dbgEveryAddFlush);
        this.bodies.setDebug(enabled);
    }

    public int bodyOfSurface(int surfaceId) {
        return registry.bodyOfSurface(surfaceId);
    }

    public PhysicsBodyHandle handle(int bodyId) {
        return registry.get(bodyId);
    }

    public boolean exists(int bodyId) {
        return registry.exists(bodyId);
    }

    public void cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        int id = registry.bodyOfSurface(surfaceId);
        if (id > 0) remove(id);
    }

    public PhysicsSpace requireSpace() {
        // World handles the case when app is temporarily null (detached) - keep facade stable.
        return world.requireSpace(this::onCollisionEvent, this::onPhysicsTick);
    }

    public Object raycastAll(Object cfg) {
        return raycasts.raycastAll(cfg);
    }

    public PhysicsRayHit raycast(Object cfg) {
        return raycasts.raycast(cfg);
    }

    public Object raycastEx(Object cfg) {
        return raycasts.raycastEx(cfg);
    }

    public Object position(Object handleOrId) {
        return bodies.position(handleOrId);
    }

    public Object velocity(Object handleOrId) {
        return bodies.velocity(handleOrId);
    }

    public void velocity(Object handleOrId, Object vec3) {
        bodies.velocity(handleOrId, vec3);
    }

    public void yaw(Object handleOrId, double yaw) {
        bodies.yaw(handleOrId, yaw);
    }

    public void applyImpulse(Object handleOrId, Object vec3) {
        bodies.applyImpulse(handleOrId, vec3);
    }

    public void lockRotation(Object handleOrId, boolean lock) {
        bodies.lockRotation(handleOrId, lock);
    }

    public void setKinematic(Object handleOrId, boolean kinematic) {
        bodies.setKinematic(handleOrId, kinematic);
    }

    public void collisionGroups(Object handleOrId, int group, int mask) {
        bodies.collisionGroups(handleOrId, group, mask);
    }

    public void applyCentralForce(Object handleOrId, Object vec3) {
        bodies.applyCentralForce(handleOrId, vec3);
    }

    public void applyTorque(Object handleOrId, Object vec3) {
        bodies.applyTorque(handleOrId, vec3);
    }

    public Object angularVelocity(Object handleOrId) {
        return bodies.angularVelocity(handleOrId);
    }

    public void angularVelocity(Object handleOrId, Object vec3) {
        bodies.angularVelocity(handleOrId, vec3);
    }

    public void clearForces(Object handleOrId) {
        bodies.clearForces(handleOrId);
    }

    public void gravity(Object vec3) {
        bodies.gravity(vec3);
    }

    public void warp(Object handleOrId, Object vec3) {
        bodies.warp(handleOrId, vec3);
    }

    public PhysicsBodyHandle body(Object cfg) {
        requireSpace();
        return bodies.body(cfg);
    }

    public void remove(Object handleOrId) {
        bodies.remove(handleOrId);
    }

    public void clearAll() {
        bodies.clearAll();
        shapeCache.clear();
        collisions.clearAll();
    }

    private void flushPendingAdd() {
        world.flushPendingAdd(rb -> {
                    // Important: after adding to space, Bullet may create/attach underlying PhysicsRigidBody lazily.
                    // Reindex here to make collision resolution deterministic even when events return PRB instances.
                    registry.onAddedToSpace(rb);
                    emitter.emitBodyAdded(rb);
                },
                this::onCollisionEvent,
                this::onPhysicsTick);
    }

    private void onCollisionEvent(PhysicsCollisionEvent event) {
        if (event == null) return;
        try {
            collisions.onCollision(event, registry::bodyIdFromCollisionObject);
        } catch (Throwable t) {
            log.error("[physics] collision event failed", t);
        }
    }

    private void onPhysicsTick(PhysicsSpace space, float dt) {
        lastDt = dt;
        long step = physicsStepCounter.incrementAndGet();

        try {
            collisions.flush(step, dt, collisionEmitter);
        } catch (Throwable t) {
            log.error("[physics] collision flush failed", t);
        }

        try {
            bodies.emitMoveEvents(step, dt);
        } catch (Throwable t) {
            log.error("[physics] move events failed", t);
        }

        if (dbg && log.isDebugEnabled() && (step % dbgEverySteps) == 0) {
            log.debug("[physics][dbg] step={} dt={}", step, dt);
        }
    }
}