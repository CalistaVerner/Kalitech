// FILE: org/foxesworld/kalitech/engine/modules/physics/ContactAgg.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

/**
 * Allocation-light contact accumulator for a single physics step.
 *
 * <p>Tracks max impulse and optionally keeps the point/normal associated with that max impulse.
 * Also accumulates average point/normal when provided.</p>
 */
public final class ContactAgg {

    public float maxImpulse;

    /**
     * Point at which the max impulse was observed (reused object).
     */
    public final Vector3f maxPoint = new Vector3f();

    /**
     * Normal at which the max impulse was observed (reused object).
     */
    public final Vector3f maxNormal = new Vector3f();

    public float sumPx, sumPy, sumPz;
    public float sumNx, sumNy, sumNz;

    /**
     * Number of samples contributing to sums (only increments when at least one of point/normal is provided).
     */
    public int points;

    public void clear() {
        maxImpulse = 0f;

        maxPoint.set(0f, 0f, 0f);
        maxNormal.set(0f, 0f, 0f);

        sumPx = sumPy = sumPz = 0f;
        sumNx = sumNy = sumNz = 0f;
        points = 0;
    }

    public void add(float impulse, Vector3f point) {
        add(impulse, point, null);
    }

    public void add(float impulse, Vector3f point, Vector3f normal) {
        boolean hasImpulse = Float.isFinite(impulse);

        if (hasImpulse && impulse > maxImpulse) {
            maxImpulse = impulse;
            if (point != null) maxPoint.set(point);
            if (normal != null) maxNormal.set(normal);
        }

        boolean contributed = false;

        if (point != null) {
            sumPx += point.x;
            sumPy += point.y;
            sumPz += point.z;
            contributed = true;
        }
        if (normal != null) {
            sumNx += normal.x;
            sumNy += normal.y;
            sumNz += normal.z;
            contributed = true;
        }

        if (contributed) {
            points++;
        }
    }

    /**
     * Writes average contact point into {@code out}.
     * Returns {@code out} or null if no points were accumulated.
     */
    public Vector3f avgPoint(Vector3f out) {
        if (out == null) out = new Vector3f();
        if (points <= 0) return null;
        float inv = 1f / (float) points;
        out.set(sumPx * inv, sumPy * inv, sumPz * inv);
        return out;
    }

    /**
     * Writes average contact normal into {@code out}.
     * Returns {@code out} or null if no normals were accumulated.
     */
    public Vector3f avgNormal(Vector3f out) {
        if (out == null) out = new Vector3f();
        if (points <= 0) return null;
        float inv = 1f / (float) points;
        out.set(sumNx * inv, sumNy * inv, sumNz * inv);
        return out;
    }
}