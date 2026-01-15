// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/filters/TexelSnapFilter.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.filters;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.foxesworld.kalitech.engine.modules.render.shadow.Snapper;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFrameContext;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowSplitContext;

/**
 * Texel snapping with "hold-last-snap" mode.
 * <p>
 * If temporal gate disallows re-snap, we still prevent sub-texel drift by
 * locking the projected (left/up) coordinates to the last snapped position.
 */
public final class TexelSnapFilter implements ShadowFilter {

    public boolean enabled = true;
    public int snapFirstCascades = 1;

    private final Vector3f tmpLoc = new Vector3f();

    private Snapper snapper;
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    /**
     * Optional temporal gate (set by pipeline/registry).
     */
    public TemporalSnapGateFilter gate;
    // Per-split locked snapped coordinates in projected light space
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
        ctx.texelWorld = 0f;

        if (!enabled || snapper == null) return;
        if (ctx.splitIndex >= snapFirstCascades) return;

        Camera sc = ctx.shadowCam;

        // Compute texel world size from current ortho size and actual shadow map size
        float orthoW = sc.getFrustumRight() - sc.getFrustumLeft();
        float orthoH = sc.getFrustumTop() - sc.getFrustumBottom();
        float ortho = Math.max(orthoW, orthoH);

        float texelWorld = (ctx.frame.shadowMapSize > 0) ? (ortho / (float) ctx.frame.shadowMapSize) : 0f;
        ctx.texelWorld = texelWorld;

        int si = ctx.splitIndex;

        boolean allowResnap = true;
        if (gate != null) {
            allowResnap = gate.allowResnap(ctx, texelWorld);
        }

        // If texel grid changed noticeably, force re-snap once (prevents long "almost snapped" drift)
        if (!allowResnap && hasLast[si] && (texelWorld > 0f) && !Float.isNaN(lastTexel[si])) {
            float dt = Math.abs(texelWorld - lastTexel[si]);
            if (dt > texelWorld * 0.25f) {
                allowResnap = true;
            }
        }

        // Always ensure we have a baseline snap at least once
        if (!hasLast[si]) {
            allowResnap = true;
        }

        if (allowResnap) {
            // Full snap (quantize to texel grid)
            snapper.snap(sc);
            sc.update();

            // Record snapped projected coords for hold mode
            tmpLoc.set(sc.getLocation());
            tmpLeft.set(sc.getLeft());
            tmpUp.set(sc.getUp());

            float x = tmpLoc.dot(tmpLeft);
            float y = tmpLoc.dot(tmpUp);

            lastX[si] = snapDown(x, texelWorld);
            lastY[si] = snapDown(y, texelWorld);
            lastTexel[si] = texelWorld;
            hasLast[si] = true;

            ctx.snapped = true;
            return;
        }

        // Hold-last-snap mode: lock projected coords to last snapped (prevents sub-texel drift)
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
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSnapFirstCascades(int snapFirstCascades) {
        this.snapFirstCascades = snapFirstCascades;
    }

    public void setGate(TemporalSnapGateFilter gate) {
        this.gate = gate;
    }

    public void setSnapper(Snapper snapper) {
        this.snapper = snapper;
    }

    /**
     * Clears last snapped state (use on full reload if desired).
     */
    public void reset() {
        if (hasLast == null) return;
        for (int i = 0; i < hasLast.length; i++) {
            hasLast[i] = false;
            lastTexel[i] = Float.NaN;
        }
    }
}