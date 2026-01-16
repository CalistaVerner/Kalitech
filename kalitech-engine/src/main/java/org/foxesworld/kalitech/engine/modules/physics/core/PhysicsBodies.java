// FILE: org/foxesworld/kalitech/engine/modules/physics/internal/PhysicsBodies.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.BodyStateStore;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionObjectUtil;
import org.foxesworld.kalitech.engine.modules.physics.collision.PhysicsColliderFactory;
import org.foxesworld.kalitech.engine.modules.physics.shapes.PhysicsShapes;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.modules.physics.js.PhysicsJs.jsVec3;

public final class PhysicsBodies {

    private static final float MOVE_POS_EPS = 0.0025f;
    private static final float MOVE_ROT_EPS = 0.0010f;
    private static final float MOVE_VEL_EPS = 0.01f;
    private static final int MOVE_EVENT_MAX_PER_STEP = 512;

    private final EngineApiImpl engine;
    private final Logger log;

    private final PhysicsRegistry registry;
    private final PhysicsWorld world;
    private final PhysicsEmitter emitter;
    private final BodyStateStore bodyState = new BodyStateStore(2048);
    private final PhysicsShapes shapes;
    private volatile SurfaceRegistry surfaces;
    private volatile boolean dbg = false;

    public PhysicsBodies(
            EngineApiImpl engine,
            Logger log,
            PhysicsRegistry registry,
            PhysicsWorld world,
            PhysicsEmitter emitter,
            SurfaceRegistry surfaces,
            PhysicsShapes shapes
    ) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = Objects.requireNonNull(log, "log");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.world = Objects.requireNonNull(world, "world");
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
        this.shapes = Objects.requireNonNull(shapes, "shapes");
    }

    private static boolean safeActive(RigidBodyControl rb) {
        try {
            return rb.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void tryActivate(RigidBodyControl rb) {
        if (rb == null) return;
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    private static int resolveIdFromValue(Value v, String... members) {
        if (v == null || members == null) return 0;
        try {
            if (v.isNumber()) return v.asInt();
        } catch (Throwable ignored) {
        }
        for (String m : members) {
            int r = getIntMember(v, m);
            if (r > 0) return r;
        }
        return 0;
    }

    private static int getIntMember(Value v, String member) {
        if (v == null || member == null) return 0;
        try {
            if (!v.hasMember(member)) return 0;
            Value m = v.getMember(member);
            if (m == null) return 0;
            if (m.isNumber()) return m.asInt();
            if (m.canExecute()) {
                Value r = m.execute();
                if (r != null && r.isNumber()) return r.asInt();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void validateColliderTypeForMass(Object colliderCfg, boolean dynamic) {
        if (!dynamic) return;

        if (colliderCfg instanceof Value v && v.hasMembers() && v.hasMember("type")) {
            String t = String.valueOf(v.getMember("type"));
            if ("mesh".equalsIgnoreCase(t)) {
                throw new IllegalArgumentException(
                        "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                "Use collider.type='dynamicMesh' or primitive collider."
                );
            }
            return;
        }

        if (colliderCfg instanceof Map<?, ?> m) {
            Object tObj = m.get("type");
            String t = (tObj != null) ? String.valueOf(tObj) : "";
            if ("mesh".equalsIgnoreCase(t)) {
                throw new IllegalArgumentException(
                        "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                "Use collider.type='dynamicMesh' or primitive collider."
                );
            }
        }
    }

    public void attach(SurfaceRegistry surfaces) {
        this.surfaces = surfaces;
    }

    public void setDebug(boolean dbg) {
        this.dbg = dbg;
    }

    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return jsVec3(p);
    }

    public Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return jsVec3(v);
    }

    public void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setLinearVelocity(v);
        tryActivate(h.__raw());
    }

    public void yaw(Object handleOrId, double yaw) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.yaw(yaw)");
        RigidBodyControl rb = h.__raw();

        Quaternion q = new Quaternion();
        q.fromAngles(0f, (float) yaw, 0f);

        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);
        tryActivate(rb);
    }

    public void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
        tryActivate(h.__raw());
    }

    public void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1f);
        }
        tryActivate(rb);
    }

    public void setKinematic(Object handleOrId, boolean kinematic) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.setKinematic(kinematic)");
        RigidBodyControl rb = h.__raw();
        rb.setKinematic(kinematic);
        tryActivate(rb);
    }

    public void collisionGroups(Object handleOrId, int group, int mask) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.collisionGroups(group,mask)");
        RigidBodyControl rb = h.__raw();
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);
        tryActivate(rb);
    }

    public void applyCentralForce(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.applyCentralForce(force)");
        Vector3f f = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyCentralForce(f);
        tryActivate(h.__raw());
    }

    public void applyTorque(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.applyTorque(torque)");
        Vector3f t = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyTorque(t);
        tryActivate(h.__raw());
    }

    public Object angularVelocity(Object handleOrId) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.angularVelocity()");
        Vector3f v = h.__raw().getAngularVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    public void angularVelocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.angularVelocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setAngularVelocity(v);
        tryActivate(h.__raw());
    }

    public void clearForces(Object handleOrId) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.clearForces()");
        RigidBodyControl rb = h.__raw();
        rb.clearForces();
        rb.setAngularVelocity(Vector3f.ZERO);
        rb.setLinearVelocity(Vector3f.ZERO);
        tryActivate(rb);
    }

    public void gravity(Object vec3) {
        PhysicsSpace space = engine.__getPhysicsSpaceOrNull();
        if (space == null) throw new IllegalStateException("[physics] PhysicsSpace not bound");
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        space.setGravity(g);
    }

    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = registry.requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
        tryActivate(rb);
        emitter.emitTeleport(h, p);
    }

    public PhysicsBodyHandle body(Object cfg) {
        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        PhysicsBodyHandle existing = registry.getExistingBySurface(surfaceId);
        if (existing != null) return existing;

        Spatial spatial = surfaces.get(surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + surfaceId);

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        boolean dynamic = mass > 0f;

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        CollisionShape shape;
        if (colliderCfg == null) {
            shape = shapes.defaultShapeForSpatial(spatial, dynamic);
        } else {
            validateColliderTypeForMass(colliderCfg, dynamic);
            shape = PhysicsColliderFactory.create(colliderCfg, spatial, dynamic);
        }

        RigidBodyControl rb = new RigidBodyControl(shape, mass);
        rb.setKinematic(PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false));
        rb.setFriction((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8));
        rb.setRestitution((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.0));

        boolean lockRotation = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);
        if (lockRotation) rb.setAngularFactor(0f);

        int group = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "group"), 1);
        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), -1);
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);

        spatial.addControl(rb);

        int id = registry.nextId();
        PhysicsBodyHandle h = new PhysicsBodyHandle(id, surfaceId, rb);

        registry.put(h);

        CollisionObjectUtil.tryBindUserObject(rb, h);
        registry.indexCollisionObject(h);

        world.enqueueAdd(rb);

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][body] created id={} surfaceId={} mass={} dyn={} group={} mask={} name={}",
                    id, surfaceId, mass, dynamic, group, mask, spatial.getName());
        }

        return h;
    }

    public void remove(Object handleOrId) {
        int id = registry.resolveBodyId(handleOrId);
        if (id <= 0) return;

        PhysicsBodyHandle h = registry.remove(id);
        if (h == null) return;

        bodyState.remove(id);
        registry.removeSurfaceBinding(h.surfaceId, id);

        RigidBodyControl rb;
        try {
            rb = h.__raw();
        } catch (Throwable ignored) {
            rb = null;
        }

        if (rb != null) {
            registry.removeControlBinding(rb, id);
        }

        registry.unindexCollisionObject(h);

        try {
            PhysicsSpace sp = engine.__getPhysicsSpaceOrNull();
            if (sp != null && rb != null) sp.remove(rb);
        } catch (Throwable ignored) {
        }

        try {
            Spatial sp = surfaces.get(h.surfaceId);
            if (sp != null) sp.removeControl(RigidBodyControl.class);
        } catch (Throwable ignored) {
        }

        if (dbg && log.isDebugEnabled()) {
            log.debug("[physics][body] removed id={} surfaceId={}", h.id, h.surfaceId);
        }

        emitter.emitBodyRemoved(h);
    }

    public void clearAll() {
        for (PhysicsBodyHandle h : registry.values()) {
            if (h == null) continue;
            try {
                remove(h.id);
            } catch (Throwable ignored) {
            }
        }
        registry.clearAll();
        bodyState.clear();
    }

    public boolean emitMoveEvents(long step, float dt) {
        int emitted = 0;
        for (PhysicsBodyHandle h : registry.values()) {
            if (h == null) continue;

            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable ignored) {
                continue;
            }
            if (rb == null) continue;

            boolean changed = bodyState.updateAndCheckChanged(h.id, rb, MOVE_POS_EPS, MOVE_ROT_EPS, MOVE_VEL_EPS);
            if (!changed) continue;

            emitter.bus().emit(PhysicsEmitter.TOPIC_BODY_MOVE, org.foxesworld.kalitech.engine.modules.physics.js.PhysicsJs.evtJs(
                    "step", step,
                    "dt", dt,
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", null,
                    "pos", null,
                    "rot", null,
                    "vel", null,
                    "angVel", null,
                    "active", safeActive(rb)
            ));

            emitted++;
            if (emitted >= MOVE_EVENT_MAX_PER_STEP) break;
        }
        return emitted > 0;
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            int id = resolveIdFromValue(v, "id", "surfaceId");
            if (id > 0) return id;
        }

        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        if (s instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }
}