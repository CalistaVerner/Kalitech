// FILE: org/foxesworld/kalitech/engine/modules/physics/BodyStateStore.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;

import java.util.Objects;

/**
 * Stores and updates {@link BodyState} snapshots for change detection.
 *
 * <p>Optimized for physics tick thread: no boxing, no ConcurrentHashMap.</p>
 */
public final class BodyStateStore {

    private final IntObjectMap<BodyState> map;

    public BodyStateStore(int initialCapacity) {
        this.map = new IntObjectMap<>(Math.max(16, initialCapacity));
    }

    public BodyState get(int bodyId) {
        if (bodyId <= 0) return null;
        return map.get(bodyId);
    }

    public void remove(int bodyId) {
        if (bodyId <= 0) return;
        map.remove(bodyId);
    }

    public void clear() {
        map.clear();
    }

    /**
     * Updates snapshot for the given body and returns true if state changed beyond eps thresholds.
     * If snapshot does not exist, it is created and treated as changed.
     */
    public boolean updateAndCheckChanged(int bodyId, RigidBodyControl rb, float posEps, float rotEps, float velEps) {
        Objects.requireNonNull(rb, "rb");
        if (bodyId <= 0) return false;

        BodyState st = map.get(bodyId);
        if (st == null) {
            st = BodyState.from(rb);
            map.put(bodyId, st);
            return true;
        }
        return st.updateFromAndCheckChanged(rb, posEps, rotEps, velEps);
    }
}