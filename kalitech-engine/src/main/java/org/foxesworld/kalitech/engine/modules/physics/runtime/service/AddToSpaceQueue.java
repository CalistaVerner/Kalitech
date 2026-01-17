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
    private final ConcurrentLinkedQueue<PendingAdd> pendingAdd = new ConcurrentLinkedQueue<>();

    AddToSpaceQueue(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    void enqueue(int bodyId, RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.add(new PendingAdd(bodyId, rb));
    }

    public void remove(RigidBodyControl rb) {
        if (rb == null) return;
        pendingAdd.removeIf(p -> p.rb == rb);
    }

    public void removeByBodyId(int bodyId) {
        if (bodyId <= 0) return;
        pendingAdd.removeIf(p -> p.bodyId == bodyId);
    }

    boolean isEmpty() {
        return pendingAdd.isEmpty();
    }

    public void clear() {
        pendingAdd.clear();
    }

    void flushTo(PhysicsSpace space, IntConsumer onAddedBodyId) {
        if (space == null) return;

        int n = 0;
        PendingAdd p;
        while (n < ADD_FLUSH_MAX_PER_TICK && (p = pendingAdd.poll()) != null) {
            try {
                space.add(p.rb);
            } catch (Throwable t) {
                log.error("[physics] addToSpace failed", t);
            }

            if (onAddedBodyId != null) {
                try {
                    onAddedBodyId.accept(p.bodyId);
                } catch (Throwable t) {
                    log.debug("[physics] addToSpace callback failed bodyId={}", p.bodyId, t);
                }
            }
            n++;
        }
    }

    private static final class PendingAdd {
        private final int bodyId;
        private final RigidBodyControl rb;

        private PendingAdd(int bodyId, RigidBodyControl rb) {
            this.bodyId = bodyId;
            this.rb = rb;
        }
    }
}