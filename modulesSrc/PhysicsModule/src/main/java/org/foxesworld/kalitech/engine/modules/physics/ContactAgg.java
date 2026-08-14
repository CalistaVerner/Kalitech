/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

public final class ContactAgg {
    public final Vector3f maxPoint = new Vector3f();
    public final Vector3f maxNormal = new Vector3f();
    private final long pairKey;
    public float impulseSum;
    public float energyApprox;
    public float maxRelSpeed;
    public float sumPx;
    public float sumPy;
    public float sumPz;
    public float sumNx;
    public float sumNy;
    public float sumNz;
    public int points;
    public float maxImpulse;
    private boolean frameAlive;

    public ContactAgg(long pairKey) {
        this.pairKey = pairKey;
    }

    public long getPairKey() {
        return this.pairKey;
    }

    public boolean isFrameAlive() {
        return this.frameAlive;
    }

    public void onBegin() {
        this.clear();
        this.frameAlive = true;
    }

    public void onEnd() {
        this.frameAlive = false;
    }

    public void markFrameAlive() {
        this.frameAlive = true;
    }

    public void clearFrameAlive() {
        this.frameAlive = false;
    }

    public void clear() {
        this.maxImpulse = 0.0f;
        this.impulseSum = 0.0f;
        this.energyApprox = 0.0f;
        this.maxRelSpeed = 0.0f;
        this.maxPoint.set(0.0f, 0.0f, 0.0f);
        this.maxNormal.set(0.0f, 0.0f, 0.0f);
        this.sumPz = 0.0f;
        this.sumPy = 0.0f;
        this.sumPx = 0.0f;
        this.sumNz = 0.0f;
        this.sumNy = 0.0f;
        this.sumNx = 0.0f;
        this.points = 0;
    }

    public void add(float impulse, Vector3f point) {
        this.add(impulse, point, null);
    }

    public void add(float impulse, Vector3f point, Vector3f normal) {
        if (Float.isFinite(impulse) && impulse > 0.0f) {
            this.impulseSum += impulse;
            if (impulse > this.maxImpulse) {
                this.maxImpulse = impulse;
                if (point != null) {
                    this.maxPoint.set(point);
                }
                if (normal != null) {
                    this.maxNormal.set(normal);
                }
            }
        }
        boolean contributed = false;
        if (point != null) {
            this.sumPx += point.x;
            this.sumPy += point.y;
            this.sumPz += point.z;
            contributed = true;
        }
        if (normal != null) {
            this.sumNx += normal.x;
            this.sumNy += normal.y;
            this.sumNz += normal.z;
            contributed = true;
        }
        if (contributed) {
            ++this.points;
        }
    }

    public void accumulateKinematics(float relSpeed, float reducedMass) {
        if (Float.isFinite(relSpeed) && relSpeed > this.maxRelSpeed) {
            this.maxRelSpeed = relSpeed;
        }
        if (Float.isFinite(relSpeed) && Float.isFinite(reducedMass) && reducedMass > 0.0f) {
            this.energyApprox += 0.5f * reducedMass * relSpeed * relSpeed;
        }
    }

    public void mergeFrom(ContactAgg updated) {
        if (updated == null) {
            return;
        }
        if (updated.maxImpulse > this.maxImpulse) {
            this.maxImpulse = updated.maxImpulse;
            this.maxPoint.set(updated.maxPoint);
            this.maxNormal.set(updated.maxNormal);
        }
        this.impulseSum += updated.impulseSum;
        this.energyApprox += updated.energyApprox;
        if (updated.maxRelSpeed > this.maxRelSpeed) {
            this.maxRelSpeed = updated.maxRelSpeed;
        }
        if (updated.points > 0) {
            this.sumPx += updated.sumPx;
            this.sumPy += updated.sumPy;
            this.sumPz += updated.sumPz;
            this.sumNx += updated.sumNx;
            this.sumNy += updated.sumNy;
            this.sumNz += updated.sumNz;
            this.points += updated.points;
        }
    }

    public Vector3f avgPoint(Vector3f out) {
        if (this.points <= 0) {
            return null;
        }
        if (out == null) {
            out = new Vector3f();
        }
        float inv = 1.0f / (float)this.points;
        out.set(this.sumPx * inv, this.sumPy * inv, this.sumPz * inv);
        return out;
    }

    public Vector3f avgNormal(Vector3f out) {
        if (this.points <= 0) {
            return null;
        }
        if (out == null) {
            out = new Vector3f();
        }
        float inv = 1.0f / (float)this.points;
        out.set(this.sumNx * inv, this.sumNy * inv, this.sumNz * inv);
        return out;
    }
}

