/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.math.FastMath
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.renderer.Camera
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 */
package org.foxesworld.kalitech.engine.modules.camera;

import com.jme3.app.SimpleApplication;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.modules.camera.CameraBasis;
import org.foxesworld.kalitech.engine.modules.camera.CameraDirty;
import org.foxesworld.kalitech.engine.modules.camera.Vec3View;

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
    private volatile float pitchLimitRad = 1.5697963f;

    public Camera(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.initFromNativeBestEffort();
    }

    public void flushOncePerFrame() {
        if (!this.engine.isJmeThread()) {
            return;
        }
        int mask = this.dirty.take();
        if (mask == 0) {
            return;
        }
        SimpleApplication app = this.engine.getApp();
        if (app == null) {
            return;
        }
        com.jme3.renderer.Camera cam = app.getCamera();
        if (cam == null) {
            return;
        }
        if ((mask & 1) != 0) {
            this.tmpV.set(this.desiredX, this.desiredY, this.desiredZ);
            cam.setLocation(this.tmpV);
            this.locView.set(this.tmpV.x, this.tmpV.y, this.tmpV.z);
        }
        if ((mask & 2) != 0) {
            float p = this.clampPitch(this.desiredPitch);
            float y = this.desiredYaw;
            this.tmpQ.fromAngles(p, y, 0.0f);
            cam.setRotation(this.tmpQ);
            this.cachedPitch = p;
            this.cachedYaw = y;
        }
    }

    public void setLocation(double x, double y, double z) {
        this.desiredX = (float)x;
        this.desiredY = (float)y;
        this.desiredZ = (float)z;
        this.dirty.mark(1);
    }

    public void setYawPitch(double yaw, double pitch) {
        this.desiredYaw = (float)yaw;
        this.desiredPitch = (float)pitch;
        this.cachedYaw = this.desiredYaw;
        this.cachedPitch = this.clampPitch(this.desiredPitch);
        this.dirty.mark(2);
    }

    public void moveWorld(double dx, double dy, double dz) {
        this.desiredX += (float)dx;
        this.desiredY += (float)dy;
        this.desiredZ += (float)dz;
        this.dirty.mark(1);
    }

    public void rotateYawPitch(double dYaw, double dPitch) {
        this.desiredYaw += (float)dYaw;
        this.desiredPitch += (float)dPitch;
        this.cachedYaw = this.desiredYaw;
        this.cachedPitch = this.clampPitch(this.desiredPitch);
        this.dirty.mark(2);
    }

    public void moveLocal(double dx, double dy, double dz) {
        float fdx = (float)dx;
        float fdy = (float)dy;
        float fdz = (float)dz;
        float y = this.cachedYaw;
        float p = this.cachedPitch;
        float cp = FastMath.cos((float)p);
        float sp = FastMath.sin((float)p);
        float cy = FastMath.cos((float)y);
        float sy = FastMath.sin((float)y);
        float fx = -sy * cp;
        float fy = sp;
        float fz = -cy * cp;
        float rx = cy;
        float ry = 0.0f;
        float rz = -sy;
        float ux = 0.0f * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - 0.0f * fx;
        this.desiredX += rx * fdx + ux * fdy + fx * fdz;
        this.desiredY += 0.0f * fdx + uy * fdy + fy * fdz;
        this.desiredZ += rz * fdx + uz * fdy + fz * fdz;
        this.dirty.mark(1);
    }

    public Vec3View locationView() {
        this.locView.set(this.desiredX, this.desiredY, this.desiredZ);
        return this.locView;
    }

    public double yaw() {
        return this.cachedYaw;
    }

    public double pitch() {
        return this.cachedPitch;
    }

    public Vec3View forwardView() {
        CameraBasis.forward(this.cachedYaw, this.cachedPitch, this.fwdView);
        return this.fwdView;
    }

    public Vec3View rightView() {
        CameraBasis.right(this.cachedYaw, this.rightView);
        return this.rightView;
    }

    public Vec3View upView() {
        CameraBasis.up(this.cachedYaw, this.cachedPitch, this.upView);
        return this.upView;
    }

    public void setPitchLimitRad(double limitRad) {
        float v = (float)limitRad;
        if (!Float.isFinite(v) || v <= 0.0f) {
            throw new IllegalArgumentException("limitRad must be > 0");
        }
        this.pitchLimitRad = v;
        this.cachedPitch = this.clampPitch(this.desiredPitch);
        this.dirty.mark(2);
    }

    public double pitchLimitRad() {
        return this.pitchLimitRad;
    }

    public void syncFromNativeNow() {
        this.initFromNativeStrict();
    }

    private void initFromNativeBestEffort() {
        block2: {
            try {
                this.initFromNativeStrict();
            }
            catch (Throwable t) {
                if (!log.isDebugEnabled()) break block2;
                log.debug("[camera] init from native skipped", t);
            }
        }
    }

    private void initFromNativeStrict() {
        SimpleApplication app = this.engine.getApp();
        if (app == null) {
            throw new IllegalStateException("engine.app is null");
        }
        com.jme3.renderer.Camera cam = app.getCamera();
        if (cam == null) {
            throw new IllegalStateException("engine.app.camera is null");
        }
        Vector3f p = cam.getLocation();
        this.desiredX = p.x;
        this.desiredY = p.y;
        this.desiredZ = p.z;
        float[] ang = new float[3];
        cam.getRotation().toAngles(ang);
        this.desiredPitch = ang[0];
        this.desiredYaw = ang[1];
        this.cachedPitch = this.clampPitch(this.desiredPitch);
        this.cachedYaw = this.desiredYaw;
        this.locView.set(p.x, p.y, p.z);
    }

    private float clampPitch(float pitch) {
        float limit = this.pitchLimitRad;
        if (pitch > limit) {
            return limit;
        }
        if (pitch < -limit) {
            return -limit;
        }
        return pitch;
    }
}

