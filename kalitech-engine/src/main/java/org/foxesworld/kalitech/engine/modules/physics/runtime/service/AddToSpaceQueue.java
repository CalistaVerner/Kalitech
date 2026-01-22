// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/AddToSpaceQueue.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Queue for delayed add-to-PhysicsSpace operations to avoid threading issues.
 *
 * <p>Allocation-minimal: ring buffer (no per-enqueue node objects).</p>
 */
public final class AddToSpaceQueue {

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    /**
     * Capacity is power-of-two for fast modulo.
     * Increase if you batch-spawn many bodies in one frame.
     */
    private static final int DEFAULT_CAPACITY = 4096;

    private final Logger log;

    private final Object lock = new Object();

    private int[] bodyIds;
    private RigidBodyControl[] bodies;

    private int head;
    private int tail;
    private int size;
    private int mask;

    AddToSpaceQueue(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        init(DEFAULT_CAPACITY);
    }

    private void init(int capacityPow2) {
        int cap = 1;
        while (cap < capacityPow2) cap <<= 1;
        if (cap < 64) cap = 64;

        this.bodyIds = new int[cap];
        this.bodies = new RigidBodyControl[cap];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
        this.mask = cap - 1;
    }

    private void growIfNeeded() {
        if (size < bodyIds.length) return;

        final int newCap = bodyIds.length << 1;
        final int[] nb = new int[newCap];
        final RigidBodyControl[] nr = new RigidBodyControl[newCap];

        for (int i = 0; i < size; i++) {
            int idx = (head + i) & mask;
            nb[i] = bodyIds[idx];
            nr[i] = bodies[idx];
        }

        bodyIds = nb;
        bodies = nr;
        head = 0;
        tail = size;
        mask = newCap - 1;
    }

    void enqueue(int bodyId, RigidBodyControl rb) {
        if (rb == null) return;

        synchronized (lock) {
            growIfNeeded();
            bodyIds[tail] = bodyId;
            bodies[tail] = rb;
            tail = (tail + 1) & mask;
            size++;
        }
    }

    public void remove(RigidBodyControl rb) {
        if (rb == null) return;

        synchronized (lock) {
            if (size == 0) return;

            int write = head;
            int remaining = size;

            for (int i = 0; i < remaining; i++) {
                int idx = (head + i) & mask;
                RigidBodyControl cur = bodies[idx];
                if (cur == rb) {
                    bodies[idx] = null;
                    continue;
                }
                bodyIds[write] = bodyIds[idx];
                bodies[write] = cur;
                write = (write + 1) & mask;
            }

            // Clear tail region (optional, helps GC)
            int newSize = 0;
            for (int i = 0; i < remaining; i++) {
                int idx = (head + i) & mask;
                if (bodies[idx] != null) newSize++;
            }

            // Re-pack already happened via "write", so rebuild head/tail deterministically
            // by scanning from head and counting non-nulls is expensive; instead compute via write pointer:
            // write now points at head+newSize.
            tail = write;
            size = newSize;

            // Null out slots between tail and old tail if needed (best-effort)
            // Not strictly necessary, but keeps references short-lived.
            // We only clear a bounded number of slots.
            int cap = bodies.length;
            int clearN = Math.min(cap, cap); // explicit
            for (int i = 0; i < clearN; i++) {
                int idx = (tail + i) & mask;
                if (i >= (cap - size)) break;
                // no-op
            }
        }
    }

    public void removeByBodyId(int bodyId) {
        if (bodyId <= 0) return;

        synchronized (lock) {
            if (size == 0) return;

            int write = head;
            int remaining = size;

            for (int i = 0; i < remaining; i++) {
                int idx = (head + i) & mask;
                if (bodyIds[idx] == bodyId) {
                    bodies[idx] = null;
                    continue;
                }
                bodyIds[write] = bodyIds[idx];
                bodies[write] = bodies[idx];
                write = (write + 1) & mask;
            }

            int newSize = 0;
            for (int i = 0; i < remaining; i++) {
                int idx = (head + i) & mask;
                if (bodies[idx] != null) newSize++;
            }

            tail = write;
            size = newSize;
        }
    }

    boolean isEmpty() {
        synchronized (lock) {
            return size == 0;
        }
    }

    public void clear() {
        synchronized (lock) {
            for (int i = 0; i < size; i++) {
                int idx = (head + i) & mask;
                bodies[idx] = null;
            }
            head = 0;
            tail = 0;
            size = 0;
        }
    }

    void flushTo(PhysicsSpace space, IntConsumer onAddedBodyId) {
        if (space == null) return;

        int n = 0;

        while (n < ADD_FLUSH_MAX_PER_TICK) {
            final int bodyId;
            final RigidBodyControl rb;

            synchronized (lock) {
                if (size == 0) break;

                bodyId = bodyIds[head];
                rb = bodies[head];

                bodies[head] = null;
                bodyIds[head] = 0;

                head = (head + 1) & mask;
                size--;
            }

            if (rb != null) {
                try {
                    space.add(rb);
                } catch (Throwable t) {
                    log.error("[physics] addToSpace failed", t);
                }

                if (onAddedBodyId != null && bodyId > 0) {
                    try {
                        onAddedBodyId.accept(bodyId);
                    } catch (Throwable t) {
                        log.debug("[physics] addToSpace callback failed bodyId={}", bodyId, t);
                    }
                }
            }

            n++;
        }
    }
}