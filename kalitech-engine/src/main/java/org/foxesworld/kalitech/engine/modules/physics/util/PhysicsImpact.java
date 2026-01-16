// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsImpact.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.graalvm.polyglot.proxy.ProxyObject;

public final class PhysicsImpact {

    private PhysicsImpact() {
    }

    public static boolean hasCollision(RigidBodyControl rb) {
        if (rb == null) return false;
        try {
            return rb.getCollisionShape() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isHardSurface(RigidBodyControl rb, Spatial sp) {
        if (!hasCollision(rb)) return false;

        if (sp != null) {
            try {
                Boolean hard = sp.getUserData("hardSurface");
                if (hard != null) return hard.booleanValue();
            } catch (Throwable ignored) {
            }
        }

        try {
            if (rb.isKinematic()) return true;
        } catch (Throwable ignored) {
        }

        try {
            float m = rb.getMass();
            if (Float.isFinite(m) && m <= 0f) return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static float relativeSpeedApprox(RigidBodyControl a, RigidBodyControl b) {
        if (a == null || b == null) return 0f;
        try {
            Vector3f va = a.getLinearVelocity();
            Vector3f vb = b.getLinearVelocity();
            if (va == null || vb == null) return 0f;

            float dx = va.x - vb.x;
            float dy = va.y - vb.y;
            float dz = va.z - vb.z;
            float s2 = dx * dx + dy * dy + dz * dz;
            return (s2 > 0f) ? (float) Math.sqrt(s2) : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    public static float reducedMassSafe(float ma, float mb) {
        if (!(Float.isFinite(ma) && Float.isFinite(mb))) return 0f;
        if (ma <= 0f || mb <= 0f) return 0f;
        float sum = ma + mb;
        if (!(sum > 1e-6f)) return 0f;
        return (ma * mb) / sum;
    }

    public static float safeImpulseApprox(ProxyObject contact) {
        try {
            Object mi = contact.getMember("maxImpulse");
            if (mi instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return 0f;
    }
}