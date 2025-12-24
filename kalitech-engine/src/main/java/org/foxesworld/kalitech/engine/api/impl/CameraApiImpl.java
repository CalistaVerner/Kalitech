package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

public final class CameraApiImpl implements CameraApi {

    private static final Logger log = LogManager.getLogger(CameraApiImpl.class);

    @SuppressWarnings("unused")
    private final SimpleApplication app; // reserved for future camera ops
    private final CameraState state;

    public CameraApiImpl(EngineApiImpl engineApi) {
        this.app = engineApi.getApp();
        this.state = engineApi.getCameraState();
    }

    @HostAccess.Export
    @Override
    public void mode(String mode) {
        String m = (mode == null ? "" : mode.trim().toLowerCase());
        if (m.isEmpty()) m = "off";
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

    @HostAccess.Export
    @Override
    public void fly(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        state.enabled = bool(cfg, "enabled", state.enabled);
        state.speed = clamp(num(cfg, "speed", state.speed), 0.1, 10_000.0);
        state.accel = clamp(num(cfg, "accel", state.accel), 0.0, 1_000.0);
        state.drag = clamp(num(cfg, "drag", state.drag), 0.0, 1_000.0);
        state.smoothing = clamp(num(cfg, "smoothing", state.smoothing), 0.0, 1.0);
        state.lookSensitivity = clamp(num(cfg, "lookSensitivity", state.lookSensitivity), 0.001, 10.0);
        state.invertY = bool(cfg, "invertY", state.invertY);
        state.invertX = bool(cfg, "invertX", state.invertX); // ✅
        state.invertMoveX = bool(cfg, "invertMoveX", state.invertMoveX);
        state.invertMoveZ = bool(cfg, "invertMoveZ", state.invertMoveZ);


        if (!"fly".equals(state.mode)) state.mode = "fly";

        log.info("Fly cfg applied: enabled={} speed={} accel={} drag={} smoothing={} sens={} invertY={}",
                state.enabled, state.speed, state.accel, state.drag, state.smoothing, state.lookSensitivity, state.invertY);
    }

    private static boolean bool(Value v, String k, boolean def) {
        try { return v.hasMember(k) ? v.getMember(k).asBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static double num(Value v, String k, double def) {
        try { return v.hasMember(k) ? v.getMember(k).asDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}