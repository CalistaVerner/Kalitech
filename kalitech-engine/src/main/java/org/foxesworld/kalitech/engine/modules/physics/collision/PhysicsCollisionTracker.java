// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsCollisionTracker.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.physics.collision;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.util.LongContactMap;

import java.util.Objects;

/**
 * Frame-stable collision lifecycle tracker.
 *
 * <p>Consumes Bullet collision callbacks, aggregates contacts per canonical pair key and emits
 * begin/stay/end transitions deterministically on physics ticks.</p>
 */
public final class PhysicsCollisionTracker {

    private final LongContactMap<ContactAgg> active = new LongContactMap<>();
    private final LongContactMap<ContactAgg> frameAgg = new LongContactMap<>();
    private final int maxActive;
    private final int maxFrame;

    public PhysicsCollisionTracker(int maxActive, int maxFrame) {
        this.maxActive = Math.max(128, maxActive);
        this.maxFrame = Math.max(128, maxFrame);
    }

    private static void accumulateFromEvent(PhysicsCollisionEvent event, ContactAgg agg) {
        if (agg == null) return;

        float impulse = 0f;
        try {
            impulse = event.getAppliedImpulse();
        } catch (Throwable ignored) {
        }

        Vector3f p = null;
        Vector3f n = null;

        try {
            p = event.getPositionWorldOnA();
        } catch (Throwable ignored) {
        }

        try {
            n = event.getNormalWorldOnB();
        } catch (Throwable ignored) {
        }

        // No allocations: ContactAgg copies data into its reusable vectors and sums scalars.
        // It's safe to pass event vectors directly.
        agg.add(impulse, p, n);
    }

    /**
     * Clears all collision state.
     */
    public void clearAll() {
        active.clear();
        frameAgg.clear();
    }

    /**
     * Called from Bullet collision listener for every collision callback.
     * Aggregates contact data into per-frame accumulator.
     */
    public void onCollision(PhysicsCollisionEvent event, BodyIdResolver resolver) {
        if (event == null || resolver == null) return;

        final Object objA;
        final Object objB;

        try {
            objA = event.getObjectA();
            objB = event.getObjectB();
        } catch (Throwable ignored) {
            return;
        }

        if (objA == null || objB == null) return;

        final int idA;
        final int idB;

        try {
            idA = resolver.resolve(objA);
            idB = resolver.resolve(objB);
        } catch (Throwable ignored) {
            return;
        }

        if (idA <= 0 || idB <= 0 || idA == idB) return;

        final long pairKey = CollisionPairKey.pack(idA, idB);

        ContactAgg agg = frameAgg.get(pairKey);
        if (agg == null) {
            if (frameAgg.size() >= maxFrame) return;
            agg = new ContactAgg(pairKey);
            frameAgg.put(pairKey, agg);
        }

        accumulateFromEvent(event, agg);
    }

    /**
     * Flushes per-frame aggregation, emits begin/stay/end transitions.
     * Must be called exactly once per physics tick.
     */
    public void flush(long step, float dt, Emitter emitter) {
        Objects.requireNonNull(emitter, "emitter");

        // 1) Clear frame marker on all active contacts
        for (LongContactMap.Entry<ContactAgg> e : active.entries()) {
            ContactAgg c = e.value();
            if (c != null) c.clearFrameAlive();
        }

        // 2) Process all per-frame contacts: begin or stay
        for (LongContactMap.Entry<ContactAgg> e : frameAgg.entries()) {
            long key = e.key();
            ContactAgg frame = e.value();
            if (frame == null) continue;

            ContactAgg cur = active.get(key);
            if (cur == null) {
                if (active.size() >= maxActive) {
                    // Capacity exceeded: skip promoting new active contacts (deterministic behavior)
                    continue;
                }

                // Promote the frame accumulator as the active contact instance
                cur = frame;
                active.put(key, cur);

                cur.onBegin();
                emitter.onBegin(step, dt, key, cur);
            } else {
                // Merge frame data into active contact (no lifecycle state leaks)
                cur.mergeFrom(frame);
                cur.markFrameAlive();
                emitter.onStay(step, dt, key, cur);
            }

            // Ensure liveness is marked even for begin path
            cur.markFrameAlive();
        }

        // 3) Emit end for contacts not seen this frame
        active.sweep(c -> {
            if (c == null) return false;

            if (!c.isFrameAlive()) {
                long key = c.getPairKey();
                c.onEnd();
                emitter.onEnd(step, dt, key);
                return false;
            }
            return true;
        });

        // 4) Clear per-frame aggregation
        frameAgg.clear();
    }

    /**
     * Resolves engine bodyId by Bullet collision object.
     */
    @FunctionalInterface
    public interface BodyIdResolver {
        int resolve(Object collisionObject);
    }

    /**
     * Emission target for collision lifecycle.
     */
    public interface Emitter {
        void onBegin(long step, float dt, long pairKey, ContactAgg agg);

        void onStay(long step, float dt, long pairKey, ContactAgg agg);

        void onEnd(long step, float dt, long pairKey);
    }
}