// FILE: org/foxesworld/kalitech/engine/modules/physics/BodyStateStore.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores and updates {@link BodyState} snapshots for change detection.
 * Thread-safe for typical engine usage (physics tick thread).
 */
public final class BodyStateStore {

    private final ConcurrentHashMap<Integer, BodyState> map;

    public BodyStateStore(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(Math.max(16, initialCapacity));
    }

    public BodyState get(int bodyId) {
        return map.get(bodyId);
    }

    public void remove(int bodyId) {
        map.remove(bodyId);
    }

    public void clear() {
        map.clear();
    }

    /**
     * Updates snapshot for the given body and returns true if state changed beyond eps thresholds.
     * If snapshot does not exist, it is created and treated as changed (first init).
     */
    public boolean updateAndCheckChanged(
            int bodyId,
            RigidBodyControl rb,
            float posEps,
            float rotEps,
            float velEps
    ) {
        Objects.requireNonNull(rb, "rb");

        BodyState st = map.get(bodyId);
        if (st == null) {
            st = BodyState.from(rb);
            map.put(bodyId, st);
            return true;
        }
        return st.updateFromAndCheckChanged(rb, posEps, rotEps, velEps);
    }
}