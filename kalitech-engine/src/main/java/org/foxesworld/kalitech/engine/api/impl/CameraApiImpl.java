package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.contract.*;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.module.EngineCameraModule;
import org.foxesworld.kalitech.engine.modules.camera.Camera;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;

/**
 * Script-facing camera bridge.
 *
 * <p>Threading:
 * - Mutations are batched and flushed once per frame on the JME thread.
 */
public final class CameraApiImpl extends AbstractApiModule implements CameraApi, EngineCameraModule {

    private static final Method M_LOCATION =
            method(CameraApiImpl.class, "location");

    private static final Method M_SET_LOCATION_VALUE =
            method(CameraApiImpl.class, "setLocation", Value.class);

    private static final Method M_SET_LOCATION_XYZ =
            method(CameraApiImpl.class, "setLocation", double.class, double.class, double.class);

    private static final Method M_SET_YAW_PITCH =
            method(CameraApiImpl.class, "setYawPitch", double.class, double.class);

    private static final Method M_YAW =
            method(CameraApiImpl.class, "yaw");

    private static final Method M_PITCH =
            method(CameraApiImpl.class, "pitch");

    private static final Method M_FORWARD =
            method(CameraApiImpl.class, "forward");

    private static final Method M_RIGHT =
            method(CameraApiImpl.class, "right");

    private static final Method M_UP =
            method(CameraApiImpl.class, "up");

    private static final Method M_MOVE_LOCAL =
            method(CameraApiImpl.class, "moveLocal", double.class, double.class, double.class);

    private static final Method M_MOVE_WORLD =
            method(CameraApiImpl.class, "moveWorld", double.class, double.class, double.class);

    private static final Method M_ROTATE_YAW_PITCH =
            method(CameraApiImpl.class, "rotateYawPitch", double.class, double.class);

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

    /**
     * Engine-internal per-frame flush.
     * Should be called from JME update.
     */
    @Override
    public void flush() {
        Camera c = camera;
        if (c != null) c.flushOncePerFrame();
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public Object location() {
        return profiled(() ->
                apiCall(M_LOCATION, new Object[0], () -> camera.locationView())
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void setLocation(Value v) {
        profiledVoid(() ->
                apiVoid(M_SET_LOCATION_VALUE, new Object[]{v}, () -> {
                    if (v == null || v.isNull()) return;
                    camera.setLocation(num(v, "x", 0.0), num(v, "y", 0.0), num(v, "z", 0.0));
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void setLocation(@Finite double x, @Finite double y, @Finite double z) {
        profiledVoid(() ->
                apiVoid(M_SET_LOCATION_XYZ, new Object[]{x, y, z}, () -> camera.setLocation(x, y, z))
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void setYawPitch(@Finite double yaw, @Finite double pitch) {
        profiledVoid(() ->
                apiVoid(M_SET_YAW_PITCH, new Object[]{yaw, pitch}, () -> camera.setYawPitch(yaw, pitch))
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public double yaw() {
        return profiled(() ->
                apiCall(M_YAW, new Object[0], camera::yaw)
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public double pitch() {
        return profiled(() ->
                apiCall(M_PITCH, new Object[0], camera::pitch)
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public Object forward() {
        return profiled(() ->
                apiCall(M_FORWARD, new Object[0], () -> camera.forwardView())
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public Object right() {
        return profiled(() ->
                apiCall(M_RIGHT, new Object[0], () -> camera.rightView())
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public Object up() {
        return profiled(() ->
                apiCall(M_UP, new Object[0], () -> camera.upView())
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void moveLocal(@Finite double dx, @Finite double dy, @Finite double dz) {
        profiledVoid(() ->
                apiVoid(M_MOVE_LOCAL, new Object[]{dx, dy, dz}, () -> camera.moveLocal(dx, dy, dz))
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void moveWorld(@Finite double dx, @Finite double dy, @Finite double dz) {
        profiledVoid(() ->
                apiVoid(M_MOVE_WORLD, new Object[]{dx, dy, dz}, () -> camera.moveWorld(dx, dy, dz))
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void rotateYawPitch(@Finite double dYaw, @Finite double dPitch) {
        profiledVoid(() ->
                apiVoid(M_ROTATE_YAW_PITCH, new Object[]{dYaw, dPitch}, () -> camera.rotateYawPitch(dYaw, dPitch))
        );
    }
}
