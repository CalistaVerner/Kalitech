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
 * Shared physics module state (engine.modules).
 *
 * Rules:
 *  - PhysicsSpace mutations (add/remove) must happen ONLY on physics thread (prePhysicsTick).
 *  - Game/JS thread may enqueue pendingAdd/pendingRemove.
 */
final class PhysicsState {

    static final Logger log = LogManager.getLogger("Physics");

    /**
     * Budget for draining pending add/remove per physics tick.
     * Prevents pathological spikes when many bodies are spawned/removed at once.
     */
    static final int FLUSH_MAX_PER_TICK = 2048;

    final EngineApiImpl engine;
    final SimpleApplication app;
    final SurfaceRegistry surfaces;

    final AtomicInteger ids = new AtomicInteger(1);

    // id -> handle
    final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    // surfaceId -> bodyId
    final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();
    // RigidBodyControl -> bodyId (identity mapping)
    final ConcurrentHashMap<RigidBodyControl, Integer> idByControl = new ConcurrentHashMap<>();

    // collisionObject identity -> bodyId (depends on jME/Bullet internals)
    final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject = new ConcurrentHashMap<>();

    // shape cache (mesh + dynamic flag)
    final ConcurrentHashMap<ShapeKey, CollisionShape> shapeCache = new ConcurrentHashMap<>();

    // queued mutations (thread-safe)
    final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<RigidBodyControl> pendingRemove = new ConcurrentLinkedQueue<>();

    // listeners bound once
    final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    final AtomicBoolean tickListenerBound = new AtomicBoolean(false);

    final AtomicLong physicsStepCounter = new AtomicLong(0);

    private final Field RBC_BODY_FIELD = findRbcBodyField();
    private final Field PRB_COLLISION_OBJECT_FIELD = findPrbCollisionObjectField();

    PhysicsState(EngineApiImpl engine, SimpleApplication app, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(app, "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    static Map<String, Object> hitObj(boolean hit, int bodyId, int surfaceId,
                                      float fraction, float distance, Vector3f point, Vector3f normal) {

        PhysicsRayHit.Vec3 p = (point == null)
                ? new PhysicsRayHit.Vec3(0, 0, 0)
                : new PhysicsRayHit.Vec3(point.x, point.y, point.z);

        PhysicsRayHit.Vec3 n = (normal == null)
                ? new PhysicsRayHit.Vec3(0, 1, 0)
                : new PhysicsRayHit.Vec3(normal.x, normal.y, normal.z);

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

    PhysicsSpace requireSpace() {
        PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
        if (sp == null) throw new IllegalStateException("[physics] PhysicsSpace is not available");
        return sp;
    }

    ScriptEventBus bus() {
        ScriptEventBus b = engine.getBus();
        if (b == null) throw new IllegalStateException("[physics] ScriptEventBus is not available");
        return b;
    }

    int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;
        Integer id = bodyIdByCollisionObject.get(obj);
        return (id != null) ? id : 0;
    }

    void indexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;

        Object co = tryGetCollisionObject(h.__raw());
        if (co != null) bodyIdByCollisionObject.put(co, h.id);

        // also try underlying PhysicsRigidBody -> collisionObject (more stable for some jME versions)
        PhysicsRigidBody prb = rbcBody(h.__raw());
        if (prb != null) {
            Object co2 = tryGetCollisionObject(prb);
            if (co2 != null) bodyIdByCollisionObject.put(co2, h.id);
        }
    }

    /* -------------------- payload helpers -------------------- */

    static Map<String, Object> evt(Object... kv) {
        HashMap<String, Object> m = new HashMap<>(Math.max(8, kv.length * 2));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            if (k == null) continue;
            m.put(String.valueOf(k), kv[i + 1]);
        }
        return m;
    }

    void unindexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;

        Object co = tryGetCollisionObject(h.__raw());
        if (co != null) bodyIdByCollisionObject.remove(co, h.id);

        PhysicsRigidBody prb = rbcBody(h.__raw());
        if (prb != null) {
            Object co2 = tryGetCollisionObject(prb);
            if (co2 != null) bodyIdByCollisionObject.remove(co2, h.id);
        }
    }

    private Object tryGetCollisionObject(Object rbOrPrb) {
        if (rbOrPrb == null) return null;

        // Direct mapping cache: some versions use collision object instance directly.
        // We keep it generic and rely on PRB_COLLISION_OBJECT_FIELD if needed.
        if (rbOrPrb instanceof PhysicsRigidBody prb) {
            return tryGetCollisionObject(prb);
        }
        if (rbOrPrb instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = rbcBody(rb);
            return (prb != null) ? tryGetCollisionObject(prb) : null;
        }
        return null;
    }

    /* -------------------- reflection helpers -------------------- */

    private Object tryGetCollisionObject(PhysicsRigidBody prb) {
        if (prb == null) return null;

        // Some jME versions expose collision object indirectly; try field if present.
        if (PRB_COLLISION_OBJECT_FIELD != null) {
            try {
                Object o = PRB_COLLISION_OBJECT_FIELD.get(prb);
                if (o != null) return o;
            } catch (Throwable ignored) {
            }
        }

        // Fallback: use prb itself as key (works for many Bullet wrappers)
        return prb;
    }

    private PhysicsRigidBody rbcBody(RigidBodyControl rb) {
        if (rb == null || RBC_BODY_FIELD == null) return null;
        try {
            Object o = RBC_BODY_FIELD.get(rb);
            return (o instanceof PhysicsRigidBody prb) ? prb : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Field findPrbCollisionObjectField() {
        try {
            Field f = PhysicsRigidBody.class.getDeclaredField("collisionObject");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    record ShapeKey(Mesh mesh, boolean dynamic) {
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
}