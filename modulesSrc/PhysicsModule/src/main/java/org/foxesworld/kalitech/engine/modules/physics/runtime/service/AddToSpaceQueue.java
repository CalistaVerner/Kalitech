/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.apache.logging.log4j.Logger;

public final class AddToSpaceQueue {
    private static final int ADD_FLUSH_MAX_PER_TICK = 128;
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
        this.init(4096);
    }

    private void init(int capacityPow2) {
        int cap;
        for (cap = 1; cap < capacityPow2; cap <<= 1) {
        }
        if (cap < 64) {
            cap = 64;
        }
        this.bodyIds = new int[cap];
        this.bodies = new RigidBodyControl[cap];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
        this.mask = cap - 1;
    }

    private void growIfNeeded() {
        if (this.size < this.bodyIds.length) {
            return;
        }
        int newCap = this.bodyIds.length << 1;
        int[] nb = new int[newCap];
        RigidBodyControl[] nr = new RigidBodyControl[newCap];
        for (int i = 0; i < this.size; ++i) {
            int idx = this.head + i & this.mask;
            nb[i] = this.bodyIds[idx];
            nr[i] = this.bodies[idx];
        }
        this.bodyIds = nb;
        this.bodies = nr;
        this.head = 0;
        this.tail = this.size;
        this.mask = newCap - 1;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void enqueue(int bodyId, RigidBodyControl rb) {
        if (rb == null) {
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            this.growIfNeeded();
            this.bodyIds[this.tail] = bodyId;
            this.bodies[this.tail] = rb;
            this.tail = this.tail + 1 & this.mask;
            ++this.size;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void remove(RigidBodyControl rb) {
        if (rb == null) {
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            if (this.size == 0) {
                return;
            }
            int write = this.head;
            int remaining = this.size;
            for (int i = 0; i < remaining; ++i) {
                int idx = this.head + i & this.mask;
                RigidBodyControl cur = this.bodies[idx];
                if (cur == rb) {
                    this.bodies[idx] = null;
                    continue;
                }
                this.bodyIds[write] = this.bodyIds[idx];
                this.bodies[write] = cur;
                write = write + 1 & this.mask;
            }
            int newSize = 0;
            for (int i = 0; i < remaining; ++i) {
                int idx = this.head + i & this.mask;
                if (this.bodies[idx] == null) continue;
                ++newSize;
            }
            this.tail = write;
            this.size = newSize;
            int cap = this.bodies.length;
            int clearN = Math.min(cap, cap);
            for (int i = 0; i < clearN; ++i) {
                int idx = this.tail + i & this.mask;
                if (i >= cap - this.size) break;
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeByBodyId(int bodyId) {
        if (bodyId <= 0) {
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            if (this.size == 0) {
                return;
            }
            int write = this.head;
            int remaining = this.size;
            for (int i = 0; i < remaining; ++i) {
                int idx = this.head + i & this.mask;
                if (this.bodyIds[idx] == bodyId) {
                    this.bodies[idx] = null;
                    continue;
                }
                this.bodyIds[write] = this.bodyIds[idx];
                this.bodies[write] = this.bodies[idx];
                write = write + 1 & this.mask;
            }
            int newSize = 0;
            for (int i = 0; i < remaining; ++i) {
                int idx = this.head + i & this.mask;
                if (this.bodies[idx] == null) continue;
                ++newSize;
            }
            this.tail = write;
            this.size = newSize;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    boolean isEmpty() {
        Object object = this.lock;
        synchronized (object) {
            return this.size == 0;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clear() {
        Object object = this.lock;
        synchronized (object) {
            for (int i = 0; i < this.size; ++i) {
                int idx = this.head + i & this.mask;
                this.bodies[idx] = null;
            }
            this.head = 0;
            this.tail = 0;
            this.size = 0;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void flushTo(PhysicsSpace space, IntConsumer onAddedBodyId) {
        if (space == null) {
            return;
        }
        for (int n = 0; n < 128; ++n) {
            RigidBodyControl rb;
            int bodyId;
            Object object = this.lock;
            synchronized (object) {
                if (this.size == 0) {
                    break;
                }
                bodyId = this.bodyIds[this.head];
                rb = this.bodies[this.head];
                this.bodies[this.head] = null;
                this.bodyIds[this.head] = 0;
                this.head = this.head + 1 & this.mask;
                --this.size;
            }
            if (rb == null) continue;
            try {
                space.add(rb);
            }
            catch (Throwable t) {
                this.log.error("[physics] addToSpace failed", t);
            }
            if (onAddedBodyId == null || bodyId <= 0) continue;
            try {
                onAddedBodyId.accept(bodyId);
                continue;
            }
            catch (Throwable t) {
                this.log.debug("[physics] addToSpace callback failed bodyId={}", (Object)bodyId, (Object)t);
            }
        }
    }
}

