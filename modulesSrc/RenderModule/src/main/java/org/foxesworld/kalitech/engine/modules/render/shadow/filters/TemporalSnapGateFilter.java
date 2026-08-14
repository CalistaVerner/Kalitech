/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.FastMath
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.Set;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class TemporalSnapGateFilter
implements ShadowFilter {
    public boolean enabled = true;
    private final Vector3f lastCamPos = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    public float minRotateDeg = 0.25f;
    public float minMoveTexels = 1.25f;
    public float teleportMoveTexels = 24.0f;
    public int gatedFirstCascades = 1;
    private final Quaternion lastCamRot = new Quaternion();
    private final Quaternion invPrev = new Quaternion();
    private final Quaternion delta = new Quaternion();
    private boolean hasLast = false;
    private float lastMoveWorld = 0.0f;
    private float lastAngleDeg = 0.0f;
    private long lastFrameId = -1L;

    private static float estimateTexelWorld(ShadowSplitContext ctx) {
        float h;
        if (ctx == null || ctx.shadowCam == null) {
            return 0.0f;
        }
        if (!ctx.shadowCam.isParallelProjection()) {
            return 0.0f;
        }
        float w = ctx.shadowCam.getFrustumRight() - ctx.shadowCam.getFrustumLeft();
        float ortho = Math.max(w, h = ctx.shadowCam.getFrustumTop() - ctx.shadowCam.getFrustumBottom());
        if (!(ortho > 0.0f)) {
            return 0.0f;
        }
        int map = ctx.frame.shadowMapSize;
        if (map <= 0) {
            return 0.0f;
        }
        return ortho / (float)map;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void beginSplit(ShadowSplitContext ctx) {
        if (!this.enabled) {
            return;
        }
        if (ctx.splitIndex != 0) {
            return;
        }
        long fid = ctx.frame.frameId;
        if (fid == this.lastFrameId) {
            return;
        }
        this.lastFrameId = fid;
        Vector3f p = ctx.viewCam.getLocation();
        Quaternion r = ctx.viewCam.getRotation();
        if (!this.hasLast || Float.isNaN(this.lastCamPos.x)) {
            this.lastCamPos.set(p);
            this.lastCamRot.set(r);
            this.lastMoveWorld = 0.0f;
            this.lastAngleDeg = 0.0f;
            this.hasLast = true;
            return;
        }
        this.lastMoveWorld = this.lastCamPos.distance(p);
        this.invPrev.set(this.lastCamRot).inverseLocal();
        this.delta.set(this.invPrev).multLocal(r);
        float angleRad = 2.0f * FastMath.acos((float)FastMath.clamp((float)this.delta.getW(), (float)-1.0f, (float)1.0f));
        this.lastAngleDeg = angleRad * 57.295776f;
        this.lastCamPos.set(p);
        this.lastCamRot.set(r);
        ctx.frame.ws.put(ShadowKeys.VIEW_CAM_MOVE_WORLD, Float.valueOf(this.lastMoveWorld));
        ctx.frame.ws.put(ShadowKeys.VIEW_CAM_ROTATE_DEG, Float.valueOf(this.lastAngleDeg));
    }

    @Override
    public Set<ShadowKey<?>> provides() {
        return Set.of(ShadowKeys.VIEW_CAM_MOVE_WORLD, ShadowKeys.VIEW_CAM_ROTATE_DEG, ShadowKeys.ALLOW_TEXEL_SNAP, ShadowKeys.TEXEL_WORLD);
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        if (!this.enabled) {
            ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, Boolean.TRUE);
            return;
        }
        float texelWorld = ctx.ws.getOrDefault(ShadowKeys.TEXEL_WORLD, Float.valueOf(0.0f)).floatValue();
        if (!(texelWorld > 0.0f) && (texelWorld = TemporalSnapGateFilter.estimateTexelWorld(ctx)) > 0.0f) {
            ctx.ws.put(ShadowKeys.TEXEL_WORLD, Float.valueOf(texelWorld));
        }
        boolean allow = this.allowResnap(ctx, texelWorld);
        ctx.ws.put(ShadowKeys.ALLOW_TEXEL_SNAP, allow);
    }

    public boolean allowResnap(ShadowSplitContext ctx, float texelWorld) {
        if (!this.enabled) {
            return true;
        }
        if (ctx.splitIndex >= this.gatedFirstCascades) {
            return true;
        }
        if (!this.hasLast) {
            return true;
        }
        if (!(texelWorld > 0.0f)) {
            return true;
        }
        float moveTexels = this.lastMoveWorld / texelWorld;
        if (moveTexels >= this.teleportMoveTexels) {
            return true;
        }
        if (moveTexels >= this.minMoveTexels) {
            return true;
        }
        return this.lastAngleDeg >= this.minRotateDeg;
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

