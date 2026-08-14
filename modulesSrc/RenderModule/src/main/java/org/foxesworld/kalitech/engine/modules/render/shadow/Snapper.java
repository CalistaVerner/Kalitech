/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 */
package org.foxesworld.kalitech.engine.modules.render.shadow;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public final class Snapper {
    private final Vector3f tmp = new Vector3f();
    private final int shadowMapSize;
    private final Vector3f tmpLoc = new Vector3f();
    private final Vector3f tmpLeft = new Vector3f();
    private final Vector3f tmpUp = new Vector3f();
    private final Vector3f delta = new Vector3f();

    public Snapper(int shadowMapSize) {
        if (shadowMapSize <= 0) {
            throw new IllegalArgumentException("shadowMapSize must be > 0");
        }
        this.shadowMapSize = shadowMapSize;
    }

    public int shadowMapSize() {
        return this.shadowMapSize;
    }

    private static float snapNearest(float v, float step) {
        if (!(step > 0.0f)) {
            return v;
        }
        return (float)Math.floor(v / step + 0.5f) * step;
    }

    public boolean snap(Camera shadowCam) {
        return this.snap(shadowCam, null);
    }

    public boolean snap(Camera shadowCam, SnapDebug dbg) {
        if (shadowCam == null) {
            return false;
        }
        if (!shadowCam.isParallelProjection()) {
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
        float texel = size / (float)this.shadowMapSize;
        if (!(texel > 0.0f)) {
            return false;
        }
        this.tmpLoc.set(shadowCam.getLocation());
        this.tmpLeft.set(shadowCam.getLeft());
        this.tmpUp.set(shadowCam.getUp());
        float x = this.tmpLoc.dot(this.tmpLeft);
        float y = this.tmpLoc.dot(this.tmpUp);
        float sx = Snapper.snapNearest(x, texel);
        float sy = Snapper.snapNearest(y, texel);
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
        if (dx == 0.0f && dy == 0.0f) {
            return false;
        }
        this.delta.set(this.tmpLeft).multLocal(dx);
        this.tmp.set(this.tmpUp).multLocal(dy);
        this.delta.addLocal(this.tmp);
        this.tmpLoc.addLocal(this.delta);
        shadowCam.setLocation(this.tmpLoc);
        shadowCam.update();
        return true;
    }

    public static final class SnapDebug {
        public float texel;
        public float x;
        public float y;
        public float sx;
        public float sy;
        public float dx;
        public float dy;
        public float width;
        public float height;
    }
}

