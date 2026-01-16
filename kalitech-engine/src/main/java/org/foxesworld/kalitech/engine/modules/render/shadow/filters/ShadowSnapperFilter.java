// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/ShadowSnapperFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.*;

/**
 * Texel snapping for cascaded shadow cameras.
 * <p>
 * Executes after the shadow camera is finalized (handled by filters or by the default jME path),
 * and before occluder gather/cull. This guarantees that all downstream work observes the snapped
 * camera and eliminates shimmering caused by sub-texel movement.
 */
public final class ShadowSnapperFilter implements ShadowFilter {

    public boolean enabled = true;
    public int snapFirstCascades = 2;

    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    /**
     * Adds hysteresis on top of texel snapping (prevents sub-texel oscillation near grid boundaries).
     */
    public boolean holdEnabled = true;
    /**
     * Hysteresis size in texels. Larger values = more stable, more latency when crossing texel boundaries.
     */
    public float holdThresholdTexels = 1.25f;
    private float[] lastSnapX;
    private float[] lastSnapY;
    private boolean[] lastSnapValid;

    private Snapper snapper;

    private static float snapNearest(float v, float step) {
        if (!(step > 0f)) return v;
        return (float) Math.floor((v / step) + 0.5f) * step;
    }

    @Override
    public int order() {
        return ShadowOrders.TEXEL_SNAP_FINAL;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null || snapper.shadowMapSize() != ctx.shadowMapSize) {
            snapper = new Snapper(ctx.shadowMapSize);
        }

        if (lastSnapX == null || lastSnapX.length != ctx.numSplits) {
            lastSnapX = new float[ctx.numSplits];
            lastSnapY = new float[ctx.numSplits];
            lastSnapValid = new boolean[ctx.numSplits];
        }
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        ctx.snapped = false;
        ctx.texelSnapped = false;

        if (!enabled || snapper == null) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }

        if (ctx.splitIndex >= snapFirstCascades) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }

        Boolean allowBoxed = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        boolean allow = allowBoxed == null || allowBoxed;
        if (!allow) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }

        Camera sc = ctx.shadowCam;

        boolean changed;
        if (!holdEnabled || holdThresholdTexels <= 0f) {
            changed = snapper.snap(sc);
        } else {
            changed = snapHold(sc, ctx.splitIndex);
        }

        ctx.snapped = changed;
        ctx.texelSnapped = changed;

        ctx.ws.put(ShadowKeys.SNAP_APPLIED, true);
        ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, changed);
    }

    private boolean snapHold(Camera shadowCam, int splitIndex) {
        if (shadowCam == null || !shadowCam.isParallelProjection()) return false;

        float left = shadowCam.getFrustumLeft();
        float right = shadowCam.getFrustumRight();
        float bottom = shadowCam.getFrustumBottom();
        float top = shadowCam.getFrustumTop();

        float width = right - left;
        float height = top - bottom;
        if (!(width > 0f) || !(height > 0f)) return false;

        float size = Math.max(width, height);
        float texel = size / (float) snapper.shadowMapSize();
        if (!(texel > 0f)) return false;

        tmpLoc.set(shadowCam.getLocation());
        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());

        float x = tmpLoc.dot(tmpLeft);
        float y = tmpLoc.dot(tmpUp);

        float sx = snapNearest(x, texel);
        float sy = snapNearest(y, texel);

        if (splitIndex >= 0 && splitIndex < lastSnapValid.length && lastSnapValid[splitIndex]) {
            float lx = lastSnapX[splitIndex];
            float ly = lastSnapY[splitIndex];

            float th = holdThresholdTexels * texel;
            if (Math.abs(sx - lx) < th) sx = lx;
            if (Math.abs(sy - ly) < th) sy = ly;
        }

        if (splitIndex >= 0 && splitIndex < lastSnapValid.length) {
            lastSnapX[splitIndex] = sx;
            lastSnapY[splitIndex] = sy;
            lastSnapValid[splitIndex] = true;
        }

        float dx = sx - x;
        float dy = sy - y;
        if (dx == 0f && dy == 0f) return false;

        delta.set(tmpLeft).multLocal(dx);
        delta.addLocal(tmpUp.x * dy, tmpUp.y * dy, tmpUp.z * dy);
        tmpLoc.addLocal(delta);

        shadowCam.setLocation(tmpLoc);
        shadowCam.update();
        return true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSnapFirstCascades(int snapFirstCascades) {
        this.snapFirstCascades = snapFirstCascades;
    }

    public void setHoldEnabled(boolean holdEnabled) {
        this.holdEnabled = holdEnabled;
    }

    public void setHoldThresholdTexels(float holdThresholdTexels) {
        this.holdThresholdTexels = holdThresholdTexels;
    }

    public void setSnapper(Snapper snapper) {
        this.snapper = snapper;
    }
}