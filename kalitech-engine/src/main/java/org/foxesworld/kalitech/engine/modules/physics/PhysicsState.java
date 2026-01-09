package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.util.LongHashSet;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared module state (package-private). No JS exports.
 */
final class PhysicsState {

    static final Logger log = LogManager.getLogger("Physics");

    static final int ADD_FLUSH_MAX_PER_TICK = 128;
    private static final Field RBC_BODY_FIELD = findRbcBodyField();
    final EngineApiImpl engine;
    final SimpleApplication app;
    final SurfaceRegistry surfaces;
    final AtomicInteger ids = new AtomicInteger(1);
    final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>(1024);
    final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>(1024);
    final ConcurrentHashMap<RigidBodyControl, Integer> idByControl = new ConcurrentHashMap<>(1024);
    final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject = new ConcurrentHashMap<>(1024);
    final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    final AtomicLong physicsStepCounter = new AtomicLong(0);
    final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    final PhysicsContacts.LongContactMap currContacts = new PhysicsContacts.LongContactMap(4096);
    final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>(256);
    volatile float lastDt = 0f;
    LongHashSet prevPairs = new LongHashSet(4096);
    LongHashSet currPairs = new LongHashSet(4096);

    PhysicsState(EngineApiImpl engine, SimpleApplication app, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    private static PhysicsRigidBody rbcBody(RigidBodyControl rb) {
        if (rb == null || RBC_BODY_FIELD == null) return null;
        try {
            Object o = RBC_BODY_FIELD.get(rb);
            return (o instanceof PhysicsRigidBody prb) ? prb : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findRbcBodyField() {
        try {
            Field f = RigidBodyControl.class.getDeclaredField("body");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Map<String, Object> evt(Object... kv) {
        HashMap<String, Object> m = new HashMap<>(Math.max(8, kv.length * 2));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            if (k == null) continue;
            m.put(String.valueOf(k), kv[i + 1]);
        }
        return m;
    }

    static Map<String, Object> hitObj(boolean hit, int bodyId, int surfaceId, float fraction, float distance, Vector3f point, Vector3f normal) {
        PhysicsRayHit.Vec3 p = (point == null) ? new PhysicsRayHit.Vec3(0, 0, 0) : new PhysicsRayHit.Vec3(point.x, point.y, point.z);
        PhysicsRayHit.Vec3 n;
        if (normal == null) n = new PhysicsRayHit.Vec3(0, 1, 0);
        else n = new PhysicsRayHit.Vec3(normal.x, normal.y, normal.z);

        return evt(
                "hit", hit,
                "bodyId", bodyId,
                "surfaceId", surfaceId,
                "fraction", fraction,
                "distance", distance,
                "point", p,
                "normal", n
        );
    }

    ScriptEventBus bus() {
        return engine.getBus();
    }

    PhysicsSpace requireSpace() {
        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        if (sp == null)
            throw new IllegalStateException("[physics] PhysicsSpace is not available (Bullet not attached?)");
        return sp;
    }

    int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;
        Integer id = bodyIdByCollisionObject.get(obj);
        return id == null ? 0 : id;
    }

    void indexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;
        RigidBodyControl rb = h.__raw();
        if (rb == null) return;

        // 1) Control itself
        bodyIdByCollisionObject.put(rb, h.id);

        // 2) PhysicsRigidBody (preferred): matches rayTest collision objects
        PhysicsRigidBody prb = rbcBody(rb);
        if (prb != null) bodyIdByCollisionObject.put(prb, h.id);
    }

    void unindexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;
        RigidBodyControl rb = h.__raw();
        if (rb == null) return;

        bodyIdByCollisionObject.remove(rb);
        PhysicsRigidBody prb = rbcBody(rb);
        if (prb != null) bodyIdByCollisionObject.remove(prb);
    }

    record ShapeKey(Mesh mesh, boolean dynamic) {
    }
}