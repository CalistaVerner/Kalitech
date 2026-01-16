// FILE: org/foxesworld/kalitech/engine/modules/physics/ContactAgg.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.math.Vector3f;

/**
 * Allocation-light, frame-stable contact accumulator bound to a single collision pair.
 *
 * <p>Tracks max impulse and optionally keeps the point/normal associated with that max impulse.
 * Also accumulates average point/normal when provided.</p>
 *
 * <p>This object represents the full lifecycle of one unordered collision pair.</p>
 */
public final class ContactAgg {

    private final long pairKey;

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
     * Number of samples contributing to sums.
     */
    public int points;

    /**
     * Frame-liveness marker (set by collision tracker).
     * Kept private to avoid package coupling.
     */
    private boolean frameAlive;

    public ContactAgg(long pairKey) {
        this.pairKey = pairKey;
    }

    /**
     * Returns canonical long key of the collision pair.
     */
    public long getPairKey() {
        return pairKey;
    }

    /**
     * Called exactly once when the contact first appears.
     */
    public void onBegin() {
        // Do not clear here.
        // The collision tracker may promote the per-frame accumulator to the active instance
        // to avoid allocations. In that scenario this object already contains the current frame's
        // samples and must preserve them for the begin/impact payload.
        frameAlive = true;
    }

    /**
     * Called when the contact persists across frames.
     */
    public void onStay(ContactAgg updated) {
        frameAlive = true;
        mergeFrom(updated);
    }

    /**
     * Called exactly once when the contact ends.
     */
    public void onEnd() {
        frameAlive = false;
    }

    /**
     * Marks this contact as observed in the current frame.
     * Intended for collision tracker.
     */
    public void markFrameAlive() {
        frameAlive = true;
    }

    /**
     * Clears frame marker before processing a new frame.
     * Intended for collision tracker.
     */
    public void clearFrameAlive() {
        frameAlive = false;
    }

    /**
     * Returns true if this contact was observed in the current frame.
     * Intended for collision tracker.
     */
    public boolean isFrameAlive() {
        return frameAlive;
    }

    /**
     * Backward-friendly alias: "alive" in the sense of observed this frame.
     */
    public boolean isAlive() {
        return frameAlive;
    }

    /**
     * Resets accumulated data but preserves pair identity.
     */
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
     * Merges another accumulator into this one (no lifecycle flags).
     */
    public void mergeFrom(ContactAgg updated) {
        if (updated == null) return;

        if (updated.maxImpulse > this.maxImpulse) {
            this.maxImpulse = updated.maxImpulse;
            this.maxPoint.set(updated.maxPoint);
            this.maxNormal.set(updated.maxNormal);
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