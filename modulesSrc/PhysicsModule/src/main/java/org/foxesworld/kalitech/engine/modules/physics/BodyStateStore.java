/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.control.RigidBodyControl;
import java.util.Objects;
import org.foxesworld.kalitech.engine.modules.physics.BodyState;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;

public final class BodyStateStore {
    private final IntObjectMap<BodyState> map;

    public BodyStateStore(int initialCapacity) {
        this.map = new IntObjectMap(Math.max(16, initialCapacity));
    }

    public BodyState get(int bodyId) {
        if (bodyId <= 0) {
            return null;
        }
        return this.map.get(bodyId);
    }

    public void remove(int bodyId) {
        if (bodyId <= 0) {
            return;
        }
        this.map.remove(bodyId);
    }

    public void clear() {
        this.map.clear();
    }

    public int updateAndGetDelta(int bodyId, RigidBodyControl rb, float posEps, float rotEps, float velEps) {
        Objects.requireNonNull(rb, "rb");
        if (bodyId <= 0) {
            return 0;
        }
        BodyState st = this.map.get(bodyId);
        if (st == null) {
            st = new BodyState();
            this.map.put(bodyId, st);
        }
        return st.updateFromAndGetDelta(rb, posEps, rotEps, velEps);
    }
}

