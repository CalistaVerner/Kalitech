package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.camera.Camera;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * Script-facing camera bridge.
 *
 * <p>Threading:
 * - Mutations are batched and flushed once per frame on the JME thread.
 */
public final class CameraApiImpl extends AbstractApiModule implements CameraApi {

    private Camera camera;

    public CameraApiImpl() {
        super("camera", "Camera", "1.0.0");
    }

    private static double num(Value v, String key, double def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return def;
            Value m = v.getMember(key);
            if (m == null || m.isNull()) return def;
            return m.asDouble();
        } catch (Throwable t) {
            return def;
        }
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.camera = new Camera(ctx.engine);
    }

    @Override
    public void detach() {
        this.camera = null;
        super.detach();
    }

    public void __flush() {
        Camera c = camera;
        if (c != null) c.flushOncePerFrame();
    }

    @HostAccess.Export
    @Override
    public Object location() {
        return profiled(() -> camera.locationView());
    }

    @HostAccess.Export
    @Override
    public void setLocation(Value v) {
        profiledVoid(() -> {
            if (v == null || v.isNull()) return;
            camera.setLocation(num(v, "x", 0.0), num(v, "y", 0.0), num(v, "z", 0.0));
        });
    }

    @HostAccess.Export
    @Override
    public void setLocation(double x, double y, double z) {
        profiledVoid(() -> camera.setLocation(x, y, z));
    }

    @HostAccess.Export
    @Override
    public void setYawPitch(double yaw, double pitch) {
        profiledVoid(() -> camera.setYawPitch(yaw, pitch));
    }

    @HostAccess.Export
    @Override
    public double yaw() {
        return profiled(camera::yaw);
    }

    @HostAccess.Export
    @Override
    public double pitch() {
        return profiled(camera::pitch);
    }

    @HostAccess.Export
    @Override
    public Object forward() {
        return profiled(() -> camera.forwardView());
    }

    @HostAccess.Export
    @Override
    public Object right() {
        return profiled(() -> camera.rightView());
    }

    @HostAccess.Export
    @Override
    public Object up() {
        return profiled(() -> camera.upView());
    }

    @HostAccess.Export
    @Override
    public void moveLocal(double dx, double dy, double dz) {
        profiledVoid(() -> camera.moveLocal(dx, dy, dz));
    }

    @HostAccess.Export
    @Override
    public void moveWorld(double dx, double dy, double dz) {
        profiledVoid(() -> camera.moveWorld(dx, dy, dz));
    }

    @HostAccess.Export
    @Override
    public void rotateYawPitch(double dYaw, double dPitch) {
        profiledVoid(() -> camera.rotateYawPitch(dYaw, dPitch));
    }
}