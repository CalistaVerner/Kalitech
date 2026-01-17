// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowTemporalStabilityPolicyFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowOrders;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

import java.util.Set;

/**
 * Centralized temporal stability policy.
 * <p>
 * This filter computes view camera motion (translation/rotation) once per frame and produces
 * per-split decisions for:
 * <ul>
 *   <li>Whether shadow camera refit is allowed ({@link ShadowKeys#ALLOW_SHADOW_CAM_REFIT}).</li>
 *   <li>Whether texel snapping is allowed ({@link ShadowKeys#ALLOW_TEXEL_SNAP}).</li>
 * </ul>
 * The goal is to provide a single source of truth for temporal behavior so that future GPU shader
 * integration can rely on deterministic decisions and stable parameters.
 */
public final class ShadowTemporalStabilityPolicyFilter implements ShadowFilter {

    private boolean enabled = true;

    private float minRotateDegForSnap = 0.25f;
    private float minMoveTexelsForSnap = 1.25f;
    private float teleportMoveTexels = 24.0f;
    private int gateSnapFirstCascades = 1;

    private float minRotateDegForRefit = 0.15f;
    private float minMoveTexelsForRefit = 0.75f;
    private int gateRefitFirstCascades = 1;

    private final Vector3f lastCamPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private final Quaternion lastCamRot = new Quaternion();
    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    private boolean hasLast = false;

    private long computedFrameId = Long.MIN_VALUE;
    private float lastMoveWorld = 0f;
    private float lastAngleDeg = 0f;
    private boolean lastTeleport = false;
    private float lastStabilityScore = 1f;

    @Override
    public int order() {
        return ShadowOrders.TEMPORAL_POLICY;
    }

    @Override
    public Set<ShadowKey<?>> provides() {
        return Set.of(
                ShadowKeys.VIEW_CAM_MOVE_WORLD,
                ShadowKeys.VIEW_CAM_ROTATE_DEG,
                ShadowKeys.VIEW_CAM_TELEPORT,
                ShadowKeys.STABILITY_SCORE,
                ShadowKeys.ALLOW_SHADOW_CAM_REFIT,
                ShadowKeys.ALLOW_TEXEL_SNAP,
                ShadowKeys.SPLIT_TELEPORT,
                ShadowKeys.TEXEL_WORLD
        );
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (!enabled) return;
        computeFrameMotion(ctx);
        ctx.ws.put(ShadowKeys.VIEW_CAM_MOVE_WORLD, lastMoveWorld);
        ctx.ws.put(ShadowKeys.VIEW_CAM_ROTATE_DEG, lastAngleDeg);
        ctx.ws.put(ShadowKeys.VIEW_CAM_TELEPORT, lastTeleport);
        ctx.ws.put(ShadowKeys.STABILITY_SCORE, lastStabilityScore);
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        if (!enabled) {
            ctx.ws.put(ShadowKeys.ALLOW_SHADOW_CAM_REFIT, Boolean.TRUE);
            ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, Boolean.TRUE);
            ctx.ws.put(ShadowKeys.SPLIT_TELEPORT, Boolean.FALSE);
            return;
        }

        computeFrameMotion(ctx.frame);

        float texelWorld = estimateTexelWorld(ctx.shadowCam, ctx.frame.shadowMapSize);
        if (texelWorld > 0f) {
            ctx.ws.put(ShadowKeys.TEXEL_WORLD, texelWorld);
            ctx.texelWorld = texelWorld;
        }

        boolean teleport = lastTeleport;
        if (!teleport && texelWorld > 0f) {
            float moveTexels = lastMoveWorld / texelWorld;
            teleport = moveTexels >= teleportMoveTexels;
        }
        ctx.ws.put(ShadowKeys.SPLIT_TELEPORT, teleport);

        boolean allowRefit = computeAllowRefit(ctx.splitIndex, texelWorld, teleport);
        boolean allowSnap = computeAllowSnap(ctx.splitIndex, texelWorld, teleport);

        ctx.ws.put(ShadowKeys.ALLOW_SHADOW_CAM_REFIT, allowRefit);
        ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, allowSnap);
    }

    private void computeFrameMotion(ShadowFrameContext ctx) {
        long fid = ctx.frameId;
        if (fid == computedFrameId) return;
        computedFrameId = fid;

        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();

        if (!hasLast || Float.isNaN(lastCamPos.x)) {
            lastCamPos.set(p);
            lastCamRot.set(r);
            lastMoveWorld = 0f;
            lastAngleDeg = 0f;
            lastTeleport = false;
            lastStabilityScore = 1f;
            hasLast = true;
            return;
        }

        lastMoveWorld = lastCamPos.distance(p);

        invPrev.set(lastCamRot).inverseLocal();
        delta.set(invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos(FastMath.clamp(delta.getW(), -1f, 1f));
        lastAngleDeg = angleRad * FastMath.RAD_TO_DEG;

        lastCamPos.set(p);
        lastCamRot.set(r);

        // Hard pre-classification for extreme cases (split-level teleport is refined via texelWorld).
        lastTeleport = lastMoveWorld > 1000.0f;

        float moveScore = 1.0f - FastMath.clamp(lastMoveWorld / 2.0f, 0f, 1f);
        float rotScore = 1.0f - FastMath.clamp(lastAngleDeg / 5.0f, 0f, 1f);
        lastStabilityScore = FastMath.clamp(moveScore * 0.75f + rotScore * 0.25f, 0f, 1f);
    }

    private boolean computeAllowSnap(int splitIndex, float texelWorld, boolean teleport) {
        if (!enabled) return true;
        if (teleport) return true;
        if (splitIndex >= gateSnapFirstCascades) return true;
        if (!(texelWorld > 0f)) return true;

        float moveTexels = lastMoveWorld / texelWorld;
        if (moveTexels >= teleportMoveTexels) return true;
        if (moveTexels >= minMoveTexelsForSnap) return true;
        return lastAngleDeg >= minRotateDegForSnap;
    }

    private boolean computeAllowRefit(int splitIndex, float texelWorld, boolean teleport) {
        if (!enabled) return true;
        if (teleport) return true;
        if (splitIndex >= gateRefitFirstCascades) return true;
        if (!(texelWorld > 0f)) return true;

        float moveTexels = lastMoveWorld / texelWorld;
        if (moveTexels >= teleportMoveTexels) return true;
        if (moveTexels >= minMoveTexelsForRefit) return true;
        return lastAngleDeg >= minRotateDegForRefit;
    }

    private static float estimateTexelWorld(Camera shadowCam, int mapSize) {
        if (shadowCam == null || mapSize <= 0) return 0f;
        if (!shadowCam.isParallelProjection()) return 0f;

        float w = shadowCam.getFrustumRight() - shadowCam.getFrustumLeft();
        float h = shadowCam.getFrustumTop() - shadowCam.getFrustumBottom();
        float ortho = Math.max(w, h);
        if (!(ortho > 0f)) return 0f;
        return ortho / (float) mapSize;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMinRotateDegForSnap(float minRotateDegForSnap) {
        this.minRotateDegForSnap = Math.max(0f, minRotateDegForSnap);
    }

    public void setMinMoveTexelsForSnap(float minMoveTexelsForSnap) {
        this.minMoveTexelsForSnap = Math.max(0f, minMoveTexelsForSnap);
    }

    public void setTeleportMoveTexels(float teleportMoveTexels) {
        this.teleportMoveTexels = Math.max(0f, teleportMoveTexels);
    }

    public void setGateSnapFirstCascades(int gateSnapFirstCascades) {
        this.gateSnapFirstCascades = Math.max(0, gateSnapFirstCascades);
    }

    public void setMinRotateDegForRefit(float minRotateDegForRefit) {
        this.minRotateDegForRefit = Math.max(0f, minRotateDegForRefit);
    }

    public void setMinMoveTexelsForRefit(float minMoveTexelsForRefit) {
        this.minMoveTexelsForRefit = Math.max(0f, minMoveTexelsForRefit);
    }

    public void setGateRefitFirstCascades(int gateRefitFirstCascades) {
        this.gateRefitFirstCascades = Math.max(0, gateRefitFirstCascades);
    }
}