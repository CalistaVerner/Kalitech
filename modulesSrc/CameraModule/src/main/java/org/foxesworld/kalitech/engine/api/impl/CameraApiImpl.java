/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.contract.Finite
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.impl;

import java.lang.reflect.Method;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.contract.Finite;
import org.foxesworld.kalitech.engine.api.interfaces.CameraApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.camera.Camera;
import org.foxesworld.kalitech.engine.modules.camera.Vec3View;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class CameraApiImpl
extends AbstractApiModule
implements CameraApi {
    private static final Method M_LOCATION = CameraApiImpl.method(CameraApiImpl.class, (String)"location", (Class[])new Class[0]);
    private static final Method M_SET_LOCATION_VALUE = CameraApiImpl.method(CameraApiImpl.class, (String)"setLocation", (Class[])new Class[]{LuaValueRef.class});
    private static final Method M_SET_LOCATION_XYZ = CameraApiImpl.method(CameraApiImpl.class, (String)"setLocation", (Class[])new Class[]{Double.TYPE, Double.TYPE, Double.TYPE});
    private static final Method M_SET_YAW_PITCH = CameraApiImpl.method(CameraApiImpl.class, (String)"setYawPitch", (Class[])new Class[]{Double.TYPE, Double.TYPE});
    private static final Method M_YAW = CameraApiImpl.method(CameraApiImpl.class, (String)"yaw", (Class[])new Class[0]);
    private static final Method M_PITCH = CameraApiImpl.method(CameraApiImpl.class, (String)"pitch", (Class[])new Class[0]);
    private static final Method M_FORWARD = CameraApiImpl.method(CameraApiImpl.class, (String)"forward", (Class[])new Class[0]);
    private static final Method M_RIGHT = CameraApiImpl.method(CameraApiImpl.class, (String)"right", (Class[])new Class[0]);
    private static final Method M_UP = CameraApiImpl.method(CameraApiImpl.class, (String)"up", (Class[])new Class[0]);
    private static final Method M_MOVE_LOCAL = CameraApiImpl.method(CameraApiImpl.class, (String)"moveLocal", (Class[])new Class[]{Double.TYPE, Double.TYPE, Double.TYPE});
    private static final Method M_MOVE_WORLD = CameraApiImpl.method(CameraApiImpl.class, (String)"moveWorld", (Class[])new Class[]{Double.TYPE, Double.TYPE, Double.TYPE});
    private static final Method M_ROTATE_YAW_PITCH = CameraApiImpl.method(CameraApiImpl.class, (String)"rotateYawPitch", (Class[])new Class[]{Double.TYPE, Double.TYPE});
    private Camera camera;

    public CameraApiImpl() {
        super("camera", "Camera", "1.0.0");
    }

    private static double num(LuaValueRef v, String key, double def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) {
                return def;
            }
            LuaValueRef m = v.getMember(key);
            if (m == null || m.isNull()) {
                return def;
            }
            return m.asDouble();
        }
        catch (Throwable t) {
            return def;
        }
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.camera = new Camera(ctx.engine);
    }

    public void detach() {
        this.camera = null;
        super.detach();
    }

    public void __flush() {
        Camera c = this.camera;
        if (c != null) {
            c.flushOncePerFrame();
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public Object location() {
        return this.profiled(() -> (Vec3View)this.apiCall(M_LOCATION, new Object[0], () -> this.camera.locationView()));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void setLocation(LuaValueRef v) {
        this.profiledVoid(() -> this.apiVoid(M_SET_LOCATION_VALUE, new Object[]{v}, () -> {
            if (v == null || v.isNull()) {
                return;
            }
            this.camera.setLocation(CameraApiImpl.num(v, "x", 0.0), CameraApiImpl.num(v, "y", 0.0), CameraApiImpl.num(v, "z", 0.0));
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void setLocation(@Finite double x, @Finite double y, @Finite double z) {
        this.profiledVoid(() -> this.apiVoid(M_SET_LOCATION_XYZ, new Object[]{x, y, z}, () -> this.camera.setLocation(x, y, z)));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void setYawPitch(@Finite double yaw, @Finite double pitch) {
        this.profiledVoid(() -> this.apiVoid(M_SET_YAW_PITCH, new Object[]{yaw, pitch}, () -> this.camera.setYawPitch(yaw, pitch)));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public double yaw() {
        return (Double)this.profiled(() -> (Double)this.apiCall(M_YAW, new Object[0], this.camera::yaw));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public double pitch() {
        return (Double)this.profiled(() -> (Double)this.apiCall(M_PITCH, new Object[0], this.camera::pitch));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public Object forward() {
        return this.profiled(() -> (Vec3View)this.apiCall(M_FORWARD, new Object[0], () -> this.camera.forwardView()));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public Object right() {
        return this.profiled(() -> (Vec3View)this.apiCall(M_RIGHT, new Object[0], () -> this.camera.rightView()));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public Object up() {
        return this.profiled(() -> (Vec3View)this.apiCall(M_UP, new Object[0], () -> this.camera.upView()));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void moveLocal(@Finite double dx, @Finite double dy, @Finite double dz) {
        this.profiledVoid(() -> this.apiVoid(M_MOVE_LOCAL, new Object[]{dx, dy, dz}, () -> this.camera.moveLocal(dx, dy, dz)));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void moveWorld(@Finite double dx, @Finite double dy, @Finite double dz) {
        this.profiledVoid(() -> this.apiVoid(M_MOVE_WORLD, new Object[]{dx, dy, dz}, () -> this.camera.moveWorld(dx, dy, dz)));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.CHEAP)
    public void rotateYawPitch(@Finite double dYaw, @Finite double dPitch) {
        this.profiledVoid(() -> this.apiVoid(M_ROTATE_YAW_PITCH, new Object[]{dYaw, dPitch}, () -> this.camera.rotateYawPitch(dYaw, dPitch)));
    }
}

