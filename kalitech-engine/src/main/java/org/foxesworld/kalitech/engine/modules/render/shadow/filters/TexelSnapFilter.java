// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TexelSnapFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKeys;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Texel snapping with "hold-last-snap" mode.
 * <p>
 * Uses shared workspace for gating and publishing results.
 */
public final class TexelSnapFilter implements ShadowFilter {

    public boolean enabled = true;
    private final Vector3f tmpLoc = new Vector3f();

    private Snapper snapper;
    public int snapFirstCascades = 2;
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    private final Vector3f tmp = new Vector3f();

    private float[] lastX;
    private float[] lastY;
    private float[] lastTexel;
    private boolean[] hasLast;

    @Override
    public int order() {
        return 1000;
    }

    private static float snapDown(float v, float step) {
        if (!(step > 0f)) return v;
        return (float) Math.floor(v / step) * step;
    }

    @Override
    public void beginFrame(ShadowFrameContext ctx) {
        if (snapper == null) {
            snapper = new Snapper(ctx.shadowMapSize);
        }
        ensureArrays(ctx.numSplits);
    }

    private void ensureArrays(int splits) {
        if (lastX != null && lastX.length == splits) return;

        lastX = new float[splits];
        lastY = new float[splits];
        lastTexel = new float[splits];
        hasLast = new boolean[splits];

        for (int i = 0; i < splits; i++) {
            hasLast[i] = false;
            lastTexel[i] = Float.NaN;
        }
    }

    @Override
    public void afterShadowCam(ShadowSplitContext ctx) {
        ctx.snapped = false;
        ctx.texelSnapped = false;

        if (!enabled || snapper == null) return;
        if (ctx.splitIndex >= snapFirstCascades) return;

        Camera sc = ctx.shadowCam;

        float texelWorld = ctx.ws.getOrDefault(ShadowKeys.TEXEL_WORLD, 0f);
        if (!(texelWorld > 0f)) {
            float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
            float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
            float ortho = Math.max(orthoW, orthoH);
            texelWorld = (ctx.frame.shadowMapSize > 0 && ortho > 0f) ? (ortho / (float) ctx.frame.shadowMapSize) : 0f;
            if (texelWorld > 0f) {
                ctx.ws.put(ShadowKeys.TEXEL_WORLD, texelWorld);
            }
        }

        ctx.texelWorld = texelWorld;

        int si = ctx.splitIndex;

        Boolean allowBoxed = ctx.ws.get(ShadowKeys.ALLOW_TEXEL_SNAP);
        boolean allowResnap = (allowBoxed == null) || Boolean.TRUE.equals(allowBoxed);

        if (!allowResnap && hasLast[si] && (texelWorld > 0f) && !Float.isNaN(lastTexel[si])) {
            float dt = Math.abs(texelWorld - lastTexel[si]);
            if (dt > texelWorld * 0.25f) {
                allowResnap = true;
            }
        }

        ctx.ws.put(ShadowKeys.SNAP_APPLIED, true);

        if (allowResnap) {
            tmpLoc.set(sc.getLocation());
            tmpLeft.set(sc.getLeft());
            tmpUp.set(sc.getUp());

            float x = tmpLoc.dot(tmpLeft);
            float y = tmpLoc.dot(tmpUp);

            lastX[si] = snapDown(x, texelWorld);
            lastY[si] = snapDown(y, texelWorld);
            lastTexel[si] = texelWorld;
            hasLast[si] = true;

            float dx = lastX[si] - x;
            float dy = lastY[si] - y;

            if (dx != 0f || dy != 0f) {
                delta.set(tmpLeft).multLocal(dx);
                tmp.set(tmpUp).multLocal(dy);
                delta.addLocal(tmp);
                tmpLoc.addLocal(delta);
                sc.setLocation(tmpLoc);
                sc.update();

                ctx.snapped = true;
                ctx.texelSnapped = true;
            }

            ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, ctx.texelSnapped);
            return;
        }

        if (hasLast[si] && (texelWorld > 0f)) {
            tmpLoc.set(sc.getLocation());
            tmpLeft.set(sc.getLeft());
            tmpUp.set(sc.getUp());

            float x = tmpLoc.dot(tmpLeft);
            float y = tmpLoc.dot(tmpUp);

            float dx = lastX[si] - x;
            float dy = lastY[si] - y;

            if (dx != 0f || dy != 0f) {
                delta.set(tmpLeft).multLocal(dx);
                tmp.set(tmpUp).multLocal(dy);
                delta.addLocal(tmp);
                tmpLoc.addLocal(delta);
                sc.setLocation(tmpLoc);
                sc.update();
                ctx.snapped = true;
            }
        }

        ctx.ws.put(ShadowKeys.TEXEL_SNAPPED, false);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSnapFirstCascades(int snapFirstCascades) {
        this.snapFirstCascades = snapFirstCascades;
    }

    public void setSnapper(Snapper snapper) {
        this.snapper = snapper;
    }

    public void reset() {
        if (hasLast == null) return;
        for (int i = 0; i < hasLast.length; i++) {
            hasLast[i] = false;
            lastTexel[i] = Float.NaN;
        }
    }
}