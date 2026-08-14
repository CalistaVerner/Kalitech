/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Set;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

public final class ShadowSnapperFilter
implements ShadowFilter {
    public boolean enabled = true;
    public int snapFirstCascades = 2;
    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    public boolean holdEnabled = true;
    public float holdThresholdTexels = 1.25f;
    private float[] lastSnapX;
    private float[] lastSnapY;
    private boolean[] lastSnapValid;
    private Snapper snapper;

    private static float snapNearest(float v, float step) {
        if (!(step > 0.0f)) {
            return v;
        }
        return (float)Math.floor(v / step + 0.5f) * step;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public Set<ShadowKey<?>> requires() {
        return Set.of(ShadowKeys.ALLOW_TEXEL_SNAP);
    }

    @Override
    public Set<ShadowKey<?>> provides() {
        return Set.of(ShadowKeys.SNAP_APPLIED, ShadowKeys.TEXEL_SNAPPED);
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (this.snapper == null || this.snapper.shadowMapSize() != ctx.shadowMapSize) {
            this.snapper = new Snapper(ctx.shadowMapSize);
        }
        if (this.lastSnapX == null || this.lastSnapX.length != ctx.numSplits) {
            this.lastSnapX = new float[ctx.numSplits];
            this.lastSnapY = new float[ctx.numSplits];
            this.lastSnapValid = new boolean[ctx.numSplits];
        }
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        boolean allow;
        ctx.snapped = false;
        ctx.texelSnapped = false;
        if (!this.enabled || this.snapper == null) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }
        if (ctx.splitIndex >= this.snapFirstCascades) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }
        Boolean allowBoxed = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        boolean bl = allow = allowBoxed == null || allowBoxed != false;
        if (!allow) {
            ctx.ws.put(ShadowKeys.SNAP_APPLIED, false);
            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
            return;
        }
        Camera sc = ctx.shadowCam;
        boolean changed = !this.holdEnabled || this.holdThresholdTexels <= 0.0f ? this.snapper.snap(sc) : this.snapHold(sc, ctx.splitIndex);
        ctx.snapped = changed;
        ctx.texelSnapped = changed;
        ctx.ws.put(ShadowKeys.SNAP_APPLIED, true);
        ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, changed);
    }

    private boolean snapHold(Camera shadowCam, int splitIndex) {
        if (shadowCam == null || !shadowCam.isParallelProjection()) {
            return false;
        }
        float left = shadowCam.getFrustumLeft();
        float right = shadowCam.getFrustumRight();
        float bottom = shadowCam.getFrustumBottom();
        float top = shadowCam.getFrustumTop();
        float width = right - left;
        float height = top - bottom;
        if (!(width > 0.0f) || !(height > 0.0f)) {
            return false;
        }
        float size = Math.max(width, height);
        float texel = size / (float)this.snapper.shadowMapSize();
        if (!(texel > 0.0f)) {
            return false;
        }
        this.tmpLoc.set(shadowCam.getLocation());
        this.tmpLeft.set(shadowCam.getLeft());
        this.tmpUp.set(shadowCam.getUp());
        float x = this.tmpLoc.dot(this.tmpLeft);
        float y = this.tmpLoc.dot(this.tmpUp);
        float sx = ShadowSnapperFilter.snapNearest(x, texel);
        float sy = ShadowSnapperFilter.snapNearest(y, texel);
        if (splitIndex >= 0 && splitIndex < this.lastSnapValid.length && this.lastSnapValid[splitIndex]) {
            float lx = this.lastSnapX[splitIndex];
            float ly = this.lastSnapY[splitIndex];
            float th = this.holdThresholdTexels * texel;
            if (Math.abs(sx - lx) < th) {
                sx = lx;
            }
            if (Math.abs(sy - ly) < th) {
                sy = ly;
            }
        }
        if (splitIndex >= 0 && splitIndex < this.lastSnapValid.length) {
            this.lastSnapX[splitIndex] = sx;
            this.lastSnapY[splitIndex] = sy;
            this.lastSnapValid[splitIndex] = true;
        }
        float dx = sx - x;
        float dy = sy - y;
        if (dx == 0.0f && dy == 0.0f) {
            return false;
        }
        this.delta.set(this.tmpLeft).multLocal(dx);
        this.delta.addLocal(this.tmpUp.x * dy, this.tmpUp.y * dy, this.tmpUp.z * dy);
        this.tmpLoc.addLocal(this.delta);
        shadowCam.setLocation(this.tmpLoc);
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

