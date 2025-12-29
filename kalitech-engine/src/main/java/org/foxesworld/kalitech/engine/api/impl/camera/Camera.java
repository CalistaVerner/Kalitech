package org.foxesworld.kalitech.engine.api.impl.camera;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.CameraState;

/**
 * Owns batched desired camera transform + applies it once per frame on JME thread.
 *
 * Threading:
 * - set* methods are thread-safe (volatile writes + dirty bits).
 * - flush() must be called from JME thread (EngineApiImpl.__updateTime).
 */
public final class Camera {

    private static final Logger log = LogManager.getLogger(Camera.class);

    private final EngineApiImpl engine;
    private final CameraState state;
    private final CameraDirty dirty = new CameraDirty();

    // temps (JME thread only)
    private final Vector3f tmpV = new Vector3f();
    private final Quaternion tmpQ = new Quaternion();

    // desired (thread-safe)
    private volatile float desiredX;
    private volatile float desiredY;
    private volatile float desiredZ;

    private volatile float desiredYaw;
    private volatile float desiredPitch;

    // cached (last applied/read)
    private volatile float cachedYaw;
    private volatile float cachedPitch;

    // views
    private final Vec3View locView = new Vec3View();
    private final Vec3View fwdView = new Vec3View();
    private final Vec3View rightView = new Vec3View();
    private final Vec3View upView = new Vec3View();

    public Camera(EngineApiImpl engine, CameraState state) {
        this.engine = engine;
        this.state = state;

        // init from native camera (best effort)
        try {
            var cam = engine.getApp().getCamera();
            Vector3f p = cam.getLocation();
            desiredX = p.x; desiredY = p.y; desiredZ = p.z;

            float[] ang = new float[3];
            cam.getRotation().toAngles(ang);
            desiredPitch = ang[0];
            desiredYaw = ang[1];

            cachedPitch = desiredPitch;
            cachedYaw = desiredYaw;

            locView.set(p.x, p.y, p.z);
        } catch (Throwable ignored) {}
    }

    // ---------- Flush ----------

    public void flushOncePerFrame() {
        if (!engine.isJmeThread()) return;

        final int mask = dirty.take();
        if (mask == 0) return;

        final var cam = engine.getApp().getCamera();

        if ((mask & CameraDirty.LOC) != 0) {
            tmpV.set(desiredX, desiredY, desiredZ);
            cam.setLocation(tmpV);
            locView.set(tmpV.x, tmpV.y, tmpV.z);
        }

        if ((mask & CameraDirty.ROT) != 0) {
            final float p = clampPitch(desiredPitch);
            final float y = desiredYaw;
            tmpQ.fromAngles(p, y, 0f);
            cam.setRotation(tmpQ);
            cachedPitch = p;
            cachedYaw = y;
        }
    }

    // ---------- Desired setters (thread-safe) ----------

    public void setLocation(double x, double y, double z) {
        desiredX = (float) x;
        desiredY = (float) y;
        desiredZ = (float) z;
        dirty.mark(CameraDirty.LOC);
    }

    public void setYawPitch(double yaw, double pitch) {
        desiredYaw = (float) yaw;
        desiredPitch = (float) pitch;

        cachedYaw = desiredYaw;
        cachedPitch = clampPitch(desiredPitch);

        dirty.mark(CameraDirty.ROT);
    }

    public void moveWorld(double dx, double dy, double dz) {
        desiredX += (float) dx;
        desiredY += (float) dy;
        desiredZ += (float) dz;
        dirty.mark(CameraDirty.LOC);
    }

    public void rotateYawPitch(double dYaw, double dPitch) {
        desiredYaw += (float) dYaw;
        desiredPitch += (float) dPitch;

        cachedYaw = desiredYaw;
        cachedPitch = clampPitch(desiredPitch);

        dirty.mark(CameraDirty.ROT);
    }

    public void moveLocal(double dx, double dy, double dz) {
        final float fdx = (float) dx;
        final float fdy = (float) dy;
        final float fdz = (float) dz;

        final float y = cachedYaw;
        final float p = cachedPitch;

        // compute basis cheaply; no allocations
        // forward
        final float cp = com.jme3.math.FastMath.cos(p);
        final float sp = com.jme3.math.FastMath.sin(p);
        final float cy = com.jme3.math.FastMath.cos(y);
        final float sy = com.jme3.math.FastMath.sin(y);

        final float fx = -sy * cp;
        final float fy = sp;
        final float fz = -cy * cp;

        // right (roll=0)
        final float rx = cy;
        final float ry = 0f;
        final float rz = -sy;

        // up = cross(right, forward)
        final float ux = (ry * fz) - (rz * fy);
        final float uy = (rz * fx) - (rx * fz);
        final float uz = (rx * fy) - (ry * fx);

        desiredX += (rx * fdx + ux * fdy + fx * fdz);
        desiredY += (ry * fdx + uy * fdy + fy * fdz);
        desiredZ += (rz * fdx + uz * fdy + fz * fdz);

        dirty.mark(CameraDirty.LOC);
    }

    // ---------- Getters / views ----------

    public Vec3View locationView() {
        locView.set(desiredX, desiredY, desiredZ);
        return locView;
    }

    public double yaw() { return cachedYaw; }
    public double pitch() { return cachedPitch; }

    public Vec3View forwardView() {
        CameraBasis.forward(cachedYaw, cachedPitch, fwdView);
        return fwdView;
    }

    public Vec3View rightView() {
        CameraBasis.right(cachedYaw, rightView);
        return rightView;
    }

    public Vec3View upView() {
        CameraBasis.up(cachedYaw, cachedPitch, upView);
        return upView;
    }

    // ---------- helpers ----------

    private float clampPitch(float pitch) {
        float limit = state.pitchLimit;
        if (limit <= 0f) return pitch;
        if (pitch > limit) return limit;
        if (pitch < -limit) return -limit;
        return pitch;
    }
}