package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.camera.Camera;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * Thin bridge to batched camera orchestrator for scripts.
 * All heavy logic is moved into impl.camera.* (orchestrator + helpers).
 */
public final class CameraApiImpl implements CameraApi {

    private static final Logger log = LogManager.getLogger(CameraApiImpl.class);

    private final EngineApiImpl engine;
    private final CameraState state;
    private final Camera orch;

    // legacy storage
    private volatile String legacyMode = "free";
    private volatile Value legacyFlyCfg = null;

    public CameraApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.state = engine.getCameraState();
        this.orch = new Camera(engine, state);
    }

    /** Internal: called once per frame by EngineApiImpl.__updateTime() on JME thread. */
    public void __flush() {
        orch.flushOncePerFrame();
    }

    // -------------------------------------------------------------------------
    // Core transform primitives (JS)
    // -------------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public Object location() {
        return orch.locationView();
    }

    @HostAccess.Export
    @Override
    public void setLocation(Value v) {
        if (v == null || v.isNull()) return;
        double x = num(v, "x", 0.0);
        double y = num(v, "y", 0.0);
        double z = num(v, "z", 0.0);
        orch.setLocation(x, y, z);
    }

    @HostAccess.Export
    @Override
    public void setLocation(double x, double y, double z) {
        orch.setLocation(x, y, z);
    }

    @HostAccess.Export
    @Override
    public void setYawPitch(double yaw, double pitch) {
        orch.setYawPitch(yaw, pitch);
    }

    @HostAccess.Export
    @Override
    public double yaw() {
        return orch.yaw();
    }

    @HostAccess.Export
    @Override
    public double pitch() {
        return orch.pitch();
    }

    @HostAccess.Export
    @Override
    public Object forward() {
        return orch.forwardView();
    }

    @HostAccess.Export
    @Override
    public Object right() {
        return orch.rightView();
    }

    @HostAccess.Export
    @Override
    public Object up() {
        return orch.upView();
    }

    @HostAccess.Export
    @Override
    public void moveLocal(double dx, double dy, double dz) {
        orch.moveLocal(dx, dy, dz);
    }

    @HostAccess.Export
    @Override
    public void moveWorld(double dx, double dy, double dz) {
        orch.moveWorld(dx, dy, dz);
    }

    @HostAccess.Export
    @Override
    public void rotateYawPitch(double dYaw, double dPitch) {
        orch.rotateYawPitch(dYaw, dPitch);
    }

    // -------------------------------------------------------------------------
    // Deprecated legacy / compatibility layer (kept in this class)
    // -------------------------------------------------------------------------

    @Deprecated
    @HostAccess.Export
    @Override
    public void mode(String mode) {
        String m = (mode == null ? "" : mode.trim());
        if (m.isEmpty()) return;
        this.legacyMode = m;
        this.state.mode = m;
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
        this.legacyFlyCfg = cfg;
        if (cfg == null || cfg.isNull()) return;

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

        state.pitchLimit = (float) clamp(num(cfg, "pitchLimit", state.pitchLimit), 0.0, Math.PI * 0.499);
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
    // Helpers (unchanged)
    // -------------------------------------------------------------------------

    private static boolean bool(Value v, String key, boolean def) {
        try { return v != null && v.hasMember(key) ? v.getMember(key).asBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static double num(Value v, String key, double def) {
        try { return v != null && v.hasMember(key) ? v.getMember(key).asDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static void readVec3Into(Value cfg, String key, com.jme3.math.Vector3f out) {
        try {
            if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return;
            Value vv = cfg.getMember(key);
            if (vv == null || vv.isNull()) return;

            float x = (float) (vv.hasMember("x") ? vv.getMember("x").asDouble() : out.x);
            float y = (float) (vv.hasMember("y") ? vv.getMember("y").asDouble() : out.y);
            float z = (float) (vv.hasMember("z") ? vv.getMember("z").asDouble() : out.z);
            out.set(x, y, z);
        } catch (Exception ignored) {}
    }
}