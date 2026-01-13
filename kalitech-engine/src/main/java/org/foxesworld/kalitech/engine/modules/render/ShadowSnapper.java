// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Snaps a parallel-projection camera to a shadow-map texel grid (world-units).
 * This removes shimmering caused by sub-texel movement.
 *
 * Contract:
 *  - Works for parallel projection cameras (orthographic shadow cams).
 *  - Expects caller to keep cascade extents stable (fixed-square fitting + radius quantization).
 */
public final class ShadowSnapper {

    private final int shadowMapSize;

    // temps (no allocations per call)
    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    private final Vector3f tmp = new Vector3f();

    public ShadowSnapper(int shadowMapSize) {
        if (shadowMapSize <= 0) throw new IllegalArgumentException("shadowMapSize must be > 0");
        this.shadowMapSize = shadowMapSize;
    }

    private static float snapNearest(float v, float step) {
        if (!(step > 0f)) return v;
        return Math.round(v / step) * step;
    }

    public boolean snap(Camera shadowCam) {
        return snap(shadowCam, null);
    }

    /**
     * Snap shadowCam translation in its (left, up) plane so that 1 texel corresponds
     * to a fixed world-units step. This removes shimmering caused by sub-texel movement.
     *
     * @return true if camera moved
     */
    public boolean snap(Camera shadowCam, SnapDebug dbg) {
        if (shadowCam == null) return false;
        if (!shadowCam.isParallelProjection()) return false;

        final float frLeft = shadowCam.getFrustumLeft();
        final float frRight = shadowCam.getFrustumRight();
        final float frTop = shadowCam.getFrustumTop();
        final float frBottom = shadowCam.getFrustumBottom();

        final float width = frRight - frLeft;
        final float height = frTop - frBottom;
        if (!(width > 0f) || !(height > 0f)) return false;

        final float texelX = width / (float) shadowMapSize;
        final float texelY = height / (float) shadowMapSize;
        if (!(texelX > 0f) || !(texelY > 0f)) return false;

        shadowCam.setLocation(tmpLoc);
        shadowCam.getLeft(tmpLeft);
        shadowCam.getUp(tmpUp);

        // project camera position onto its left/up axes
        final float x = tmpLoc.dot(tmpLeft);
        final float y = tmpLoc.dot(tmpUp);

        final float sx = snapNearest(x, texelX);
        final float sy = snapNearest(y, texelY);

        final float dx = sx - x;
        final float dy = sy - y;

        if (dbg != null) {
            dbg.texelX = texelX;
            dbg.texelY = texelY;
            dbg.x = x;
            dbg.y = y;
            dbg.sx = sx;
            dbg.sy = sy;
            dbg.dx = dx;
            dbg.dy = dy;
            dbg.width = width;
            dbg.height = height;
        }

        if (dx == 0f && dy == 0f) return false;

        // convert delta from projected space back to world space
        delta.set(tmpLeft).multLocal(dx);
        tmp.set(tmpUp).multLocal(dy);
        delta.addLocal(tmp);

        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);
        shadowCam.update();

        return true;
    }

    public static final class SnapDebug {
        public float texelX, texelY;
        public float x, y;
        public float sx, sy;
        public float dx, dy;
        public float width, height;
    }
}