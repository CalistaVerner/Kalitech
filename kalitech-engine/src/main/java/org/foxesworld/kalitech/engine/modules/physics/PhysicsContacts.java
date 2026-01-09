package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * Collision pipeline:
 * - collects contact pairs during physics step
 * - emits begin / stay / end
 * - aggregates minimal contact info (placeholder-ready)
 * <p>
 * NO API EXPORTS. Internal module service.
 */
final class PhysicsContacts {

    final LongContactMap currContacts = new LongContactMap(4096);
    final LongContactMap prevContacts = new LongContactMap(4096);
    private final PhysicsState S;

    PhysicsContacts(PhysicsState state) {
        this.S = state;
    }

    /* ========================== binding ========================== */

    private static Object defaultContact() {
        return PhysicsState.evt(
                "points", 0,
                "maxImpulse", 0f,
                "point", new PhysicsRayHit.Vec3(0, 0, 0),
                "normal", new PhysicsRayHit.Vec3(0, 1, 0)
        );
    }

    /* ========================== emit ========================== */

    static long pairKey(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return ((long) min << 32) | (max & 0xFFFFFFFFL);
    }

    static int keyA(long k) {
        return (int) (k >>> 32);
    }

    /* ========================== pair utils ========================== */

    static int keyB(long k) {
        return (int) k;
    }

    void ensureBound(PhysicsSpace space) {
        if (space == null) return;

        if (S.collisionListenerBound.compareAndSet(false, true)) {
            space.addCollisionListener(new PhysicsCollisionListener() {
                @Override
                public void collision(PhysicsCollisionEvent e) {
                    int a = S.bodyIdFromCollisionObject(e.getObjectA());
                    int b = S.bodyIdFromCollisionObject(e.getObjectB());
                    if (a <= 0 || b <= 0) return;

                    long key = pairKey(a, b);
                    currContacts.add(key);
                }
            });
        }

        if (S.tickListenerBound.compareAndSet(false, true)) {
            space.addTickListener(new PhysicsTickListener() {
                @Override
                public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                    currContacts.clear();
                }

                @Override
                public void physicsTick(PhysicsSpace space, float timeStep) {
                    long step = S.physicsStepCounter.incrementAndGet();

                    // BEGIN + STAY
                    currContacts.forEach(k -> {
                        if (!prevContacts.contains(k)) emit(PhysicsEvents.COLL_BEGIN, step, timeStep, k);
                        emit(PhysicsEvents.COLL_STAY, step, timeStep, k);
                    });

                    // END
                    prevContacts.forEach(k -> {
                        if (!currContacts.contains(k)) emit(PhysicsEvents.COLL_END, step, timeStep, k);
                    });

                    // swap
                    prevContacts.swapWith(currContacts);

                    S.bus().emit(
                            PhysicsEvents.POST_STEP,
                            PhysicsState.evt("step", step, "dt", timeStep)
                    );
                }
            });
        }
    }

    private void emit(String topic, long step, float dt, long key) {
        int aId = keyA(key);
        int bId = keyB(key);

        PhysicsBodyHandle a = S.byId.get(aId);
        PhysicsBodyHandle b = S.byId.get(bId);
        if (a == null || b == null) return;

        S.bus().emit(topic, PhysicsState.evt(
                "step", step,
                "dt", dt,
                "a", PhysicsState.evt("bodyId", a.id, "surfaceId", a.surfaceId),
                "b", PhysicsState.evt("bodyId", b.id, "surfaceId", b.surfaceId),
                "contact", defaultContact()
        ));
    }

    /* ========================== LongContactMap ========================== */

    /**
     * Fast primitive long set with swap support.
     * No boxing. Deterministic iteration.
     */
    static final class LongContactMap {

        private long[] data;
        private int size;

        LongContactMap(int capacity) {
            data = new long[Math.max(16, capacity)];
        }

        boolean contains(long v) {
            for (int i = 0; i < size; i++) {
                if (data[i] == v) return true;
            }
            return false;
        }

        void add(long v) {
            if (contains(v)) return;
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        void clear() {
            size = 0;
        }

        void forEach(LongConsumer c) {
            for (int i = 0; i < size; i++) {
                c.accept(data[i]);
            }
        }

        void swapWith(LongContactMap other) {
            long[] td = this.data;
            int ts = this.size;

            this.data = other.data;
            this.size = other.size;

            other.data = td;
            other.size = ts;
        }
    }
}