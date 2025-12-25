// FILE: org/foxesworld/kalitech/engine/api/impl/physics/PhysicsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.SurfaceRegistry;
import org.foxesworld.kalitech.engine.api.interfaces.SurfaceApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhysicsApiImpl implements PhysicsApi {

    private static final Logger log = LogManager.getLogger(PhysicsApiImpl.class);

    private final EngineApiImpl engine;
    private final SimpleApplication app;
    private final SurfaceRegistry surfaces;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();

    public PhysicsApiImpl(EngineApiImpl engine, SurfaceRegistry surfaces) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = Objects.requireNonNull(engine.getApp(), "app");
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    private PhysicsSpace space() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            throw new IllegalStateException("[physics] PhysicsSpace not bound. RuntimeAppState must attach BulletAppState and call engineApi.__setPhysicsSpace(space).");
        }
        return s;
    }

    private PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) throw new IllegalArgumentException(where + ": body id/handle required");
        PhysicsBodyHandle h = byId.get(id);
        if (h == null) throw new IllegalArgumentException(where + ": unknown bodyId=" + id);
        return h;
    }

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public PhysicsBodyHandle body(Object cfg) {
        PhysicsSpace space = space();

        if (cfg == null) throw new IllegalArgumentException("physics.body(cfg) cfg is required");

        int surfaceId = resolveSurfaceId(cfg);
        if (surfaceId <= 0) throw new IllegalArgumentException("physics.body: surface id is required");

        Spatial spatial = surfaces.get(surfaceId);
        if (spatial == null) throw new IllegalStateException("physics.body: unknown surfaceId=" + surfaceId);

        Integer existing = bodyIdBySurface.get(surfaceId);
        if (existing != null) {
            PhysicsBodyHandle h = byId.get(existing);
            if (h != null) return h;
        }

        Object colliderCfg = PhysicsValueParsers.member(cfg, "collider");
        CollisionShape shape = PhysicsColliderFactory.create(colliderCfg, spatial);

        float mass = (float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mass"), 0.0);

        RigidBodyControl rb = new RigidBodyControl(shape, mass);

        rb.setFriction((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "friction"), 0.8));
        rb.setRestitution((float) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "restitution"), 0.1));

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
        if (lockRot) rb.setAngularFactor(0f);

        spatial.addControl(rb);
        space.add(rb);

        int id = ids.getAndIncrement();
        PhysicsBodyHandle handle = new PhysicsBodyHandle(id, surfaceId, rb);
        byId.put(id, handle);
        bodyIdBySurface.put(surfaceId, id);

        log.debug("[physics] body created id={} surfaceId={} mass={} kinematic={} lockRotation={}", id, surfaceId, mass, kinematic, lockRot);
        return handle;
    }

    @HostAccess.Export
    @Override
    public void remove(Object handleOrId) {
        PhysicsSpace space = space();
        int id = resolveBodyId(handleOrId);
        if (id <= 0) return;

        PhysicsBodyHandle h = byId.remove(id);
        if (h == null) return;

        bodyIdBySurface.remove(h.surfaceId, id);

        Spatial spatial = surfaces.get(h.surfaceId);
        RigidBodyControl rb = h.__raw();

        try { space.remove(rb); } catch (Throwable ignored) {}
        try { if (spatial != null) spatial.removeControl(rb); } catch (Throwable ignored) {}

        log.debug("[physics] body removed id={} surfaceId={}", id, h.surfaceId);
    }

    @HostAccess.Export
    @Override
    public PhysicsRayHit raycast(Object cfg) {
        PhysicsSpace space = space();
        if (cfg == null) throw new IllegalArgumentException("physics.raycast(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to   = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"),   0, 0, 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return null;

        // choose closest
        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (f < bestFrac) { bestFrac = f; best = r; }
        }
        if (best == null) return null;

        // Map back to our handle (by comparing native object reference)
        int bodyId = 0;
        int surfaceId = 0;

        Object obj = best.getCollisionObject();
        if (obj != null) {
            for (PhysicsBodyHandle h : byId.values()) {
                if (h != null && h.__raw() == obj) {
                    bodyId = h.id;
                    surfaceId = h.surfaceId;
                    break;
                }
            }
        }

        Vector3f dir = to.subtract(from);
        Vector3f hitPoint = from.add(dir.mult(bestFrac));
        Vector3f n = best.getHitNormalLocal();

        return new PhysicsRayHit(
                bodyId,
                surfaceId,
                bestFrac,
                new PhysicsRayHit.Vec3(hitPoint.x, hitPoint.y, hitPoint.z),
                n == null ? new PhysicsRayHit.Vec3(0, 1, 0) : new PhysicsRayHit.Vec3(n.x, n.y, n.z)
        );
    }

    // ---- controller helpers (NEW) ----

    @HostAccess.Export
    public Object position(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.position()");
        Vector3f p = h.__raw().getPhysicsLocation();
        return new PhysicsRayHit.Vec3(p.x, p.y, p.z);
    }

    @HostAccess.Export
    public void warp(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.warp(pos)");
        Vector3f p = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        RigidBodyControl rb = h.__raw();
        rb.setPhysicsLocation(p);
        rb.setLinearVelocity(Vector3f.ZERO);
        rb.setAngularVelocity(Vector3f.ZERO);
    }

    @HostAccess.Export
    public Object velocity(Object handleOrId) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity()");
        Vector3f v = h.__raw().getLinearVelocity();
        return new PhysicsRayHit.Vec3(v.x, v.y, v.z);
    }

    @HostAccess.Export
    public void velocity(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.velocity(v)");
        Vector3f v = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().setLinearVelocity(v);
    }

    @HostAccess.Export
    public void applyImpulse(Object handleOrId, Object vec3) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.applyImpulse(impulse)");
        Vector3f imp = PhysicsValueParsers.vec3(vec3, 0, 0, 0);
        h.__raw().applyImpulse(imp, Vector3f.ZERO);
    }

    @HostAccess.Export
    public void lockRotation(Object handleOrId, boolean lock) {
        PhysicsBodyHandle h = requireHandle(handleOrId, "physics.lockRotation(lock)");
        RigidBodyControl rb = h.__raw();
        if (lock) {
            rb.setAngularFactor(0f);
            rb.setAngularVelocity(Vector3f.ZERO);
        } else {
            rb.setAngularFactor(1f);
        }
    }

    // ---- debug/gravity ----

    @HostAccess.Export
    @Override
    public void debug(boolean enabled) {
        // FIX: bullet field was never bound. Take BulletAppState from AppStateManager.
        BulletAppState b = app.getStateManager().getState(BulletAppState.class);
        if (b == null) {
            log.warn("[physics] debug({}) ignored: BulletAppState not attached", enabled);
            return;
        }
        b.setDebugEnabled(enabled);
    }

    @HostAccess.Export
    @Override
    public void gravity(Object vec3) {
        PhysicsSpace space = space();
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        space.setGravity(g);
    }

    // ---- integration helpers (internal) ----

    /** Called by EngineApiImpl cleanup when a surface is destroyed */
    public void __cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        Integer id = bodyIdBySurface.get(surfaceId);
        if (id != null) remove(id);
    }

    // ---- parsing ----

    private int resolveSurfaceId(Object cfg) {
        Object s = PhysicsValueParsers.member(cfg, "surface");
        if (s == null) return 0;

        // number
        if (s instanceof Number n) return n.intValue();

        // JS Value
        if (s instanceof Value v) {
            if (v.isNumber()) return v.asInt();

            // SurfaceHandle object: {id, kind}
            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null && id.isNumber()) return id.asInt();
            }
        }

        // SurfaceHandle from Java (SurfaceApi.SurfaceHandle)
        if (s instanceof SurfaceApi.SurfaceHandle h) return h.id;

        // Map {id:...}
        if (s instanceof java.util.Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        throw new IllegalArgumentException("physics.body: surface must be surfaceId or SurfaceHandle");
    }

    private int resolveBodyId(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof Number n) return n.intValue();
        if (handleOrId instanceof PhysicsBodyHandle h) return h.id;

        if (handleOrId instanceof Value v) {
            if (v.isNumber()) return v.asInt();
            if (v.hasMember("id")) {
                Value id = v.getMember("id");
                if (id != null && id.isNumber()) return id.asInt();
            }
        }

        // Map {id:...}
        if (handleOrId instanceof java.util.Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }

    /** Clear ALL physics bodies created via this API (used before world rebuild / ecs.reset). */
    public void __clearAll() {
        PhysicsSpace s = engine.__getPhysicsSpaceOrNull();
        if (s == null) {
            byId.clear();
            bodyIdBySurface.clear();
            return;
        }

        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;

            int surfaceId = h.surfaceId;
            RigidBodyControl rb = h.__raw();

            try { s.remove(rb); } catch (Throwable ignored) {}

            try {
                Spatial sp = surfaces.get(surfaceId);
                if (sp != null) sp.removeControl(rb);
            } catch (Throwable ignored) {}
        }

        byId.clear();
        bodyIdBySurface.clear();

        log.info("[physics] cleared all bodies");
    }
}