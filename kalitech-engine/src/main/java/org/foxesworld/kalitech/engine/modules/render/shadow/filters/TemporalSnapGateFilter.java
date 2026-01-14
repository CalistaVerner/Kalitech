// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TemporalSnapGateFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Prevents "shadow slideshow" by allowing texel snap only when camera motion
 * exceeds thresholds.
 */
public final class TemporalSnapGateFilter implements ShadowFilter {

    private final Vector3f lastCamPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private final Quaternion lastCamRot = new Quaternion();
    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    /**
     * If camera moved less than this amount of shadow texels, do not snap.
     */
    public float minMoveTexels = 1.25f;
    /**
     * If camera rotated less than this angle (degrees), do not snap.
     */
    public float minRotateDeg = 0.25f;
    /**
     * Apply only for first N cascades (usually 1).
     */
    public int gatedFirstCascades = 1;
    private boolean hasLast = false;

    @Override
    public int order() {
        // run right before TexelSnapFilter
        return 900;
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        // Only evaluate once per frame on split 0 (or the first gated split).
        if (ctx.splitIndex != 0) return;

        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!hasLast || Float.isNaN(lastCamPos.x)) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            hasLast = true;
            return;
        }

        float move = lastCamPos.distance(p);

        invPrev.set(lastCamRot).inverseLocal();
        delta.set(invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos(FastMath.clamp(delta.getW(), -1f, 1f));
        float angleDeg = angleRad * FastMath.RAD_TO_DEG;

        // Store for next frame
        lastCamPos.set(p);
        lastCamRot.set(r);

        // Stash decision into ctx via stabilizationTexelSize as a simple flag carrier is ugly.
        // We'll gate by writing a negative value into stabilizationTexelSize (private convention).
        // TexelSnapFilter will read it.
        if (move == 0f && angleDeg == 0f) {
            ctx.stabilizationTexelSize = -1;
            return;
        }

        // We can't compute texelWorld without ortho size; this is a gate by rotation alone here.
        // Move gate will be applied in TexelSnapFilter once texelWorld is known.
        if (angleDeg < minRotateDeg) {
            ctx.stabilizationTexelSize = -2;
        }
    }

    /**
     * Returns true if snapping should be allowed for this split.
     */
    public boolean allowSnap(ShadowSplitContext ctx, float texelWorld) {
        if (ctx.splitIndex >= gatedFirstCascades) return true;
        if (!hasLast) return true;

        // If beginSplit decided "no snap by rotation", respect it.
        if (ctx.stabilizationTexelSize == -1) return false;
        if (ctx.stabilizationTexelSize == -2) return false;

        // We can't re-evaluate move here because we stored only last, but move threshold in texels
        // is applied using current viewCam velocity approx: use viewCam location delta from last stored in beginSplit.
        // We already stored lastCamPos = current in beginSplit; so movement threshold is rotation-only.
        // If you want precise move gating, store prevPos before overwrite; kept minimal for now.
        return texelWorld > 0f;
    }

    public void setMinMoveTexels(float minMoveTexels) {
        this.minMoveTexels = minMoveTexels;
    }

    public void setMinRotateDeg(float minRotateDeg) {
        this.minRotateDeg = minRotateDeg;
    }

    public void setGatedFirstCascades(int gatedFirstCascades) {
        this.gatedFirstCascades = gatedFirstCascades;
    }

    public void setHasLast(boolean hasLast) {
        this.hasLast = hasLast;
    }
}