package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * Body lifecycle + per-body operations.
 */
final class PhysicsBodies {

    private final PhysicsState S;

    PhysicsBodies(PhysicsState state) {
        this.S = state;
    }

    private static String colliderType(Object colliderCfg) {
        if (colliderCfg instanceof Value v) {
            if (v.hasMember("type")) return String.valueOf(v.getMember("type"));
            return "";
        }
        if (colliderCfg instanceof Map<?, ?> m) {
            Object t = m.get("type");
            return t == null ? "" : String.valueOf(t);
        }
        return "";
    }

    PhysicsBodyHandle body(Object cfg, PhysicsContacts contacts) {
        PhysicsSpace sp = S.requireSpace();
        contacts.ensureBound(sp);

        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        Spatial spatial = S.surfaces.get(surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + surfaceId);

        Integer existing = S.bodyIdBySurface.get(surfaceId);
        if (existing != null) {
            PhysicsBodyHandle h = S.byId.get(existing);
            if (h != null) return h;
        }

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);
        boolean dynamic = mass > 0f;

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");

        CollisionShape shape;
        if (colliderCfg == null) {
            shape = defaultShapeForSpatial(spatial, dynamic);
        } else {
            // forbid collider.type=mesh for dynamic
            if (dynamic) {
                String t = colliderType(colliderCfg);
                if ("mesh".equalsIgnoreCase(t)) {
                    throw new IllegalArgumentException(
                            "physics.body: collider.type='mesh' is not allowed for dynamic bodies (mass>0). " +
                                    "Use collider.type='dynamicMesh' or primitive collider."
                    );
                }
            }
            shape = PhysicsColliderFactory.create(colliderCfg, spatial);
        }

        RigidBodyControl rb = new RigidBodyControl(shape, mass);

        rb.setFriction((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8));
        rb.setRestitution((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.1));

        // Optional: collision groups at creation (accept both flat and object forms)
        Object groups = PhysicsValueParsers.member(cfg, "groups");
        if (groups == null) groups = PhysicsValueParsers.member(cfg, "collision");
        if (groups == null) groups = PhysicsValueParsers.member(cfg, "filter");
        int group = PhysicsValueParsers.asInt(PhysicsValueParsers.member(cfg, "group"), -1);
        int mask = PhysicsValueParsers.asInt(PhysicsValueParsers.member(cfg, "mask"), -1);
        if (groups != null) {
            if (group < 0) group = PhysicsValueParsers.asInt(PhysicsValueParsers.member(groups, "group"), group);
            if (mask < 0) mask = PhysicsValueParsers.asInt(PhysicsValueParsers.member(groups, "mask"), mask);
            // aliases
            if (group < 0) group = PhysicsValueParsers.asInt(PhysicsValueParsers.member(groups, "g"), group);
            if (mask < 0) mask = PhysicsValueParsers.asInt(PhysicsValueParsers.member(groups, "m"), mask);
        }
        if (group >= 0) rb.setCollisionGroup(group);
        if (mask >= 0) rb.setCollideWithGroups(mask);

        Object damping = PhysicsValueParsers.member(cfg, "damping");
        if (damping != null) {
            double ld = PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "linear"), 0.0);
            double ad = PhysicsValueParsers.asNum(PhysicsValueParsers.member(damping, "angular"), 0.0);
            rb.setDamping((float) ld, (float) ad);
        } else {
            rb.setDamping(0.05f, 0.1f);
        }

        boolean kinematic = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "kinematic"), false);
        rb.setKinematic(kinematic);

        boolean lockRot = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "lockRotation"), false);
        // Optional: per-axis factors (numbers or vec3)
        Object linearFactor = PhysicsValueParsers.member(cfg, "linearFactor");
        if (linearFactor != null) {
            Vector3f lf = PhysicsValueParsers.vec3(linearFactor, 1f, 1f, 1f);
            rb.setLinearFactor(lf);
        }

        Object angularFactor = PhysicsValueParsers.member(cfg, "angularFactor");
        if (angularFactor != null) {
            Vector3f af = PhysicsValueParsers.vec3(angularFactor, 1f, 1f, 1f);
            rb.setAngularFactor(af);
        }

        if (lockRot) rb.setAngularFactor(0f);

        // Optional: per-body gravity / gravityFactor
        Object gOverride = PhysicsValueParsers.member(cfg, "gravity");
        if (gOverride != null) {
            rb.setGravity(PhysicsValueParsers.vec3(gOverride, 0f, -9.81f, 0f));
        } else {
            Object gFactor = PhysicsValueParsers.member(cfg, "gravityFactor");
            if (gFactor != null) {
                float f = (float) PhysicsValueParsers.asNum(gFactor, 1.0);
                Vector3f worldG = new Vector3f();
                try {
                    // jME usually supports getGravity(Vector3f store)
                    sp.getGravity(worldG);
                } catch (Throwable t) {
                    throw new IllegalStateException("physics.body: gravityFactor requires PhysicsSpace.getGravity(store)", t);
                }
                rb.setGravity(worldG.mult(f));
            }
        }

        // Optional: sleeping thresholds
        Object sleep = PhysicsValueParsers.member(cfg, "sleep");
        if (sleep != null) {
            float sl = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(sleep, "linear"), 0.0);
            float sa = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(sleep, "angular"), 0.0);
            rb.setSleepingThresholds(sl, sa);
        }

        // CCD for dynamics (flat keys or object form)
        if (dynamic && !kinematic) {
            Object ccd = PhysicsValueParsers.member(cfg, "ccd");
            float ccdMotionThreshold = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdMotionThreshold"), 0.001);
            float ccdRadius = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ccdSweptSphereRadius"), 0.20);
            if (ccd != null) {
                ccdMotionThreshold = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(ccd, "motionThreshold"), ccdMotionThreshold);
                ccdRadius = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(ccd, "radius"), ccdRadius);
                // aliases
                ccdMotionThreshold = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(ccd, "threshold"), ccdMotionThreshold);
            }
            rb.setCcdMotionThreshold(Math.max(0.0f, ccdMotionThreshold));
            rb.setCcdSweptSphereRadius(Math.max(0.0f, ccdRadius));
        }

        spatial.addControl(rb);
        S.pendingAdd.add(rb);

        int id = S.ids.getAndIncrement();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);

        S.byId.put(id, handle);
        S.bodyIdBySurface.put(surfaceId, id);
        S.idByControl.put(rb, id);
        S.indexCollisionObject(handle);

        S.bus().emit(PhysicsEvents.BODY_CREATE, PhysicsState.evt(
                "bodyId", id,
                "surfaceId", surfaceId,
                "mass", mass,
                "kinematic", kinematic,
                "lockRotation", lockRot
        ));

        return handle;
    }

    int bodyOfSurface(int surfaceId) {
        if (surfaceId <= 0) return 0;
        Integer id = S.bodyIdBySurface.get(surfaceId);
        return id == null ? 0 : id;
    }

    PhysicsBodyHandle handle(int bodyId) {
        if (bodyId <= 0) return null;
        return S.byId.get(bodyId);
    }

    boolean exists(int bodyId) {
        return bodyId > 0 && S.byId.containsKey(bodyId);
    }

    void remove(Object handleOrId, PhysicsContacts contacts) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) return;

        PhysicsBodyHandle h = S.byId.remove(id);
        if (h == null) return;

        S.unindexCollisionObject(h);

        S.bus().emit(PhysicsEvents.BODY_REMOVE, PhysicsState.evt(
                "bodyId", id,
                "surfaceId", h.surfaceId
        ));

        S.bodyIdBySurface.remove(h.surfaceId, id);

        Spatial spatial = S.surfaces.get(h.surfaceId);
        RigidBodyControl rb = h.__raw();

        S.idByControl.remove(rb);
        try {
            S.pendingAdd.remove(rb);
        } catch (Throwable ignored) {
        }

        // Queue removal; actual PhysicsSpace mutation is flushed in PhysicsContacts.prePhysicsTick()
        S.pendingRemove.add(rb);

        try {
            if (spatial != null) spatial.removeControl(rb);
        } catch (Throwable ignored) {
        }
    }

    Object position(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setLinearVelocity(v);
    }

    void yaw(Object handleOrId, double yaw) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.yaw(yaw)");
        RigidBodyControl rb = h.__raw();
        Quaternion q = new Quaternion();
        q.fromAngles(0f, (float) yaw, 0f);
        rb.setPhysicsRotation(q);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
    }

    void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1f);
        }
    }

    void setKinematic(Object handleOrId, boolean kinematic) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.setKinematic(kinematic)");
        RigidBodyControl rb = h.__raw();
        rb.setKinematic(kinematic);
        try {
            rb.activate();
        } catch (Throwable ignored) {
        }
    }

    void collisionGroups(Object handleOrId, int group, int mask) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.collisionGroups(group,mask)");
        RigidBodyControl rb = h.__raw();
        rb.setCollisionGroup(group);
        rb.setCollideWithGroups(mask);
    }

    void applyCentralForce(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyCentralForce(force)");
        Vector3f f = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyCentralForce(f);
    }

    void applyTorque(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyTorque(torque)");
        Vector3f t = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyTorque(t);
    }

    Object angularVelocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity()");
        Vector3f v = h.__raw().getAngularVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    void angularVelocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.angularVelocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setAngularVelocity(v);
    }

    // ---------------- helpers ----------------

    void clearForces(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.clearForces()");
        RigidBodyControl rb = h.__raw();
        rb.clearForces();
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    private PhysicsBodyHandle requireHandle(Object handleOrId, String who) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) throw new IllegalArgumentException(who + " invalid body handle/id");
        PhysicsBodyHandle h = S.byId.get(id);
        if (h == null) throw new IllegalArgumentException(who + " body not found id=" + id);
        return h;
    }

    private int resolveBodyId(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof Number n) return n.intValue();
        if (handleOrId instanceof PhysicsBodyHandle h) return h.id;

        if (handleOrId instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null) {
                    if (id.isNumber()) return id.asInt();
                    if (id.canExecute()) {
                        Value r = id.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }

            if (v.hasMember("bodyId")) {
                Value bid = v.getMember("bodyId");
                if (bid != null) {
                    if (bid.isNumber()) return bid.asInt();
                    if (bid.canExecute()) {
                        Value r = bid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (handleOrId instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        if (s instanceof Number n) return n.intValue();

        if (s instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null) {
                    if (id.isNumber()) return id.asInt();
                    if (id.canExecute()) {
                        Value r = id.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }

            if (v.hasMember("surfaceId")) {
                Value sid = v.getMember("surfaceId");
                if (sid != null) {
                    if (sid.isNumber()) return sid.asInt();
                    if (sid.canExecute()) {
                        Value r = sid.execute();
                        if (r != null && r.isNumber()) return r.asInt();
                    }
                }
            }
        }

        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        if (s instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }

    private CollisionShape defaultShapeForSpatial(Spatial spatial, boolean dynamic) {
        // Fast path for primitive geometries
        if (spatial instanceof Geometry g) {
            Mesh mesh = g.getMesh();
            if (mesh instanceof Box box) {
                Vector3f he = new Vector3f(box.xExtent, box.yExtent, box.zExtent);
                return new BoxCollisionShape(he);
            }
            if (mesh instanceof Sphere sphere) {
                return new SphereCollisionShape(sphere.getRadius());
            }
            if (mesh instanceof Cylinder cyl) {
                // jme Cylinder: radius, height
                float r = cyl.getRadius();
                float h = cyl.getHeight();
                return new CapsuleCollisionShape(r, Math.max(0f, h - (2f * r)));
            }

            // Mesh caching per Mesh instance
            PhysicsState.ShapeKey key = new PhysicsState.ShapeKey(mesh, dynamic);
            CollisionShape cached = S.shapeCache.get(key);
            if (cached != null) return cached;

            CollisionShape cs = dynamic ? CollisionShapeFactory.createDynamicMeshShape(spatial)
                    : CollisionShapeFactory.createMeshShape(spatial);
            S.shapeCache.putIfAbsent(key, cs);
            return cs;
        }

        // Generic spatial: approximate; dynamic mesh shape if dynamic
        if (!dynamic) {
            return CollisionShapeFactory.createMeshShape(spatial);
        }

        // For dynamic non-geometry, try bounding box → box collider, else dynamic mesh
        BoundingVolume bv = spatial.getWorldBound();
        if (bv instanceof BoundingBox bb) {
            Vector3f he = new Vector3f(bb.getXExtent(), bb.getYExtent(), bb.getZExtent());
            if (he.lengthSquared() > 1e-12f) return new BoxCollisionShape(he);
        }

        return CollisionShapeFactory.createDynamicMeshShape(spatial);
    }
}