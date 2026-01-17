// FILE: org/foxesworld/kalitech/engine/modules/physics/core/PhysicsWorld.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsValueParsers;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PhysicsSpace binding and tick wiring.
 */
public final class PhysicsWorld {

    private final EngineApiImpl engine;
    private final Logger log;
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    private volatile SimpleApplication app;
    private volatile boolean dbg = false;
    private volatile int dbgEveryAddFlush = 60;
    private int dbgTicks = 0;
    public PhysicsWorld(EngineApiImpl engine, SimpleApplication app, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void attach(SimpleApplication app) {
        this.app = Objects.requireNonNull(app, "app");
    }

    public void detach() {
        this.app = null;
    }

    public void setDebug(boolean enabled, int everyAddFlush) {
        this.dbg = enabled;
        this.dbgEveryAddFlush = Math.max(1, everyAddFlush);

        SimpleApplication a = this.app;
        if (a == null) return;

        BulletAppState b = a.getStateManager().getState(BulletAppState.class);
        if (b != null) b.setDebugEnabled(enabled);
    }

    public void gravity(Object vec3) {
        PhysicsSpace space = engine.__getPhysicsSpaceOrNull();
        if (space == null) throw new IllegalStateException("[physics] PhysicsSpace not bound");
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0f, -9.81f, 0f);
        space.setGravity(g);
    }

    public PhysicsSpace requireSpace(CollisionSink collisionSink, TickSink tickSink) {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
        ensureCollisionListenerBound(s, collisionSink);
        ensureTickListenerBound(s, tickSink);
        return s;
    }

    private void ensureCollisionListenerBound(PhysicsSpace sp, CollisionSink sink) {
        if (sp == null) return;
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        sp.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent event) {
                sink.onCollision(event);
            }
        });

        log.info("[physics] collision listener bound");
    }

    private void ensureTickListenerBound(PhysicsSpace sp, TickSink sink) {
        if (sp == null) return;
        if (!tickListenerBound.compareAndSet(false, true)) return;

        sp.addTickListener(new PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                if (!dbg) return;
                dbgTicks++;
                if (dbgTicks % dbgEveryAddFlush == 0) {
                    log.debug("[physics][dbg] preTick dt={}", timeStep);
                }
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                sink.onTick(timeStep);
            }
        });

        log.info("[physics] tick listener bound");
    }

    public interface CollisionSink {
        void onCollision(PhysicsCollisionEvent e);
    }

    public interface TickSink {
        void onTick(float timeStep);
    }
}