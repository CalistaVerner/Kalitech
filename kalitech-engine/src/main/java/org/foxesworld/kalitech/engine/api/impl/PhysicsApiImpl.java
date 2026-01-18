// FILE: org/foxesworld/kalitech/engine/api/impl/PhysicsApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
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
import org.graalvm.polyglot.HostAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Export-only Physics API facade.
 *
 * <p>This class must stay thin: all logic lives inside engine.modules.physics.* modules.
 * Exported methods are stable and only delegate to runtime services.</p>
 */
public final class PhysicsApiImpl extends AbstractApiModule implements PhysicsApi {

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
        attach(new ApiContext(engine));
        this.surfaces = surfaces;
        ensureModulesBound();
    }

    private static <T> T resolve(Class<T> type, Object target, String... names) {
        if (type == null || target == null || names == null) return null;

        for (String n : names) {
            if (n == null || n.isEmpty()) continue;

            // zero-arg method
            try {
                Method m = target.getClass().getMethod(n);
                if (type.isAssignableFrom(m.getReturnType())) {
                    Object v = m.invoke(target);
                    if (v != null) return type.cast(v);
                }
            } catch (Throwable ignored) {
            }

            // public field
            try {
                Field f = target.getClass().getField(n);
                if (type.isAssignableFrom(f.getType())) {
                    Object v = f.get(target);
                    if (v != null) return type.cast(v);
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = resolve(SimpleApplication.class, ctx, "app", "getApp", "application");
        this.surfaces = resolve(SurfaceRegistry.class, ctx, "surfaces", "getSurfaces", "surfaceRegistry", "getSurfaceRegistry");
        if (this.surfaces == null) {
            this.surfaces = resolve(SurfaceRegistry.class, engine, "surfaces", "getSurfaces", "surfaceRegistry", "getSurfaceRegistry");
        }
        if (this.app == null) {
            this.app = resolve(SimpleApplication.class, engine, "app", "getApp", "application", "getApplication");
        }
        ensureModulesBound();
    }

    @Override
    public void detach() {
        try {
            __clearAll();
        } catch (Throwable t) {
            log.warn("[physics] detach cleanup failed", t);
        }

        if (entityResolver != null) {
            try {
                entityResolver.unbind();
            } catch (Throwable ignored) {
            }
        }

        if (svc != null) {
            try {
                svc.unbind();
            } catch (Throwable ignored) {
            }
        }

        this.app = null;
        this.surfaces = null;

        super.detach();
    }

    private void ensureModulesBound() {
        if (svc == null) {
            svc = new PhysicsService(engine, log);
            entityResolver = new PhysicsEntityResolver();
            stateTracker = new PhysicsBodyStateTracker(svc, log);
            collisionPipeline = new PhysicsCollisionPipeline(svc, log);
            collisionPipeline.setBodyStateTracker(stateTracker);
            raycaster = new PhysicsRaycaster(svc);
            bodyOps = new PhysicsBodyOps(svc.registry(), engine.getBus(), entityResolver::entityOfSurface);

            svc.setOnBodyRemoved(stateTracker::onBodyRemoved);
        }

        if (surfaces != null) {
            try {
                entityResolver.bind(surfaces);
            } catch (Throwable t) {
                log.warn("[physics] entity resolver bind failed", t);
            }
        }

        if (app != null && surfaces != null) {
            try {
                svc.bind(app, surfaces);
            } catch (Throwable t) {
                log.warn("[physics] service bind failed", t);
            }
        }
    }

    private PhysicsSpace space() {
        ensureModulesBound();
        PhysicsSpace sp = svc.requireSpace();
        collisionPipeline.bind(sp);
        return sp;
    }

    /**
     * Clears all physics runtime state.
     */
    public void __clearAll() {
        if (svc == null) return;
        try {
            svc.clearAll();
        } finally {
            if (stateTracker != null) stateTracker.clear();
            if (collisionPipeline != null) collisionPipeline.reset();
        }
    }

    // ----------------------------------------------------------------------
    // Export API: debug / gravity
    // ----------------------------------------------------------------------

    public void __cleanupSurface(int surfaceId) {
        if (svc == null || surfaceId <= 0) return;
        int id = svc.registry().bodyOfSurface(surfaceId);
        if (id > 0) svc.removeBodyById(id);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void debug(boolean enabled) {
        BulletAppState b = app != null ? app.getStateManager().getState(BulletAppState.class) : null;
        if (b == null) {
            log.warn("[physics] debug({}) ignored: BulletAppState not attached", enabled);
            return;
        }
        b.setDebugEnabled(enabled);
    }

    // ----------------------------------------------------------------------
    // Export API: handles / lifecycle
    // ----------------------------------------------------------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void gravity(Object vec3) {
        PhysicsSpace sp = space();
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        sp.setGravity(g);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int bodyOfSurface(int surfaceId) {
        ensureModulesBound();
        return svc.registry().bodyOfSurface(surfaceId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public PhysicsBodyHandle handle(int bodyId) {
        ensureModulesBound();
        return svc.registry().get(bodyId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public boolean exists(int bodyId) {
        ensureModulesBound();
        return svc.registry().exists(bodyId);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public PhysicsBodyHandle body(Object cfg) {
        space();
        return svc.createBody(cfg);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void remove(Object handleOrId) {
        ensureModulesBound();
        svc.removeBody(handleOrId);
    }

    // ----------------------------------------------------------------------
    // Export API: body ops
    // ----------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void remove(int id) {
        ensureModulesBound();
        svc.removeBodyById(id);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object position(Object handleOrId) {
        ensureModulesBound();
        return bodyOps.position(handleOrId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object velocity(Object handleOrId) {
        ensureModulesBound();
        return bodyOps.velocity(handleOrId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void velocity(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.velocity(handleOrId, vec3);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void yaw(Object handleOrId, double yaw) {
        ensureModulesBound();
        bodyOps.yaw(handleOrId, yaw);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void applyImpulse(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.applyImpulse(handleOrId, vec3);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void lockRotation(Object handleOrId, boolean lock) {
        ensureModulesBound();
        bodyOps.lockRotation(handleOrId, lock);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setKinematic(Object handleOrId, boolean kinematic) {
        ensureModulesBound();
        bodyOps.setKinematic(handleOrId, kinematic);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void collisionGroups(Object handleOrId, int group, int mask) {
        ensureModulesBound();
        bodyOps.collisionGroups(handleOrId, group, mask);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void applyCentralForce(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.applyCentralForce(handleOrId, vec3);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void applyTorque(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.applyTorque(handleOrId, vec3);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object angularVelocity(Object handleOrId) {
        ensureModulesBound();
        return bodyOps.angularVelocity(handleOrId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void angularVelocity(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.angularVelocity(handleOrId, vec3);
    }

    // ----------------------------------------------------------------------
    // Export API: ray
    // ----------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void clearForces(Object handleOrId) {
        ensureModulesBound();
        bodyOps.clearForces(handleOrId);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void warp(Object handleOrId, Object vec3) {
        ensureModulesBound();
        bodyOps.warp(handleOrId, vec3);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public PhysicsRayHit raycast(Object cfg) {
        space();
        return raycaster.raycast(cfg);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object raycastEx(Object cfg) {
        space();
        return raycaster.raycastEx(cfg);
    }

    // ----------------------------------------------------------------------
    // Reflection helpers (to reduce coupling to ApiContext internals)
    // ----------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object raycastAll(Object cfg) {
        space();
        return raycaster.raycastAll(cfg);
    }
}