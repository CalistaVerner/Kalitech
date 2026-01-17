// FILE: org/foxesworld/kalitech/engine/modules/physics/ContactAgg.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

/**
 * Allocation-light contact accumulator (one physics step).
 */
public final class ContactAgg {
    public float maxImpulse;
    public float sumPx, sumPy, sumPz;
    public float sumNx, sumNy, sumNz;
    public int points;

    public void clear() {
        maxImpulse = 0f;
        sumPx = sumPy = sumPz = 0f;
        sumNx = sumNy = sumNz = 0f;
        points = 0;
    }

    public void add(float impulse, Vector3f point, Vector3f normal) {
        if (Float.isFinite(impulse) && impulse > maxImpulse) maxImpulse = impulse;

        if (point != null) {
            sumPx += point.x;
            sumPy += point.y;
            sumPz += point.z;
        }
        if (normal != null) {
            sumNx += normal.x;
            sumNy += normal.y;
            sumNz += normal.z;
        }
        points++;
    }
}