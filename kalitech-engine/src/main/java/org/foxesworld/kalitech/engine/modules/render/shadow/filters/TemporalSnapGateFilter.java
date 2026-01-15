// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TemporalSnapGateFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Temporal gate to reduce shimmer: only allow texel snap when camera moved/rotated enough.
 * Writes decision into workspace (split-scope): {@link ShadowKeys#ALLOW_TEXEL_SNAP}.
 */
public final class TemporalSnapGateFilter implements ShadowFilter {

    public boolean enabled = true;

    private final Vector3f lastCamPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    /**
     * If camera rotated less than this degrees, keep last snap for near cascades.
     */
    public float minRotateDeg = 0.25f;
    /**
     * If camera moved less than this amount of shadow texels, keep last snap.
     */
    public float minMoveTexels = 1.25f;
    /**
     * If camera moved more than this amount of shadow texels, treat as teleport and force resnap.
     */
    public float teleportMoveTexels = 24.0f;
    /**
     * Apply gate only for first N cascades.
     */
    public int gatedFirstCascades = 1;
    private final Quaternion lastCamRot = new Quaternion();

    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    private boolean hasLast = false;

    // Cached per-frame metrics (computed once on split 0)
    private float lastMoveWorld = 0f;
    private float lastAngleDeg = 0f;
    private long lastFrameId = -1L;

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        if (!enabled) return;
        if (ctx.splitIndex != 0) return;

        long fid = ctx.frame.frameId;
        if (fid == lastFrameId) return;
        lastFrameId = fid;

        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!hasLast || Float.isNaN(lastCamPos.x)) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            lastMoveWorld = 0f;
            lastAngleDeg = 0f;
            hasLast = true;
            return;
        }

        // Movement
        lastMoveWorld = lastCamPos.distance(p);

        // Rotation
        invPrev.set(lastCamRot).inverseLocal();
        delta.set(invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos(FastMath.clamp(delta.getW(), -1f, 1f));
        lastAngleDeg = angleRad * FastMath.RAD_TO_DEG;

        // Store current as "last" for next frame
        lastCamPos.set(p);
        lastCamRot.set(r);

        ctx.frame.ws.put(ShadowKeys.VIEW_CAM_MOVE_WORLD, lastMoveWorld);
        ctx.frame.ws.put(ShadowKeys.VIEW_CAM_ROTATE_DEG, lastAngleDeg);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled) {
            ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, Boolean.TRUE);
            return;
        }

        float texelWorld = ctx.ws.getOrDefault(ShadowKeys.TEXEL_WORLD, 0f);
        boolean allow = allowResnap(ctx, texelWorld);
        ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, allow);
    }

    /**
     * Returns true if snapping should be allowed for this split.
     */
    public boolean allowResnap(ShadowSplitContext ctx, float texelWorld) {
        if (!enabled) return true;
        if (ctx.splitIndex >= gatedFirstCascades) return true;
        if (!hasLast) return true;

        if (!(texelWorld > 0f)) {
            // If texelWorld is unknown, allow resnap (better than drift).
            return true;
        }

        float moveTexels = lastMoveWorld / texelWorld;

        if (moveTexels >= teleportMoveTexels) return true;
        if (moveTexels >= minMoveTexels) return true;
        return lastAngleDeg >= minRotateDeg;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMinRotateDeg(float minRotateDeg) {
        this.minRotateDeg = minRotateDeg;
    }

    public void setMinMoveTexels(float minMoveTexels) {
        this.minMoveTexels = minMoveTexels;
    }

    public void setTeleportMoveTexels(float teleportMoveTexels) {
        this.teleportMoveTexels = teleportMoveTexels;
    }

    public void setGatedFirstCascades(int gatedFirstCascades) {
        this.gatedFirstCascades = gatedFirstCascades;
    }
}