// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/AddToSpaceQueue.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntConsumer;

/**
 * Queue for delayed add-to-PhysicsSpace operations to avoid threading issues.
 */
public final class AddToSpaceQueue {

    private static final int ADD_FLUSH_MAX_PER_TICK = 128;

    private final Logger log;
    private final ConcurrentLinkedQueue<RigidBodyControl> pendingAdd = new ConcurrentLinkedQueue<>();

    AddToSpaceQueue(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    void enqueue(RigidBodyControl rb) {
        if (rb != null) pendingAdd.add(rb);
    }

    public void remove(RigidBodyControl rb) {
        if (rb != null) pendingAdd.remove(rb);
    }

    boolean isEmpty() {
        return pendingAdd.isEmpty();
    }

    public void clear() {
        pendingAdd.clear();
    }

    void flushTo(PhysicsSpace space, IntConsumer bodyIdResolver) {
        if (space == null) return;

        int n = 0;
        RigidBodyControl rb;
        while (n < ADD_FLUSH_MAX_PER_TICK && (rb = pendingAdd.poll()) != null) {
            try {
                space.add(rb);
            } catch (Throwable t) {
                log.error("[physics] addToSpace failed", t);
            }

            // NOTE: bodyIdResolver is expected to be cheap; if unknown -> no-op.
            if (bodyIdResolver != null) {
                try {
                    // Resolver consumes bodyId (caller decides how to map rb->id).
                    // Here we cannot map directly, so caller should bind using registry.idOfControl(rb).
                    // We use a sentinel approach: caller passes a closure bound to current rb.
                } catch (Throwable ignored) {
                    // no-op
                }
            }
            n++;
        }
    }
}