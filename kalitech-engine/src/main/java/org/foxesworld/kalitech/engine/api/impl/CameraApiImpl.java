// FILE: org/foxesworld/kalitech/engine/api/impl/CameraApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thin bridge to jME Camera for scripts.
 *
 * IMPORTANT:
 *  - No hardcoded camera modes here.
 *  - Provide stable transform primitives + a few convenience helpers.
 *  - Keep deprecated legacy methods as config storage for backward compatibility.
 */
public final class CameraApiImpl implements CameraApi {

    private static final Logger log = LogManager.getLogger(CameraApiImpl.class);

    private final EngineApiImpl engine;
    private final SimpleApplication app;
    private final CameraState state;

    // temps to avoid per-frame allocations
    private final Vector3f tmpV = new Vector3f();
    private final float[] tmpAngles = new float[3];
    private final Quaternion tmpQ = new Quaternion();

    // legacy storage
    private volatile String legacyMode = "free";
    private volatile Value legacyFlyCfg = null;

    public CameraApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = engine.getApp();
        this.state = new CameraState();
    }

    // -------------------------------------------------------------------------
    // Core transform primitives
    // -------------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public Object location() {
        Vector3f p = app.getCamera().getLocation();
        // Map.of allocates small objects anyway; HashMap is fine too.
        // Keeping it explicit and JS-friendly.
        Map<String, Object> m = new HashMap<>(3);
        m.put("x", (double) p.x);
        m.put("y", (double) p.y);
        m.put("z", (double) p.z);
        return m;
    }

    @HostAccess.Export
    @Override
    public void setLocation(Value v) {
        if (v == null || v.isNull()) return;
        double x = num(v, "x", 0.0);
        double y = num(v, "y", 0.0);
        double z = num(v, "z", 0.0);
        setLocation(x, y, z);
    }

    @HostAccess.Export
    @Override
    public void setLocation(double x, double y, double z) {
        app.getCamera().setLocation(tmpV.set((float) x, (float) y, (float) z));
    }

    @HostAccess.Export
    @Override
    public void setYawPitch(double yaw, double pitch) {
        float y = (float) yaw;
        float p = clampPitch((float) pitch);

        // jME: fromAngles(pitch, yaw, roll)
        tmpQ.fromAngles(p, y, 0f);
        app.getCamera().setRotation(tmpQ);
    }

    @HostAccess.Export
    @Override
    public double yaw() {
        app.getCamera().getRotation().toAngles(tmpAngles);
        return tmpAngles[1];
    }

    @HostAccess.Export
    @Override
    public double pitch() {
        app.getCamera().getRotation().toAngles(tmpAngles);
        return tmpAngles[0];
    }

    @HostAccess.Export
    @Override
    public Object forward() {
        Vector3f d = app.getCamera().getDirection();
        Map<String, Object> m = new HashMap<>(3);
        m.put("x", (double) d.x);
        m.put("y", (double) d.y);
        m.put("z", (double) d.z);
        return m;
    }

    @HostAccess.Export
    @Override
    public Object right() {
        // In jME, camera.getLeft() exists; right is -left
        Vector3f left = app.getCamera().getLeft();
        Map<String, Object> m = new HashMap<>(3);
        m.put("x", (double) (-left.x));
        m.put("y", (double) (-left.y));
        m.put("z", (double) (-left.z));
        return m;
    }

    @HostAccess.Export
    @Override
    public Object up() {
        Vector3f u = app.getCamera().getUp();
        Map<String, Object> m = new HashMap<>(3);
        m.put("x", (double) u.x);
        m.put("y", (double) u.y);
        m.put("z", (double) u.z);
        return m;
    }

    @HostAccess.Export
    @Override
    public void moveLocal(double dx, double dy, double dz) {
        var cam = app.getCamera();

        Vector3f forward = cam.getDirection();
        Vector3f up = cam.getUp();
        Vector3f left = cam.getLeft();
        // right = -left
        float fx = forward.x, fy = forward.y, fz = forward.z;
        float ux = up.x, uy = up.y, uz = up.z;
        float rx = -left.x, ry = -left.y, rz = -left.z;

        Vector3f p = cam.getLocation();
        tmpV.set(
                p.x + (float) (rx * dx + ux * dy + fx * dz),
                p.y + (float) (ry * dx + uy * dy + fy * dz),
                p.z + (float) (rz * dx + uz * dy + fz * dz)
        );
        cam.setLocation(tmpV);
    }

    @HostAccess.Export
    @Override
    public void moveWorld(double dx, double dy, double dz) {
        var cam = app.getCamera();
        Vector3f p = cam.getLocation();
        cam.setLocation(tmpV.set(
                p.x + (float) dx,
                p.y + (float) dy,
                p.z + (float) dz
        ));
    }

    @HostAccess.Export
    @Override
    public void rotateYawPitch(double dYaw, double dPitch) {
        double y = yaw() + dYaw;
        double p = pitch() + dPitch;
        setYawPitch(y, p);
    }

    private float clampPitch(float pitch) {
        float limit = state.pitchLimit;
        if (limit <= 0f) return pitch;
        if (pitch > limit) return limit;
        if (pitch < -limit) return -limit;
        return pitch;
    }

    // -------------------------------------------------------------------------
    // Deprecated legacy / compatibility layer
    // -------------------------------------------------------------------------

    @Deprecated
    @HostAccess.Export
    @Override
    public void mode(String mode) {
        String m = (mode == null ? "" : mode.trim());
        if (m.isEmpty()) return;
        this.legacyMode = m;
        this.state.mode = m; // keep old state in sync for legacy systems
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public String mode() {
        return legacyMode;
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public void fly(Value cfg) {
        // Keep as "config bag" only; do NOT implement behavior here.
        this.legacyFlyCfg = cfg;

        if (cfg == null || cfg.isNull()) return;

        // optional: sync common legacy fields
        state.speed = clamp(num(cfg, "speed", state.speed), 0.0, 10_000.0);
        state.accel = clamp(num(cfg, "accel", state.accel), 0.0, 1_000.0);
        state.drag = clamp(num(cfg, "drag", state.drag), 0.0, 1_000.0);
        state.smoothing = clamp(num(cfg, "smoothing", state.smoothing), 0.0, 1.0);
        state.lookSensitivity = clamp(num(cfg, "lookSensitivity", state.lookSensitivity), 0.000001, 10.0);

        state.invertY = bool(cfg, "invertY", state.invertY);
        state.invertX = bool(cfg, "invertX", state.invertX);
        state.invertMoveX = bool(cfg, "invertMoveX", state.invertMoveX);
        state.invertMoveZ = bool(cfg, "invertMoveZ", state.invertMoveZ);
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public void follow(int bodyId) {
        state.followBodyId = Math.max(0, bodyId);
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public void followConfig(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        readVec3Into(cfg, "firstPersonOffset", state.firstPersonOffset);

        state.thirdPersonDistance = (float) clamp(num(cfg, "thirdPersonDistance", state.thirdPersonDistance), 0.1, 500.0);
        state.thirdPersonMinDistance = (float) clamp(num(cfg, "thirdPersonMinDistance", state.thirdPersonMinDistance), 0.1, 500.0);
        state.thirdPersonMaxDistance = (float) clamp(num(cfg, "thirdPersonMaxDistance", state.thirdPersonMaxDistance), 0.1, 2000.0);

        state.thirdPersonHeight = (float) clamp(num(cfg, "thirdPersonHeight", state.thirdPersonHeight), -100.0, 500.0);
        state.thirdPersonSide = (float) clamp(num(cfg, "thirdPersonSide", state.thirdPersonSide), -100.0, 100.0);

        state.zoomSpeed = (float) clamp(num(cfg, "zoomSpeed", state.zoomSpeed), 0.0, 50.0);
        state.followSmoothing = (float) clamp(num(cfg, "followSmoothing", state.followSmoothing), 0.0, 1.0);

        state.thirdPersonCollision = bool(cfg, "thirdPersonCollision", state.thirdPersonCollision);
        state.collisionPadding = (float) clamp(num(cfg, "collisionPadding", state.collisionPadding), 0.0, 5.0);

        state.pitchLimit = (float) clamp(num(cfg, "pitchLimit", state.pitchLimit), 0.0, (double) FastMath.HALF_PI * 0.999);
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public void enabled(boolean enabled) {
        state.enabled = enabled;
    }

    @Deprecated
    @HostAccess.Export
    @Override
    public boolean enabled() {
        return state.enabled;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean bool(Value v, String key, boolean def) {
        try {
            return v != null && v.hasMember(key) ? v.getMember(key).asBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double num(Value v, String key, double def) {
        try {
            return v != null && v.hasMember(key) ? v.getMember(key).asDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static void readVec3Into(Value cfg, String key, Vector3f out) {
        try {
            if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return;
            Value vv = cfg.getMember(key);
            if (vv == null || vv.isNull()) return;

            float x = (float) (vv.hasMember("x") ? vv.getMember("x").asDouble() : out.x);
            float y = (float) (vv.hasMember("y") ? vv.getMember("y").asDouble() : out.y);
            float z = (float) (vv.hasMember("z") ? vv.getMember("z").asDouble() : out.z);
            out.set(x, y, z);
        } catch (Exception ignored) {
        }
    }
}