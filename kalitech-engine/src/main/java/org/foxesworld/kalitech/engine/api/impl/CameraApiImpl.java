// FILE: org/foxesworld/kalitech/engine/api/impl/CameraApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Map;

public final class CameraApiImpl implements CameraApi {

    private static final Logger log = LogManager.getLogger(CameraApiImpl.class);

    private final SimpleApplication app;
    private final CameraState state;

    public CameraApiImpl(EngineApiImpl engineApi) {
        this.app = engineApi.getApp();
        this.state = engineApi.getCameraState();
    }

    // ---------------- mode / enabled ----------------

    @HostAccess.Export
    @Override
    public void mode(String mode) {
        String m = (mode == null ? "" : mode.trim());
        if (m.isEmpty()) m = "free";
        state.mode = m;
        log.info("Camera mode set: {}", m);
    }

    @HostAccess.Export
    @Override
    public String mode() {
        return state.mode;
    }

    @HostAccess.Export
    @Override
    public void enabled(boolean on) {
        state.enabled = on;
    }

    @HostAccess.Export
    @Override
    public boolean enabled() {
        return state.enabled;
    }

    // ---------------- fly config ----------------

    @HostAccess.Export
    @Override
    public void fly(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        state.enabled = bool(cfg, "enabled", state.enabled);

        // allow speed = 0 (for follow modes)
        state.speed = clamp(num(cfg, "speed", state.speed), 0.0, 10_000.0);

        state.accel = clamp(num(cfg, "accel", state.accel), 0.0, 1_000.0);
        state.drag = clamp(num(cfg, "drag", state.drag), 0.0, 1_000.0);
        state.smoothing = clamp(num(cfg, "smoothing", state.smoothing), 0.0, 1.0);
        state.lookSensitivity = clamp(num(cfg, "lookSensitivity", state.lookSensitivity), 0.001, 10.0);
        state.invertY = bool(cfg, "invertY", state.invertY);
        state.invertX = bool(cfg, "invertX", state.invertX);
        state.invertMoveX = bool(cfg, "invertMoveX", state.invertMoveX);
        state.invertMoveZ = bool(cfg, "invertMoveZ", state.invertMoveZ);

        log.info("Fly cfg applied: enabled={} speed={} accel={} drag={} smoothing={} sens={} invertY={} invertX={}",
                state.enabled, state.speed, state.accel, state.drag, state.smoothing, state.lookSensitivity, state.invertY, state.invertX);
    }

    // ---------------- follow API ----------------

    @HostAccess.Export
    @Override
    public void followBody(int bodyId) {
        state.followBodyId = Math.max(0, bodyId);
        log.info("Camera follow body set: {}", state.followBodyId);
    }

    @HostAccess.Export
    @Override
    public void clearFollow() {
        state.followBodyId = 0;
        log.info("Camera follow cleared");
    }

    @HostAccess.Export
    @Override
    public void followConfig(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        readVec3Into(cfg, "firstPersonOffset", state.firstPersonOffset);

        state.thirdPersonDistance = (float) clamp(num(cfg, "thirdPersonDistance", state.thirdPersonDistance), 0.1, 100.0);
        state.thirdPersonMinDistance = (float) clamp(num(cfg, "thirdPersonMinDistance", state.thirdPersonMinDistance), 0.1, 100.0);
        state.thirdPersonMaxDistance = (float) clamp(num(cfg, "thirdPersonMaxDistance", state.thirdPersonMaxDistance), 0.1, 200.0);

        state.thirdPersonHeight = (float) clamp(num(cfg, "thirdPersonHeight", state.thirdPersonHeight), -10.0, 50.0);
        state.thirdPersonSide = (float) clamp(num(cfg, "thirdPersonSide", state.thirdPersonSide), -10.0, 10.0);

        state.followSmoothing = (float) clamp(num(cfg, "followSmoothing", state.followSmoothing), 0.0, 1.0);
        state.zoomSpeed = (float) clamp(num(cfg, "zoomSpeed", state.zoomSpeed), 0.01, 25.0);

        state.thirdPersonCollision = bool(cfg, "thirdPersonCollision", state.thirdPersonCollision);
        state.collisionPadding = (float) clamp(num(cfg, "collisionPadding", state.collisionPadding), 0.0, 2.0);

        log.info("Camera follow cfg applied");
    }

    // ---------------- optional direct control ----------------

    @HostAccess.Export
    @Override
    public void setLocation(Value vec3) {
        Vector3f p = readVec3(vec3);
        if (p == null) return;
        app.getCamera().setLocation(p);
    }

    @HostAccess.Export
    @Override
    public Object location() {
        Vector3f p = app.getCamera().getLocation();
        // return plain map to JS: {x,y,z}
        return Map.of("x", (double) p.x, "y", (double) p.y, "z", (double) p.z);
    }

    @HostAccess.Export
    @Override
    public void setYawPitch(double yaw, double pitch) {
        Quaternion q = new Quaternion().fromAngles((float) pitch, (float) yaw, 0f);
        app.getCamera().setRotation(q);
    }

    @HostAccess.Export
    @Override
    public double yaw() {
        Vector3f d = app.getCamera().getDirection();
        return Math.atan2(d.x, d.z);
    }

    @HostAccess.Export
    @Override
    public double pitch() {
        Vector3f d = app.getCamera().getDirection().normalize();
        return Math.asin(d.y);
    }

    @HostAccess.Export
    @Override
    public Object forward() {
        Vector3f d = app.getCamera().getDirection();
        Vector3f f = new Vector3f(d.x, 0f, d.z);
        if (f.lengthSquared() < 1e-6f) f.set(0, 0, 1);
        f.normalizeLocal();
        return Map.of("x", (double) f.x, "y", (double) f.y, "z", (double) f.z);
    }

    // ---------------- helpers ----------------

    private static boolean bool(Value v, String k, boolean def) {
        try { return v != null && v.hasMember(k) ? v.getMember(k).asBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static double num(Value v, String k, double def) {
        try { return v != null && v.hasMember(k) ? v.getMember(k).asDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static Vector3f readVec3(Value v) {
        if (v == null || v.isNull()) return null;
        try {
            double x = v.hasMember("x") ? v.getMember("x").asDouble() : 0.0;
            double y = v.hasMember("y") ? v.getMember("y").asDouble() : 0.0;
            double z = v.hasMember("z") ? v.getMember("z").asDouble() : 0.0;
            return new Vector3f((float) x, (float) y, (float) z);
        } catch (Exception e) {
            return null;
        }
    }

    private static void readVec3Into(Value root, String key, Vector3f out) {
        try {
            if (root == null || !root.hasMember(key)) return;
            Value v = root.getMember(key);
            if (v == null || v.isNull()) return;

            float x = (float) (v.hasMember("x") ? v.getMember("x").asDouble() : out.x);
            float y = (float) (v.hasMember("y") ? v.getMember("y").asDouble() : out.y);
            float z = (float) (v.hasMember("z") ? v.getMember("z").asDouble() : out.z);

            out.set(x, y, z);
        } catch (Exception ignored) {}
    }
}