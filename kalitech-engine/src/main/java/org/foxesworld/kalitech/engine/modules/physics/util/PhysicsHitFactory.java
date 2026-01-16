// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsHitFactory.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.math.Vector3f;

import java.util.HashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.evtJs;

public final class PhysicsHitFactory {

    private PhysicsHitFactory() {
    }

    public static Map<String, Object> hitObj(
            boolean hit,
            int bodyId,
            int surfaceId,
            float fraction,
            float distance,
            Vector3f point,
            Vector3f normal
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("hit", hit);
        m.put("bodyId", bodyId);
        m.put("surfaceId", surfaceId);
        m.put("fraction", fraction);
        m.put("distance", distance);

        Vector3f p = (point != null) ? point : new Vector3f(0, 0, 0);
        Vector3f n = (normal != null) ? normal : new Vector3f(0, 1, 0);

        m.put("point", evtJs("x", p.x, "y", p.y, "z", p.z));
        m.put("normal", evtJs("x", n.x, "y", n.y, "z", n.z));

        return m;
    }
}