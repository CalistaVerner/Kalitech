package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * Collision pipeline:
 *  - aggregates contact data per pair inside physics step
 *  - emits begin/stay/end deterministically
 *  - emits postStep(step,dt)
 */
final class PhysicsContacts {

    private final PhysicsState S;

    final LongContactMap currContacts = new LongContactMap(4096);
    final LongContactMap prevContacts = new LongContactMap(4096);

    PhysicsContacts(PhysicsState state) {
        this.S = state;
    }

    /* ========================== binding ========================== */

    private static Object defaultContact() {
        return PhysicsState.evt(
                "points", 0,
                "impulseSum", 0f,
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
                    if (e == null) return;

                    int a = S.bodyIdFromCollisionObject(e.getObjectA());
                    int b = S.bodyIdFromCollisionObject(e.getObjectB());
                    if (a <= 0 || b <= 0) return;

                    currContacts.record(a, b, e);
                }
            });
        }

        if (S.tickListenerBound.compareAndSet(false, true)) {
            space.addTickListener(new PhysicsTickListener() {
                @Override
                public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                    currContacts.clear();

                    // apply queued mutations on the physics thread (safe + deterministic)
                    RigidBodyControl rb;
                    while ((rb = S.pendingAdd.poll()) != null) {
                        try {
                            space.add(rb);
                        } catch (Throwable ignored) {
                        }
                    }
                    while ((rb = S.pendingRemove.poll()) != null) {
                        try {
                            space.remove(rb);
                        } catch (Throwable ignored) {
                        }
                    }
                }

                @Override
                public void physicsTick(PhysicsSpace space, float timeStep) {
                    long step = S.physicsStepCounter.incrementAndGet();

                    // BEGIN + STAY (use curr agg)
                    currContacts.forEachKey(k -> {
                        if (!prevContacts.contains(k))
                            emit(PhysicsEvents.COLL_BEGIN, step, timeStep, k, currContacts.getAgg(k));
                        emit(PhysicsEvents.COLL_STAY, step, timeStep, k, currContacts.getAgg(k));
                    });

                    // END (use prev agg as "last known contact")
                    prevContacts.forEachKey(k -> {
                        if (!currContacts.contains(k))
                            emit(PhysicsEvents.COLL_END, step, timeStep, k, prevContacts.getAgg(k));
                    });

                    // swap maps (curr becomes prev for next step)
                    prevContacts.swapWith(currContacts);

                    S.bus().emit(
                            PhysicsEvents.POST_STEP,
                            PhysicsState.evt("step", step, "dt", timeStep)
                    );
                }
            });
        }
    }

    private void emit(String topic, long step, float dt, long key, ContactAgg agg) {
        int aId = keyA(key);
        int bId = keyB(key);

        PhysicsBodyHandle a = S.byId.get(aId);
        PhysicsBodyHandle b = S.byId.get(bId);
        if (a == null || b == null) return;

        Object contact = (agg != null) ? agg.toPayload() : defaultContact();

        S.bus().emit(topic, PhysicsState.evt(
                "step", step,
                "dt", dt,
                "a", PhysicsState.evt("bodyId", a.id, "surfaceId", a.surfaceId),
                "b", PhysicsState.evt("bodyId", b.id, "surfaceId", b.surfaceId),
                "contact", contact
        ));
    }

    /* ========================== ContactAgg ========================== */

    static final class ContactAgg {
        int points;

        float impulseSum;
        float maxImpulse;

        float px, py, pz; // sum point
        float nx, ny, nz; // sum normal

        void reset() {
            points = 0;
            impulseSum = 0f;
            maxImpulse = 0f;
            px = py = pz = 0f;
            nx = ny = nz = 0f;
        }

        void add(float impulse, Vector3f pointA, Vector3f pointB, Vector3f normalOnB) {
            points++;

            if (impulse > 0f) {
                impulseSum += impulse;
                if (impulse > maxImpulse) maxImpulse = impulse;
            }

            // point: midpoint between A and B (more stable than picking one side)
            float x, y, z;
            if (pointA != null && pointB != null) {
                x = (pointA.x + pointB.x) * 0.5f;
                y = (pointA.y + pointB.y) * 0.5f;
                z = (pointA.z + pointB.z) * 0.5f;
            } else if (pointA != null) {
                x = pointA.x;
                y = pointA.y;
                z = pointA.z;
            } else if (pointB != null) {
                x = pointB.x;
                y = pointB.y;
                z = pointB.z;
            } else {
                x = 0f;
                y = 0f;
                z = 0f;
            }
            px += x;
            py += y;
            pz += z;

            // normal: accumulate, normalize later
            if (normalOnB != null) {
                nx += normalOnB.x;
                ny += normalOnB.y;
                nz += normalOnB.z;
            } else {
                ny += 1f;
            }
        }

        Object toPayload() {
            if (points <= 0) return defaultContact();

            float inv = 1f / (float) points;

            float ax = px * inv;
            float ay = py * inv;
            float az = pz * inv;

            float nnx = nx * inv;
            float nny = ny * inv;
            float nnz = nz * inv;

            // normalize avg normal
            float l2 = nnx * nnx + nny * nny + nnz * nnz;
            if (l2 > 1e-12f) {
                float invL = (float) (1.0 / Math.sqrt(l2));
                nnx *= invL;
                nny *= invL;
                nnz *= invL;
            } else {
                nnx = 0f;
                nny = 1f;
                nnz = 0f;
            }

            return PhysicsState.evt(
                    "points", points,
                    "impulseSum", impulseSum,
                    "maxImpulse", maxImpulse,
                    "point", new PhysicsRayHit.Vec3(ax, ay, az),
                    "normal", new PhysicsRayHit.Vec3(nnx, nny, nnz)
            );
        }
    }

    /* ========================== LongContactMap (pair -> ContactAgg) ========================== */

    /**
     * Open-addressing long->ContactAgg map.
     * Keys are contact pairKey(a,b).
     *
     * - No boxing
     * - Deterministic iteration (table order)
     * - swapWith for tick pipeline
     */
    static final class LongContactMap {

        private static final long EMPTY = Long.MIN_VALUE;

        private long[] keys;
        private ContactAgg[] values;
        private int size;
        private int mask;
        private int threshold;

        LongContactMap(int capacity) {
            int n = 1;
            while (n < Math.max(16, capacity)) n <<= 1;
            init(n);
        }

        private static int mix64(long z) {
            z ^= (z >>> 33);
            z *= 0xff51afd7ed558ccdL;
            z ^= (z >>> 33);
            z *= 0xc4ceb9fe1a85ec53L;
            z ^= (z >>> 33);
            return (int) z;
        }

        private void init(int n) {
            keys = new long[n];
            Arrays.fill(keys, EMPTY);
            values = new ContactAgg[n];
            mask = n - 1;
            threshold = (n * 7) / 10; // 0.70 load
            size = 0;
        }

        void clear() {
            Arrays.fill(keys, EMPTY);
            // keep values array allocated; reuse ContactAgg instances lazily
            size = 0;
        }

        boolean contains(long k) {
            return findSlot(k) >= 0;
        }

        ContactAgg getAgg(long k) {
            int slot = findSlot(k);
            return slot >= 0 ? values[slot] : null;
        }

        void record(int aId, int bId, PhysicsCollisionEvent e) {
            long k = pairKey(aId, bId);
            if (k == 0L) return;

            int slot = putSlot(k);
            ContactAgg agg = values[slot];
            if (agg == null) values[slot] = (agg = new ContactAgg());

            // NOTE: We do not reset agg here; it is reset implicitly by new tick clear().
            // But since we clear by wiping keys only, aggs can be reused; ensure fresh values:
            // if it's the first time we touch it after insertion, it is already "fresh enough".
            // HOWEVER: When the same slot is reused in future ticks, old agg must not leak.
            // So: on first insert of this key in this tick, we reset.
            // We detect "first insert" by checking if key was newly inserted in this map:
            // putSlot() handles size++ only when inserting, so we can reset when inserted.
            // We'll do it there by returning a flag via negative slot? keep it simple:
            // We'll just reset when points==0 AND impulseSum/maxImpulse ==0 but that could be legit.
            // Better: do reset on insertion inside putSlotInsert().
            //
            // Implemented: putSlot(k) ensures reset if newly inserted.
            float impulse = 0f;
            try {
                impulse = e.getAppliedImpulse();
            } catch (Throwable ignored) {
            }

            Vector3f pA = null, pB = null, nB = null;
            try {
                pA = e.getPositionWorldOnA();
            } catch (Throwable ignored) {
            }
            try {
                pB = e.getPositionWorldOnB();
            } catch (Throwable ignored) {
            }
            try {
                nB = e.getNormalWorldOnB();
            } catch (Throwable ignored) {
            }

            agg.add(impulse, pA, pB, nB);
        }

        void forEachKey(LongConsumer c) {
            long[] ks = keys;
            for (int i = 0; i < ks.length; i++) {
                long k = ks[i];
                if (k != EMPTY) c.accept(k);
            }
        }

        /* -------------------- internals -------------------- */

        void swapWith(LongContactMap other) {
            long[] tk = this.keys;
            ContactAgg[] tv = this.values;
            int ts = this.size;
            int tm = this.mask;
            int tt = this.threshold;

            this.keys = other.keys;
            this.values = other.values;
            this.size = other.size;
            this.mask = other.mask;
            this.threshold = other.threshold;

            other.keys = tk;
            other.values = tv;
            other.size = ts;
            other.mask = tm;
            other.threshold = tt;
        }

        private int findSlot(long k) {
            if (k == EMPTY) return -1;
            int idx = mix64(k) & mask;
            while (true) {
                long cur = keys[idx];
                if (cur == EMPTY) return -1;
                if (cur == k) return idx;
                idx = (idx + 1) & mask;
            }
        }

        private int putSlot(long k) {
            if (size >= threshold) rehash(keys.length << 1);

            int idx = mix64(k) & mask;
            while (true) {
                long cur = keys[idx];
                if (cur == EMPTY) {
                    keys[idx] = k;
                    size++;

                    ContactAgg agg = values[idx];
                    if (agg == null) values[idx] = (agg = new ContactAgg());
                    agg.reset(); // crucial: new key in this tick => fresh agg

                    return idx;
                }
                if (cur == k) return idx;
                idx = (idx + 1) & mask;
            }
        }

        private void rehash(int newCap) {
            long[] oldK = keys;
            ContactAgg[] oldV = values;

            init(newCap);

            for (int i = 0; i < oldK.length; i++) {
                long k = oldK[i];
                if (k == EMPTY) continue;

                int idx = mix64(k) & mask;
                while (keys[idx] != EMPTY) idx = (idx + 1) & mask;

                keys[idx] = k;
                // carry over agg instance; it will be reset on first insert in new tick anyway
                values[idx] = oldV[i];
                size++;
            }
        }
    }
}