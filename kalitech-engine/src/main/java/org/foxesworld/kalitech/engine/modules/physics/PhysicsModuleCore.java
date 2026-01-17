package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

import java.util.Objects;

/**
 * Physics module core (engine.modules).
 * <p>
 * Thin facade that composes services:
 * - PhysicsBodies (body lifecycle & per-body ops)
 * - PhysicsRaycasts (ray queries)
 * - PhysicsContacts (collision pipeline & postStep)
 * - PhysicsWorld (world-level ops)
 * <p>
 * NOTE: JS-facing exports live in api.impl.PhysicsApiImpl and delegate here.
 */
public final class PhysicsModuleCore {

    private final PhysicsState S;
    private final PhysicsContacts contacts;
    private final PhysicsBodies bodies;
    private final PhysicsRaycasts raycasts;
    private final PhysicsWorld world;

    public PhysicsModuleCore(EngineApiImpl engine, SimpleApplication app, SurfaceRegistry surfaces) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(surfaces, "surfaces");

        this.S = new PhysicsState(engine, app, surfaces);
        this.contacts = new PhysicsContacts(S);
        this.bodies = new PhysicsBodies(S);
        this.raycasts = new PhysicsRaycasts(S, contacts);
        this.world = new PhysicsWorld(S, contacts);
    }

    public void detach() {
        // best-effort: do not throw on shutdown
        try {
            clearAll();
        } catch (Throwable ignored) {
        }
    }

    // ---------------- bodies ----------------

    public PhysicsBodyHandle body(Object cfg) {
        return bodies.body(cfg, contacts);
    }

    public int bodyOfSurface(int surfaceId) {
        return bodies.bodyOfSurface(surfaceId);
    }

    public PhysicsBodyHandle handle(int bodyId) {
        return bodies.handle(bodyId);
    }

    public boolean exists(int bodyId) {
        return bodies.exists(bodyId);
    }

    public void remove(Object handleOrId) {
        bodies.remove(handleOrId, contacts);
    }

    public Object position(Object handleOrId) {
        return bodies.position(handleOrId);
    }

    public void warp(Object handleOrId, Object vec3) {
        bodies.warp(handleOrId, vec3);
    }

    public Object velocity(Object handleOrId) {
        return bodies.velocity(handleOrId);
    }

    public void velocity(Object handleOrId, Object vec3) {
        bodies.velocity(handleOrId, vec3);
    }

    public void yaw(Object handleOrId, double yaw) {
        bodies.yaw(handleOrId, yaw);
    }

    public void applyImpulse(Object handleOrId, Object vec3) {
        bodies.applyImpulse(handleOrId, vec3);
    }

    public void lockRotation(Object handleOrId, boolean lock) {
        bodies.lockRotation(handleOrId, lock);
    }

    public void setKinematic(Object handleOrId, boolean kinematic) {
        bodies.setKinematic(handleOrId, kinematic);
    }

    public void collisionGroups(Object handleOrId, int group, int mask) {
        bodies.collisionGroups(handleOrId, group, mask);
    }

    public void applyCentralForce(Object handleOrId, Object vec3) {
        bodies.applyCentralForce(handleOrId, vec3);
    }

    public void applyTorque(Object handleOrId, Object vec3) {
        bodies.applyTorque(handleOrId, vec3);
    }

    public Object angularVelocity(Object handleOrId) {
        return bodies.angularVelocity(handleOrId);
    }

    public void angularVelocity(Object handleOrId, Object vec3) {
        bodies.angularVelocity(handleOrId, vec3);
    }

    public void clearForces(Object handleOrId) {
        bodies.clearForces(handleOrId);
    }

    // ---------------- raycasts ----------------

    public PhysicsRayHit raycast(Object cfg) {
        return raycasts.raycast(cfg);
    }

    public Object raycastEx(Object cfg) {
        return raycasts.raycastEx(cfg);
    }

    public Object raycastAll(Object cfg) {
        return raycasts.raycastAll(cfg);
    }

    // ---------------- world ----------------

    public void debug(boolean enabled) {
        world.debug(enabled);
    }

    public void gravity(Object vec3) {
        world.gravity(vec3);
    }

    public void cleanupSurface(int surfaceId) {
        world.cleanupSurface(surfaceId);
    }

    public void clearAll() {
        world.clearAll();
    }
}