// FILE: org/foxesworld/kalitech/engine/modules/physics/internal/PhysicsWorld.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PhysicsSpace lifecycle, listeners binding, and pending add queue flush.
 */
public final class PhysicsWorld {

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;
    private final EngineApiImpl engine;
    private final Logger log;
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean addFlushScheduled = new AtomicBoolean(false);
    private volatile SimpleApplication app;
    private volatile boolean dbg = false;
    private volatile int dbgEveryAddFlush = 60;
    private int dbgPendingAddFlushed = 0;
    private int dbgPendingAddFailed = 0;
    public PhysicsWorld(EngineApiImpl engine, SimpleApplication app, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void attach(SimpleApplication app) {
        this.app = app;
    }

    public void detach() {
        this.app = null;
        pendingAdd.clear();
        collisionListenerBound.set(false);
        tickListenerBound.set(false);
        addFlushScheduled.set(false);
        dbgPendingAddFailed = 0;
        dbgPendingAddFlushed = 0;
    }

    public void setDebug(boolean dbg, int dbgEveryAddFlush) {
        this.dbg = dbg;
        this.dbgEveryAddFlush = Math.max(1, dbgEveryAddFlush);
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

    public void enqueueAdd(RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.add(rb);
        scheduleAddFlush();
    }

    public void flushPendingAdd(BodyAddedSink addedSink, CollisionSink collisionSink, TickSink tickSink) {
        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        if (sp == null) return;

        ensureCollisionListenerBound(sp, collisionSink);
        ensureTickListenerBound(sp, tickSink);

        int n = 0;
        RigidBodyControl rb;
        while (n < ADD_FLUSH_MAX_PER_TICK && (rb = pendingAdd.poll()) != null) {
            try {
                sp.add(rb);
                dbgPendingAddFlushed++;
                if (addedSink != null) addedSink.onBodyAdded(rb);
            } catch (Throwable t) {
                dbgPendingAddFailed++;
                log.error("[physics] addToSpace failed", t);
            }
            n++;
        }

        if (dbg && log.isDebugEnabled() && (dbgPendingAddFlushed % dbgEveryAddFlush) == 0) {
            log.debug("[physics][dbg] pendingAdd flushed={} failed={} remaining={}",
                    dbgPendingAddFlushed, dbgPendingAddFailed, pendingAdd.size());
        }
    }

    private void scheduleAddFlush() {
        if (!addFlushScheduled.compareAndSet(false, true)) return;

        SimpleApplication a = this.app;
        if (a == null) {
            addFlushScheduled.set(false);
            return;
        }

        a.enqueue(() -> {
            try {
                // actual flush happens from PhysicsModule via flushPendingAdd(...)
            } finally {
                addFlushScheduled.set(false);
            }
            return null;
        });
    }

    private void ensureCollisionListenerBound(PhysicsSpace s, CollisionSink sink) {
        if (!collisionListenerBound.compareAndSet(false, true)) return;

        s.addCollisionListener(new PhysicsCollisionListener() {
            @Override
            public void collision(PhysicsCollisionEvent event) {
                if (event == null || sink == null) return;
                sink.onCollision(event);
            }
        });
    }

    private void ensureTickListenerBound(PhysicsSpace s, TickSink sink) {
        if (!tickListenerBound.compareAndSet(false, true)) return;

        s.addTickListener(new PhysicsTickListener() {
            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                if (sink != null) sink.onPhysicsTick(space, timeStep);
            }
        });
    }

    public interface CollisionSink {
        void onCollision(PhysicsCollisionEvent event);
    }

    public interface TickSink {
        void onPhysicsTick(PhysicsSpace space, float dt);
    }

    public interface BodyAddedSink {
        void onBodyAdded(RigidBodyControl rb);
    }
}