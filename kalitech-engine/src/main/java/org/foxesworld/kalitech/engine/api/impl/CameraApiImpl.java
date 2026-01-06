package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.foxesworld.kalitech.engine.modules.camera.Camera;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * Thin bridge to batched camera orchestrator for scripts.
 *
 * Clean API (NO legacy/deprecated layer):
 *  - core transform primitives only
 *  - all heavy logic lives in modules.camera.Camera (orchestrator)
 *
 * Threading:
 *  - orchestrator is expected to be safe under the engine's call pattern (usually main/JME thread)
 *  - per-frame flush is called from EngineApiImpl.__updateTime() on JME thread.
 */
public final class CameraApiImpl implements CameraApi {

    private static final Logger log = LogManager.getLogger(CameraApiImpl.class);

    private final EngineApiImpl engine;
    private final Camera orch;

    public CameraApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.orch = new Camera(engine);
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
    // Helpers
    // -------------------------------------------------------------------------

    private static double num(Value v, String key, double def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return def;
            Value m = v.getMember(key);
            if (m == null || m.isNull()) return def;
            return m.asDouble();
        } catch (Throwable ignored) {
            return def;
        }
    }
}