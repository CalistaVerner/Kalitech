// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TemporalSnapGateFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Temporal gate for texel snapping.
 * Motion is evaluated once per frame in {@link #beginFrame(ShadowFrameContext)} and per-split
 * decisions are answered via {@link #allowSnap(ShadowSplitContext, float)}.
 */
public final class TemporalSnapGateFilter implements ShadowFilter {

    private final Vector3f lastCamPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private final Quaternion lastCamRot = new Quaternion();

    private float minMoveTexels = 1.25f;
    private float minRotateDeg = 0.25f;
    private int gatedFirstCascades = 1;

    /**
     * When move exceeds this many texels, snap is allowed even if rotation is tiny.
     */
    private float teleportMoveTexels = 24.0f;

    private boolean enabled = true;

    private float lastMoveWorld = 0f;
    private float lastRotateDeg = 0f;
    private boolean hasLast = false;

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!enabled) return;

        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!hasLast || Float.isNaN(lastCamPos.x)) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            lastMoveWorld = 0f;
            lastRotateDeg = 0f;
            hasLast = true;
            return;
        }

        lastMoveWorld = lastCamPos.distance(p);

        float dot = FastMath.abs(lastCamRot.dot(r));
        dot = FastMath.clamp(dot, -1f, 1f);
        float angleRad = 2.0f * FastMath.acos(dot);
        lastRotateDeg = angleRad * FastMath.RAD_TO_DEG;

        lastCamPos.set(p);
        lastCamRot.set(r);
    }

    public boolean allowSnap(ShadowSplitContext ctx, float texelWorld) {
        if (!enabled) return true;
        if (!hasLast) return true;
        if (ctx.splitIndex >= gatedFirstCascades) return true;

        if (!(texelWorld > 0f)) return true;

        float moveTexels = lastMoveWorld / texelWorld;
        if (moveTexels >= teleportMoveTexels) return true;

        if (lastRotateDeg < minRotateDeg) {
            return moveTexels >= minMoveTexels;
        }

        return true;
    }

    public void reset() {
        hasLast = false;
        lastMoveWorld = 0f;
        lastRotateDeg = 0f;
        lastCamPos.set(Float.NaN, Float.NaN, Float.NaN);
        lastCamRot.loadIdentity();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) reset();
    }

    public void setMinMoveTexels(float minMoveTexels) {
        this.minMoveTexels = Math.max(0f, minMoveTexels);
    }

    public void setMinRotateDeg(float minRotateDeg) {
        this.minRotateDeg = Math.max(0f, minRotateDeg);
    }

    public void setGatedFirstCascades(int gatedFirstCascades) {
        this.gatedFirstCascades = Math.max(0, gatedFirstCascades);
    }

    public void setTeleportMoveTexels(float teleportMoveTexels) {
        this.teleportMoveTexels = Math.max(0f, teleportMoveTexels);
    }
}