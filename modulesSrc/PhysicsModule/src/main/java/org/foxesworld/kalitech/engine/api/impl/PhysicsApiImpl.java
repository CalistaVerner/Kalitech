/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.math.Vector3f
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsBodyOps;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsBodyStateTracker;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsCollisionPipeline;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsEntityResolver;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsRaycaster;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class PhysicsApiImpl
extends AbstractApiModule
implements PhysicsApi {
    private static final Logger log = LogManager.getLogger(PhysicsApiImpl.class);
    private SimpleApplication app;
    private SurfaceRegistry surfaces;
    private PhysicsService svc;
    private PhysicsEntityResolver entityResolver;
    private PhysicsBodyStateTracker stateTracker;
    private PhysicsCollisionPipeline collisionPipeline;
    private PhysicsRaycaster raycaster;
    private PhysicsBodyOps bodyOps;

    public PhysicsApiImpl() {
        super("physics", "Physics", "1.0.0");
    }

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this();
        this.attach(new ApiContext(engine));
        this.surfaces = surfaces;
        this.ensureModulesBound();
    }

    private static <T> T resolve(Class<T> type, Object target, String ... names) {
        if (type == null || target == null || names == null) {
            return null;
        }
        for (String n : names) {
            Object v;
            if (n == null || n.isEmpty()) continue;
            try {
                Method m = target.getClass().getMethod(n, new Class[0]);
                if (type.isAssignableFrom(m.getReturnType()) && (v = m.invoke(target, new Object[0])) != null) {
                    return type.cast(v);
                }
            }
            catch (Throwable m) {
                // empty catch block
            }
            try {
                Field f = target.getClass().getField(n);
                if (!type.isAssignableFrom(f.getType()) || (v = f.get(target)) == null) continue;
                return type.cast(v);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return null;
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = PhysicsApiImpl.resolve(SimpleApplication.class, ctx, "app", "getApp", "application");
        this.surfaces = PhysicsApiImpl.resolve(SurfaceRegistry.class, ctx, "surfaces", "getSurfaces", "surfaceRegistry", "getSurfaceRegistry");
        if (this.surfaces == null) {
            this.surfaces = PhysicsApiImpl.resolve(SurfaceRegistry.class, this.engine, "surfaces", "getSurfaces", "surfaceRegistry", "getSurfaceRegistry");
        }
        if (this.app == null) {
            this.app = PhysicsApiImpl.resolve(SimpleApplication.class, this.engine, "app", "getApp", "application", "getApplication");
        }
        this.ensureModulesBound();
    }

    public void detach() {
        try {
            this.__clearAll();
        }
        catch (Throwable t) {
            log.warn("[physics] detach cleanup failed", t);
        }
        if (this.entityResolver != null) {
            try {
                this.entityResolver.unbind();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (this.svc != null) {
            try {
                this.svc.unbind();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.app = null;
        this.surfaces = null;
        super.detach();
    }

    private void ensureModulesBound() {
        if (this.svc == null) {
            this.svc = new PhysicsService(this.engine, log);
            this.entityResolver = new PhysicsEntityResolver();
            this.stateTracker = new PhysicsBodyStateTracker(this.svc, log);
            this.collisionPipeline = new PhysicsCollisionPipeline(this.svc, log);
            this.collisionPipeline.setBodyStateTracker(this.stateTracker);
            this.raycaster = new PhysicsRaycaster(this.svc);
            this.bodyOps = new PhysicsBodyOps(this.svc.registry(), this.engine.getBus(), this.entityResolver::entityOfSurface);
            this.svc.setOnBodyRemoved(this.stateTracker::onBodyRemoved);
        }
        if (this.surfaces != null) {
            try {
                this.entityResolver.bind(this.surfaces);
            }
            catch (Throwable t) {
                log.warn("[physics] entity resolver bind failed", t);
            }
        }
        if (this.app != null && this.surfaces != null) {
            try {
                this.svc.bind(this.app, this.surfaces);
            }
            catch (Throwable t) {
                log.warn("[physics] service bind failed", t);
            }
        }
    }

    private PhysicsSpace space() {
        this.ensureModulesBound();
        PhysicsSpace sp = this.svc.requireSpace();
        this.collisionPipeline.bind(sp);
        return sp;
    }

    @Override
    public void __clearAll() {
        if (this.svc == null) {
            return;
        }
        try {
            this.svc.clearAll();
        }
        finally {
            if (this.stateTracker != null) {
                this.stateTracker.clear();
            }
            if (this.collisionPipeline != null) {
                this.collisionPipeline.reset();
            }
        }
    }

    @Override
    public void __cleanupSurface(int surfaceId) {
        if (this.svc == null || surfaceId <= 0) {
            return;
        }
        int id = this.svc.registry().bodyOfSurface(surfaceId);
        if (id > 0) {
            this.svc.removeBodyById(id);
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void debug(boolean enabled) {
        BulletAppState b;
        BulletAppState bulletAppState = b = this.app != null ? (BulletAppState)this.app.getStateManager().getState(BulletAppState.class) : null;
        if (b == null) {
            log.warn("[physics] debug({}) ignored: BulletAppState not attached", (Object)enabled);
            return;
        }
        b.setDebugEnabled(enabled);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void gravity(Object vec3) {
        PhysicsSpace sp = this.space();
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0.0f, -9.81f, 0.0f);
        sp.setGravity(g);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public int bodyOfSurface(int surfaceId) {
        this.ensureModulesBound();
        return this.svc.registry().bodyOfSurface(surfaceId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public PhysicsBodyHandle handle(int bodyId) {
        this.ensureModulesBound();
        return this.svc.registry().get(bodyId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean exists(int bodyId) {
        this.ensureModulesBound();
        return this.svc.registry().exists(bodyId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public PhysicsBodyHandle body(Object cfg) {
        this.space();
        return this.svc.createBody(cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void remove(Object handleOrId) {
        this.ensureModulesBound();
        this.svc.removeBody(handleOrId);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void remove(int id) {
        this.ensureModulesBound();
        this.svc.removeBodyById(id);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object position(Object handleOrId) {
        this.ensureModulesBound();
        return this.bodyOps.position(handleOrId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object velocity(Object handleOrId) {
        this.ensureModulesBound();
        return this.bodyOps.velocity(handleOrId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void velocity(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.velocity(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void yaw(Object handleOrId, double yaw) {
        this.ensureModulesBound();
        this.bodyOps.yaw(handleOrId, yaw);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void applyImpulse(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.applyImpulse(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void lockRotation(Object handleOrId, boolean lock) {
        this.ensureModulesBound();
        this.bodyOps.lockRotation(handleOrId, lock);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setKinematic(Object handleOrId, boolean kinematic) {
        this.ensureModulesBound();
        this.bodyOps.setKinematic(handleOrId, kinematic);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void collisionGroups(Object handleOrId, int group, int mask) {
        this.ensureModulesBound();
        this.bodyOps.collisionGroups(handleOrId, group, mask);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void applyCentralForce(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.applyCentralForce(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void applyTorque(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.applyTorque(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object angularVelocity(Object handleOrId) {
        this.ensureModulesBound();
        return this.bodyOps.angularVelocity(handleOrId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void angularVelocity(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.angularVelocity(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void clearForces(Object handleOrId) {
        this.ensureModulesBound();
        this.bodyOps.clearForces(handleOrId);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void warp(Object handleOrId, Object vec3) {
        this.ensureModulesBound();
        this.bodyOps.warp(handleOrId, vec3);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public PhysicsRayHit raycast(Object cfg) {
        this.space();
        return this.raycaster.raycast(cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object raycastEx(Object cfg) {
        this.space();
        return this.raycaster.raycastEx(cfg);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object raycastAll(Object cfg) {
        this.space();
        return this.raycaster.raycastAll(cfg);
    }
}

