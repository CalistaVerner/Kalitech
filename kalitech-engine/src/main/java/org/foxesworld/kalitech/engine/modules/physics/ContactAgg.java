// FILE: org/foxesworld/kalitech/engine/modules/physics/ContactAgg.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

/**
 * Allocation-light, frame-stable contact accumulator bound to a single collision pair.
 *
 * Tracks max impulse and keeps the point/normal associated with that max impulse.
 * Also accumulates average point/normal when provided.
 */
public final class ContactAgg {

    public final Vector3f maxPoint = new Vector3f();
    public final Vector3f maxNormal = new Vector3f();

    private final long pairKey;
    public float impulseSum;
    public float energyApprox;
    public float maxRelSpeed;

    public float sumPx, sumPy, sumPz;
    public float sumNx, sumNy, sumNz;
    public int points;
    public float maxImpulse;
    private boolean frameAlive;

    public ContactAgg(long pairKey) {
        this.pairKey = pairKey;
    }

    public long getPairKey() {
        return pairKey;
    }

    public boolean isFrameAlive() {
        return frameAlive;
    }

    public void onBegin() {
        clear();
        frameAlive = true;
    }

    public void onEnd() {
        frameAlive = false;
    }

    public void markFrameAlive() {
        frameAlive = true;
    }

    public void clearFrameAlive() {
        frameAlive = false;
    }

    public void clear() {
        maxImpulse = 0f;
        impulseSum = 0f;
        energyApprox = 0f;
        maxRelSpeed = 0f;

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
        if (Float.isFinite(impulse) && impulse > 0f) {
            impulseSum += impulse;
            if (impulse > maxImpulse) {
                maxImpulse = impulse;
                if (point != null) maxPoint.set(point);
                if (normal != null) maxNormal.set(normal);
            }
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

        if (contributed) points++;
    }

    /**
     * Optional extended accumulation.
     */
    public void accumulateKinematics(float relSpeed, float reducedMass) {
        if (Float.isFinite(relSpeed) && relSpeed > maxRelSpeed) {
            maxRelSpeed = relSpeed;
        }
        if (Float.isFinite(relSpeed) && Float.isFinite(reducedMass) && reducedMass > 0f) {
            energyApprox += 0.5f * reducedMass * relSpeed * relSpeed;
        }
    }

    public void mergeFrom(ContactAgg updated) {
        if (updated == null) return;

        if (updated.maxImpulse > maxImpulse) {
            maxImpulse = updated.maxImpulse;
            maxPoint.set(updated.maxPoint);
            maxNormal.set(updated.maxNormal);
        }

        impulseSum += updated.impulseSum;
        energyApprox += updated.energyApprox;
        if (updated.maxRelSpeed > maxRelSpeed) maxRelSpeed = updated.maxRelSpeed;

        if (updated.points > 0) {
            sumPx += updated.sumPx;
            sumPy += updated.sumPy;
            sumPz += updated.sumPz;

            sumNx += updated.sumNx;
            sumNy += updated.sumNy;
            sumNz += updated.sumNz;

            points += updated.points;
        }
    }

    public Vector3f avgPoint(Vector3f out) {
        if (points <= 0) return null;
        if (out == null) out = new Vector3f();
        final float inv = 1f / (float) points;
        out.set(sumPx * inv, sumPy * inv, sumPz * inv);
        return out;
    }

    public Vector3f avgNormal(Vector3f out) {
        if (points <= 0) return null;
        if (out == null) out = new Vector3f();
        final float inv = 1f / (float) points;
        out.set(sumNx * inv, sumNy * inv, sumNz * inv);
        return out;
    }
}