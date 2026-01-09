package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.util.LongHashSet;

import java.util.Arrays;

/**
 * Collision pipeline (physics-thread only):
 *  - drains pending add/remove in prePhysicsTick with budget
 *  - aggregates contact info per body-pair per step
 *  - emits begin/stay/end and postStep
 */
public final class PhysicsContacts {

    private final PhysicsState S;

    private final LongHashSet currKeys = new LongHashSet(4096);
    private final LongHashSet prevKeys = new LongHashSet(4096);

    private final LongAggMap currAgg = new LongAggMap(4096);
    private final LongAggMap prevAgg = new LongAggMap(4096);

    public PhysicsContacts(PhysicsState state) {
        this.S = state;
    }

    static long pairKey(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return ((long) min << 32) | (max & 0xFFFFFFFFL);
    }

    static int keyA(long k) {
        return (int) (k >>> 32);
    }

    static int keyB(long k) {
        return (int) k;
    }


    // ---------------- emit ----------------

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

                    long k = pairKey(a, b);
                    if (k == 0L) return;

                    // mark presence
                    currKeys.add(k);

                    // aggregate
                    ContactAgg agg = currAgg.getOrCreateResetOnFirstTouch(k);

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
            });
        }

        if (S.tickListenerBound.compareAndSet(false, true)) {
            space.addTickListener(new PhysicsTickListener() {
                @Override
                public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                    // ---- drain pending ops with budget (avoid spikes) ----
                    drainPending(space);

                    // ---- start new step ----
                    currKeys.clear();
                    currAgg.clearKeysOnly(); // keeps agg objects for reuse
                }

                @Override
                public void physicsTick(PhysicsSpace space, float timeStep) {
                    long step = S.physicsStepCounter.incrementAndGet();

                    // BEGIN + STAY
                    currAgg.forEachKey(k -> {
                        if (!prevKeys.contains(k)) emit(PhysicsEvents.COLL_BEGIN, step, timeStep, k, currAgg.get(k));
                        emit(PhysicsEvents.COLL_STAY, step, timeStep, k, currAgg.get(k));
                    });

                    // END (use previous agg as "last known contact")
                    prevAgg.forEachKey(k -> {
                        if (!currKeys.contains(k)) emit(PhysicsEvents.COLL_END, step, timeStep, k, prevAgg.get(k));
                    });

                    // swap (no allocations)
                    swapStep();

                    S.bus().emit(PhysicsEvents.POST_STEP, PhysicsState.evt("step", step, "dt", timeStep));
                }
            });
        }
    }

    // ---------------- pair utils ----------------

    private void drainPending(PhysicsSpace space) {
        // add budget
        int addBudget = (S.FLUSH_MAX_PER_TICK > 0) ? S.FLUSH_MAX_PER_TICK : 256;
        while (addBudget-- > 0) {
            RigidBodyControl rb = S.pendingAdd.poll();
            if (rb == null) break;
            try {
                space.add(rb);
            } catch (Throwable t) {
                PhysicsState.log.error("[physics] space.add failed", t);
            }
        }

        // remove budget (usually more important to apply promptly)
        int remBudget = (S.FLUSH_MAX_PER_TICK > 0) ? S.FLUSH_MAX_PER_TICK : 256;
        while (remBudget-- > 0) {
            RigidBodyControl rb = S.pendingRemove.poll();
            if (rb == null) break;
            try {
                space.remove(rb);
            } catch (Throwable t) {
                PhysicsState.log.error("[physics] space.remove failed", t);
            }
        }
    }

    void swapStep() {
        // Ping-pong buffers without allocations:
        // - after emitting events we want current step => prev for next frame diff
        // - curr will be cleared at the beginning of the next physics tick
        currKeys.swapWith(prevKeys);
        currAgg.swapWith(prevAgg);
    }

    private void emit(String topic, long step, float dt, long key, ContactAgg agg) {
        if (agg == null || agg.points <= 0) return;

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
                "contact", agg.toPayload()
        ));
    }

    // ========================= ContactAgg =========================

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

            // midpoint(A,B) as stable representative point
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

            if (normalOnB != null) {
                nx += normalOnB.x;
                ny += normalOnB.y;
                nz += normalOnB.z;
            } else {
                ny += 1f;
            }
        }

        Object toPayload() {
            float inv = 1f / (float) points;

            float ax = px * inv;
            float ay = py * inv;
            float az = pz * inv;

            float nnx = nx * inv;
            float nny = ny * inv;
            float nnz = nz * inv;

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

    // ========================= LongAggMap =========================
    // Minimal long->ContactAgg open addressing map.
    // Uses EMPTY=0L (matches your LongHashSet sentinel).
    static final class LongAggMap {

        private static final long EMPTY = 0L;

        private long[] keys;
        private ContactAgg[] vals;
        private int mask;
        private int size;
        private int resizeAt;

        LongAggMap(int capPow2) {
            int cap = 1;
            while (cap < capPow2) cap <<= 1;
            if (cap < 16) cap = 16;

            keys = new long[cap];
            vals = new ContactAgg[cap];
            mask = cap - 1;
            resizeAt = (int) (cap * 0.65f);
            size = 0;
        }

        private static int mix64to32(long z) {
            z ^= (z >>> 33);
            z *= 0xff51afd7ed558ccdL;
            z ^= (z >>> 33);
            z *= 0xc4ceb9fe1a85ec53L;
            z ^= (z >>> 33);
            return (int) z;
        }

        void clearKeysOnly() {
            Arrays.fill(keys, EMPTY);
            size = 0;
        }

        ContactAgg get(long k) {
            if (k == EMPTY) return null;
            int i = mix64to32(k) & mask;
            while (true) {
                long cur = keys[i];
                if (cur == EMPTY) return null;
                if (cur == k) return vals[i];
                i = (i + 1) & mask;
            }
        }

        ContactAgg getOrCreateResetOnFirstTouch(long k) {
            if (k == EMPTY) return null;
            if (size >= resizeAt) rehash(keys.length << 1);

            int i = mix64to32(k) & mask;
            while (true) {
                long cur = keys[i];
                if (cur == EMPTY) {
                    keys[i] = k;
                    size++;
                    ContactAgg a = vals[i];
                    if (a == null) vals[i] = (a = new ContactAgg());
                    a.reset();
                    return a;
                }
                if (cur == k) return vals[i];
                i = (i + 1) & mask;
            }
        }

        void forEachKey(LongHashSet.LongConsumer c) {
            long[] t = keys;
            for (int i = 0; i < t.length; i++) {
                long k = t[i];
                if (k != EMPTY) c.accept(k);
            }
        }

        void swapWith(LongAggMap other) {
            long[] tk = this.keys;
            ContactAgg[] tv = this.vals;
            int tm = this.mask;
            int ts = this.size;
            int tr = this.resizeAt;

            this.keys = other.keys;
            this.vals = other.vals;
            this.mask = other.mask;
            this.size = other.size;
            this.resizeAt = other.resizeAt;

            other.keys = tk;
            other.vals = tv;
            other.mask = tm;
            other.size = ts;
            other.resizeAt = tr;
        }

        private void rehash(int newCap) {
            long[] oldK = keys;
            ContactAgg[] oldV = vals;

            keys = new long[newCap];
            vals = new ContactAgg[newCap];
            mask = newCap - 1;
            resizeAt = (int) (newCap * 0.65f);
            size = 0;

            for (int i = 0; i < oldK.length; i++) {
                long k = oldK[i];
                if (k == EMPTY) continue;

                int idx = mix64to32(k) & mask;
                while (keys[idx] != EMPTY) idx = (idx + 1) & mask;

                keys[idx] = k;
                vals[idx] = oldV[i];
                size++;
            }
        }
    }
}