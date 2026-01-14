// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowSnapper.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Snaps a parallel-projection camera to a shadow-map texel grid (world-units).
 * This removes shimmering caused by sub-texel movement.
 */
public final class ShadowSnapper {

    private final Vector3f tmp = new Vector3f();

    private final int shadowMapSize;

    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();
    public ShadowSnapper(int shadowMapSize) {
        if (shadowMapSize <= 0) throw new IllegalArgumentException("shadowMapSize must be > 0");
        this.shadowMapSize = shadowMapSize;
    }

    private static float snapDown(float v, float step) {
        if (!(step > 0f)) return v;
        return (float) Math.floor(v / step) * step;
    }

    /**
     * @return true if camera location was modified
     */
    public boolean snap(Camera shadowCam) {
        return snap(shadowCam, null);
    }

    /**
     * @return true if camera location was modified
     */
    public boolean snap(Camera shadowCam, SnapDebug dbg) {
        if (shadowCam == null) return false;
        if (!shadowCam.isParallelProjection()) return false;

        float left = shadowCam.getFrustumLeft();
        float right = shadowCam.getFrustumRight();
        float bottom = shadowCam.getFrustumBottom();
        float top = shadowCam.getFrustumTop();

        float width = right - left;
        float height = top - bottom;
        if (!(width > 0f) || !(height > 0f)) return false;

        // Use square size (max) to keep texel uniform.
        float size = Math.max(width, height);
        float texel = size / (float) shadowMapSize;
        if (!(texel > 0f)) return false;

        tmpLoc.set(shadowCam.getLocation());
        tmpLeft.set(shadowCam.getLeft());
        tmpUp.set(shadowCam.getUp());

        float x = tmpLoc.dot(tmpLeft);
        float y = tmpLoc.dot(tmpUp);

        float sx = snapDown(x, texel);
        float sy = snapDown(y, texel);

        float dx = sx - x;
        float dy = sy - y;

        if (dbg != null) {
            dbg.texel = texel;
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

        delta.set(tmpLeft).multLocal(dx);
        tmp.set(tmpUp).multLocal(dy);
        delta.addLocal(tmp);

        tmpLoc.addLocal(delta);
        shadowCam.setLocation(tmpLoc);
        shadowCam.update();
        return true;
    }

    public static final class SnapDebug {
        public float texel;
        public float x, y;     // projected coordinates before snap
        public float sx, sy;   // snapped coordinates
        public float dx, dy;   // delta in projected space
        public float width, height;
    }
}
