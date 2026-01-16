// FILE: org/foxesworld/kalitech/engine/modules/physics/collision/PhysicsCollisionTracker.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.util.LongContactMap;
import org.foxesworld.kalitech.engine.util.LongHashSet;

import static org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey.pairKey;
import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.isFinite;

/**
 * Tracks collision pairs across physics steps and aggregates contact data.
 *
 * <p>Correct begin/stay/end semantics:
 * <ul>
 *   <li>begin: in curr, not in prev</li>
 *   <li>stay: in curr, in prev</li>
 *   <li>end: in prev, not in curr</li>
 * </ul>
 * </p>
 */
public final class PhysicsCollisionTracker {

    private final LongHashSet prevPairs;
    private final LongHashSet currPairs;
    private final LongContactMap contacts;
    public PhysicsCollisionTracker(int pairCapacityPow2, int contactCapacityPow2) {
        this.prevPairs = new LongHashSet(Math.max(256, pairCapacityPow2));
        this.currPairs = new LongHashSet(Math.max(256, pairCapacityPow2));
        this.contacts = new LongContactMap(Math.max(256, contactCapacityPow2));
    }

    private static float safeImpulse(PhysicsCollisionEvent e) {
        try {
            float imp = e.getAppliedImpulse();
            if (isFinite(imp)) return imp;
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    private static Vector3f safePoint(PhysicsCollisionEvent e) {
        try {
            Vector3f p = e.getPositionWorldOnA();
            if (p != null) return p;
        } catch (Throwable ignored) {
        }
        try {
            Vector3f p = e.getPositionWorldOnB();
            if (p != null) return p;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Vector3f safeNormal(PhysicsCollisionEvent e) {
        // Different JME/Bullet builds expose different accessors; keep this defensive.
        try {
            Vector3f n = e.getNormalWorldOnB();
            if (n != null) return n;
        } catch (Throwable ignored) {
        }
        try {
            Vector3f n = e.getPositionWorldOnA();
            if (n != null) return n;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void clearAll() {
        prevPairs.clear();
        currPairs.clear();
        contacts.clear();
    }

    /**
     * Called from collision listener during simulation.
     */
    public void onCollision(PhysicsCollisionEvent e, Resolver resolver) {
        if (e == null) return;

        int a = resolver.bodyIdOf(e.getObjectA());
        int b = resolver.bodyIdOf(e.getObjectB());
        if (a <= 0 || b <= 0) return;

        long k = pairKey(a, b);
        if (k == 0L) return;

        currPairs.add(k);

        float imp = safeImpulse(e);
        Vector3f p = safePoint(e);
        Vector3f n = safeNormal(e);

        contacts.put(k, imp, p, n);
    }

    /**
     * Flushes current step into events and rotates buffers for next step.
     */
    public void flush(long step, float dt, Emitter emitter) {
        contacts.compact();

        // end: pairs that were in prev, but not in curr
        prevPairs.forEach((LongHashSet.LongConsumer) k -> {
            if (k == 0L) return;
            if (!currPairs.contains(k)) emitter.onEnd(step, dt, k);
        });

        // stay/begin: pairs in curr
        currPairs.forEach((LongHashSet.LongConsumer) k -> {
            if (k == 0L) return;
            ContactAgg agg = contacts.get(k);
            if (prevPairs.contains(k)) emitter.onStay(step, dt, k, agg);
            else emitter.onBegin(step, dt, k, agg);
        });

        // rotate buffers
        prevPairs.clear();
        currPairs.forEach((LongHashSet.LongConsumer) prevPairs::add);
        currPairs.clear();
        contacts.clear();
    }

    public interface Resolver {
        int bodyIdOf(Object collisionObject);
    }

    public interface Emitter {
        void onBegin(long step, float dt, long pairKey, ContactAgg agg);

        void onStay(long step, float dt, long pairKey, ContactAgg agg);

        void onEnd(long step, float dt, long pairKey);
    }
}