// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TemporalSnapGateFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Temporal gate for texel snapping.
 * <p>
 * Publishes camera motion deltas (frame-scope) and per-split resnap permission (split-scope)
 * into the shared workspace.
 */
public final class TemporalSnapGateFilter implements ShadowFilter {

    private final Vector3f lastCamPos = new Vector3f();
    public boolean enabled = true;
    public float minMoveTexels = 1.25f;
    public float minRotateDeg = 0.25f;
    public float teleportMoveTexels = 64.0f;
    public int gatedFirstCascades = 2;
    private final Quaternion lastCamRot = new Quaternion();
    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    private boolean hasLast;
    private long lastFrameId = -1L;

    private float lastMoveWorld;
    private float lastAngleDeg;

    @Override
    public int order() {
        return 900;
    }

    private static float computeTexelWorld(Camera sc, int mapSize) {
        if (mapSize <= 0) return 0f;
        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);
        if (!(ortho > 0f)) return 0f;
        return ortho / (float) mapSize;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!enabled) return;

        long fid = ctx.frameId;
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

            ctx.ws.put(ShadowKeys.VIEW_CAM_MOVE_WORLD, 0f);
            ctx.ws.put(ShadowKeys.VIEW_CAM_ROTATE_DEG, 0f);
            return;
        }

        lastMoveWorld = lastCamPos.distance(p);

        invPrev.set(lastCamRot).inverseLocal();
        delta.set(invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos(FastMath.clamp(delta.getW(), -1f, 1f));
        lastAngleDeg = angleRad * FastMath.RAD_TO_DEG;

        lastCamPos.set(p);
        lastCamRot.set(r);

        ctx.ws.put(ShadowKeys.VIEW_CAM_MOVE_WORLD, lastMoveWorld);
        ctx.ws.put(ShadowKeys.VIEW_CAM_ROTATE_DEG, lastAngleDeg);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!enabled) return;

        float texelWorld = ctx.texelWorld;
        if (!(texelWorld > 0f)) {
            texelWorld = computeTexelWorld(ctx.shadowCam, ctx.frame.shadowMapSize);
            if (texelWorld > 0f) {
                ctx.texelWorld = texelWorld;
            }
        }

        if (texelWorld > 0f) {
            ctx.ws.put(ShadowKeys.TEXEL_WORLD, texelWorld);
        }

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
            return true;
        }

        float moveTexels = lastMoveWorld / texelWorld;

        if (teleportMoveTexels > 0f && moveTexels >= teleportMoveTexels) {
            return true;
        }

        if (minMoveTexels > 0f && moveTexels >= minMoveTexels) {
            return true;
        }

        if (minRotateDeg > 0f && lastAngleDeg >= minRotateDeg) {
            return true;
        }

        return false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMinMoveTexels(float minMoveTexels) {
        this.minMoveTexels = minMoveTexels;
    }

    public void setMinRotateDeg(float minRotateDeg) {
        this.minRotateDeg = minRotateDeg;
    }

    public void setTeleportMoveTexels(float teleportMoveTexels) {
        this.teleportMoveTexels = teleportMoveTexels;
    }

    public void setGatedFirstCascades(int gatedFirstCascades) {
        this.gatedFirstCascades = gatedFirstCascades;
    }
}