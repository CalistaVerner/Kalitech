package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsModuleCore;
import org.graalvm.polyglot.HostAccess;

/**
 * Thin JS-facing Physics API facade.
 *
 * All heavy logic lives in {@link PhysicsModuleCore} (engine.modules).
 */
public final class PhysicsApiImpl extends AbstractApiModule implements PhysicsApi {

    private PhysicsModuleCore core;

    public PhysicsApiImpl() {
        super("physics", "Physics", "1.0.0");
    }

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this();
        if (engine == null) throw new NullPointerException("engine");
        if (surfaces == null) throw new NullPointerException("surfaces");
        super.attach(new ApiContext(engine));
        this.core = new PhysicsModuleCore(engine, engine.getApp(), surfaces);
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        if (ctx == null) throw new NullPointerException("ctx");
        if (ctx.engine == null) throw new IllegalStateException("[physics] ctx.engine is null");
        if (ctx.app == null) throw new IllegalStateException("[physics] ctx.app is null");
        SurfaceRegistry surfaces = ctx.engine.getSurfaceRegistry();
        if (surfaces == null) throw new IllegalStateException("[physics] SurfaceRegistry is not available");
        this.core = new PhysicsModuleCore(ctx.engine, ctx.app, surfaces);
    }

    @Override
    public void detach() {
        PhysicsModuleCore c = this.core;
        this.core = null;
        if (c != null) {
            try {
                c.detach();
            } catch (Throwable ignored) {
            }
        }
        super.detach();
    }

    private PhysicsModuleCore C() {
        PhysicsModuleCore c = this.core;
        if (c == null) throw new IllegalStateException("[physics] module is not attached");
        return c;
    }

    // ===================== JS exports =====================

    @Override
    @HostAccess.Export
    public PhysicsBodyHandle body(Object cfg) {
        return C().body(cfg);
    }

    @Override
    @HostAccess.Export
    public int bodyOfSurface(int surfaceId) {
        return C().bodyOfSurface(surfaceId);
    }

    @Override
    @HostAccess.Export
    public PhysicsBodyHandle handle(int bodyId) {
        return C().handle(bodyId);
    }

    @Override
    @HostAccess.Export
    public boolean exists(int bodyId) {
        return C().exists(bodyId);
    }

    @Override
    @HostAccess.Export
    public void remove(Object handleOrId) {
        C().remove(handleOrId);
    }

    @Override
    @HostAccess.Export
    public PhysicsRayHit raycast(Object cfg) {
        return C().raycast(cfg);
    }

    @Override
    @HostAccess.Export
    public Object raycastEx(Object cfg) {
        return C().raycastEx(cfg);
    }

    @Override
    @HostAccess.Export
    public Object raycastAll(Object cfg) {
        return C().raycastAll(cfg);
    }

    @Override
    @HostAccess.Export
    public Object position(Object handleOrId) {
        return C().position(handleOrId);
    }

    @Override
    @HostAccess.Export
    public void warp(Object handleOrId, Object vec3) {
        C().warp(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public Object velocity(Object handleOrId) {
        return C().velocity(handleOrId);
    }

    @Override
    @HostAccess.Export
    public void velocity(Object handleOrId, Object vec3) {
        C().velocity(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public void yaw(Object handleOrId, double yaw) {
        C().yaw(handleOrId, yaw);
    }

    @Override
    @HostAccess.Export
    public void applyImpulse(Object handleOrId, Object vec3) {
        C().applyImpulse(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public void lockRotation(Object handleOrId, boolean lock) {
        C().lockRotation(handleOrId, lock);
    }

    @Override
    @HostAccess.Export
    public void setKinematic(Object handleOrId, boolean kinematic) {
        C().setKinematic(handleOrId, kinematic);
    }

    @Override
    @HostAccess.Export
    public void collisionGroups(Object handleOrId, int group, int mask) {
        C().collisionGroups(handleOrId, group, mask);
    }

    @Override
    @HostAccess.Export
    public void applyCentralForce(Object handleOrId, Object vec3) {
        C().applyCentralForce(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public void applyTorque(Object handleOrId, Object vec3) {
        C().applyTorque(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public Object angularVelocity(Object handleOrId) {
        return C().angularVelocity(handleOrId);
    }

    @Override
    @HostAccess.Export
    public void angularVelocity(Object handleOrId, Object vec3) {
        C().angularVelocity(handleOrId, vec3);
    }

    @Override
    @HostAccess.Export
    public void clearForces(Object handleOrId) {
        C().clearForces(handleOrId);
    }

    @Override
    @HostAccess.Export
    public void debug(boolean enabled) {
        C().debug(enabled);
    }

    @Override
    @HostAccess.Export
    public void gravity(Object vec3) {
        C().gravity(vec3);
    }

    // ===================== engine internal =====================

    @Override
    public void __cleanupSurface(int surfaceId) {
        C().cleanupSurface(surfaceId);
    }

    @Override
    public void __clearAll() {
        C().clearAll();
    }
}
