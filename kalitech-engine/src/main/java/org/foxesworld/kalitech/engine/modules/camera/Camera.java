package org.foxesworld.kalitech.engine.modules.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.Objects;

/**
 * Owns batched desired camera transform and applies it once per frame on the JME thread.
 *
 * <p>Threading:
 * <ul>
 *   <li>{@code set*}/{@code move*}/{@code rotate*} are thread-safe (volatile writes + dirty bits).</li>
 *   <li>{@link #flushOncePerFrame()} must be called from the JME thread.</li>
 *   <li>Views returned by {@code *View()} are reused and mutable (no allocations).</li>
 * </ul>
 */
public final class Camera {

    private static final Logger log = LogManager.getLogger(Camera.class);

    private final EngineApiImpl engine;
    private final CameraDirty dirty = new CameraDirty();

    private final Vector3f tmpV = new Vector3f();
    private final Quaternion tmpQ = new Quaternion();

    private final Vec3View locView = new Vec3View();
    private final Vec3View fwdView = new Vec3View();
    private final Vec3View rightView = new Vec3View();
    private final Vec3View upView = new Vec3View();

    private volatile float desiredX;
    private volatile float desiredY;
    private volatile float desiredZ;
    private volatile float desiredYaw;
    private volatile float desiredPitch;

    private volatile float cachedYaw;
    private volatile float cachedPitch;

    private volatile float pitchLimitRad = (FastMath.HALF_PI - 0.001f);

    public Camera(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        initFromNativeBestEffort();
    }

    public void flushOncePerFrame() {
        if (!engine.isJmeThread()) return;

        final int mask = dirty.take();
        if (mask == 0) return;

        final var app = engine.getApp();
        if (app == null) return;

        final var cam = app.getCamera();
        if (cam == null) return;

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

        final float cp = FastMath.cos(p);
        final float sp = FastMath.sin(p);
        final float cy = FastMath.cos(y);
        final float sy = FastMath.sin(y);

        final float fx = -sy * cp;
        final float fy = sp;
        final float fz = -cy * cp;

        final float rx = cy;
        final float ry = 0f;
        final float rz = -sy;

        final float ux = (ry * fz) - (rz * fy);
        final float uy = (rz * fx) - (rx * fz);
        final float uz = (rx * fy) - (ry * fx);

        desiredX += (rx * fdx + ux * fdy + fx * fdz);
        desiredY += (ry * fdx + uy * fdy + fy * fdz);
        desiredZ += (rz * fdx + uz * fdy + fz * fdz);

        dirty.mark(CameraDirty.LOC);
    }

    public Vec3View locationView() {
        locView.set(desiredX, desiredY, desiredZ);
        return locView;
    }

    public double yaw() {
        return cachedYaw;
    }

    public double pitch() {
        return cachedPitch;
    }

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

    public void setPitchLimitRad(double limitRad) {
        float v = (float) limitRad;
        if (!Float.isFinite(v) || v <= 0f) throw new IllegalArgumentException("limitRad must be > 0");
        pitchLimitRad = v;

        cachedPitch = clampPitch(desiredPitch);
        dirty.mark(CameraDirty.ROT);
    }

    public double pitchLimitRad() {
        return pitchLimitRad;
    }

    public void syncFromNativeNow() {
        initFromNativeStrict();
    }

    private void initFromNativeBestEffort() {
        try {
            initFromNativeStrict();
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                log.debug("[camera] init from native skipped", t);
            }
        }
    }

    private void initFromNativeStrict() {
        final var app = engine.getApp();
        if (app == null) throw new IllegalStateException("engine.app is null");

        final var cam = app.getCamera();
        if (cam == null) throw new IllegalStateException("engine.app.camera is null");

        Vector3f p = cam.getLocation();
        desiredX = p.x;
        desiredY = p.y;
        desiredZ = p.z;

        float[] ang = new float[3];
        cam.getRotation().toAngles(ang);

        desiredPitch = ang[0];
        desiredYaw = ang[1];

        cachedPitch = clampPitch(desiredPitch);
        cachedYaw = desiredYaw;

        locView.set(p.x, p.y, p.z);
    }

    private float clampPitch(float pitch) {
        final float limit = pitchLimitRad;
        if (pitch > limit) return limit;
        if (pitch < -limit) return -limit;
        return pitch;
    }
}
