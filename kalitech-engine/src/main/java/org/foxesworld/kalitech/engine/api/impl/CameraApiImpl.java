package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.modules.camera.Camera;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Thin bridge to batched camera for scripts.
 *
 * Threading:
 *  - flush is called from EngineApiImpl.__updateTime() on JME thread.
 */
public final class CameraApiImpl extends AbstractApiModule implements CameraApi {

    private Camera orch;

    public CameraApiImpl() {
        super("camera", "Camera", "1.0.0");
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);
        this.orch = new Camera(ctx.engine);
    }

    /** Internal: called once per frame by EngineApiImpl.__updateTime() on JME thread. */
    public void __flush() {
        if (orch != null) orch.flushOncePerFrame();
    }

    @HostAccess.Export
    @Override
    public Object location() {
        return profiled(() -> orch.locationView());
    }

    @HostAccess.Export
    @Override
    public void setLocation(Value v) {
        profiledVoid(() -> {
            if (v == null || v.isNull()) return;

            double x = num(v, "x", 0.0);
            double y = num(v, "y", 0.0);
            double z = num(v, "z", 0.0);

            orch.setLocation(x, y, z);
        });
    }

    @HostAccess.Export
    @Override
    public void setLocation(double x, double y, double z) {
        profiledVoid(() -> orch.setLocation(x, y, z));
    }

    @HostAccess.Export
    @Override
    public void setYawPitch(double yaw, double pitch) {
        profiledVoid(() -> orch.setYawPitch(yaw, pitch));
    }

    @HostAccess.Export
    @Override
    public double yaw() {
        return profiled(orch::yaw);
    }

    @HostAccess.Export
    @Override
    public double pitch() {
        return profiled(orch::pitch);
    }

    @HostAccess.Export
    @Override
    public Object forward() {
        return profiled(() -> orch.forwardView());
    }

    @HostAccess.Export
    @Override
    public Object right() {
        return profiled(() -> orch.rightView());
    }

    @HostAccess.Export
    @Override
    public Object up() {
        return profiled(() -> orch.upView());
    }

    @HostAccess.Export
    @Override
    public void moveLocal(double dx, double dy, double dz) {
        profiledVoid(() -> orch.moveLocal(dx, dy, dz));
    }

    @HostAccess.Export
    @Override
    public void moveWorld(double dx, double dy, double dz) {
        profiledVoid(() -> orch.moveWorld(dx, dy, dz));
    }

    @HostAccess.Export
    @Override
    public void rotateYawPitch(double dYaw, double dPitch) {
        profiledVoid(() -> orch.rotateYawPitch(dYaw, dPitch));
    }

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