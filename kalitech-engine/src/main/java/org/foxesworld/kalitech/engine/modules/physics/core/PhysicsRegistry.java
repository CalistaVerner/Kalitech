// FILE: org/foxesworld/kalitech/engine/modules/physics/core/PhysicsRegistry.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionObjectUtil;
import org.foxesworld.kalitech.engine.modules.physics.util.IdentityObjectIntMap;
import org.foxesworld.kalitech.engine.modules.physics.util.IntIntMap;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Physics body registry + collision object indexing.
 *
 * Optimized for physics tick thread:
 * - primitive maps
 * - identity collision indexing
 * - canonical collision key normalization
 */
public final class PhysicsRegistry {

    private final Logger log;
    private final AtomicInteger ids = new AtomicInteger(1);

    private final IntObjectMap<PhysicsBodyHandle> byId = new IntObjectMap<>(2048);
    private final IntIntMap bodyIdBySurface = new IntIntMap(2048);

    private final IdentityObjectIntMap bodyIdByControl = new IdentityObjectIntMap(2048);

    /**
     * Maps various collision identities (RBC/PRB/userObject/canonical) to bodyId.
     */
    private final IdentityObjectIntMap bodyIdByCollisionIdentity = new IdentityObjectIntMap(4096);

    public PhysicsRegistry(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public int nextId() {
        return ids.getAndIncrement();
    }

    public PhysicsBodyHandle get(int bodyId) {
        if (bodyId <= 0) return null;
        return byId.get(bodyId);
    }

    public boolean exists(int bodyId) {
        return bodyId > 0 && byId.contains(bodyId);
    }

    public int bodyOfSurface(int surfaceId) {
        if (surfaceId <= 0) return 0;
        return bodyIdBySurface.getOrZero(surfaceId);
    }

    public PhysicsBodyHandle getExistingBySurface(int surfaceId) {
        int existing = bodyOfSurface(surfaceId);
        return existing > 0 ? byId.get(existing) : null;
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

    public void removeSurfaceBinding(int surfaceId, int bodyId) {
        if (surfaceId <= 0) return;
        bodyIdBySurface.removeIfEquals(surfaceId, bodyId);
    }

    public void put(PhysicsBodyHandle h) {
        Objects.requireNonNull(h, "h");
        byId.put(h.id, h);
        bodyIdBySurface.put(h.surfaceId, h.id);

        Object raw = h.__raw();
        if (raw instanceof RigidBodyControl rbc) {
            bodyIdByControl.put(rbc, h.id);
        }
    }

    public PhysicsBodyHandle remove(int id) {
        if (id <= 0) return null;
        return byId.remove(id);
    }

    public Integer idOfControl(RigidBodyControl rb) {
        if (rb == null) return null;
        int v = bodyIdByControl.getOrZero(rb);
        return v > 0 ? v : null;
    }

    public Iterable<IntObjectMap.Entry<PhysicsBodyHandle>> entries() {
        return byId.entries();
    }

    public int resolveBodyId(Object handleOrId) {
        if (handleOrId == null) return 0;

        if (handleOrId instanceof Number n) return n.intValue();
        if (handleOrId instanceof PhysicsBodyHandle h) return h.id;

        if (handleOrId instanceof Value v) {
            int id = resolveIdFromValue(v, "id", "bodyId");
            if (id > 0) return id;
        }

        if (handleOrId instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) return n.intValue();
        }

        return 0;
    }

    public PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        int id = resolveBodyId(handleOrId);
        if (id <= 0) throw new IllegalArgumentException(where + ": body id/handle required");
        PhysicsBodyHandle h = byId.get(id);
        if (h == null) throw new IllegalArgumentException(where + ": unknown bodyId=" + id);
        return h;
    }

    public void removeControlBinding(RigidBodyControl rb, int id) {
        if (rb == null) return;
        bodyIdByControl.removeIfEquals(rb, id);
    }

    public void clearAll() {
        byId.clear();
        bodyIdBySurface.clear();
        bodyIdByControl.clear();
        bodyIdByCollisionIdentity.clear();
    }

    /**
     * Fast-path bodyId resolution from any collision object / identity.
     */
    public int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;

        int id = bodyIdByCollisionIdentity.getOrZero(obj);
        if (id > 0) return id;

        // Normalize to a canonical collision key (PRB preferred).
        Object canonical = CollisionObjectUtil.canonicalCollisionKey(obj);
        if (canonical != null && canonical != obj) {
            id = bodyIdByCollisionIdentity.getOrZero(canonical);
            if (id > 0) return id;
        }

        // Try userObject indirection (common Bullet pattern).
        Object uo = CollisionObjectUtil.tryGetUserObject(obj);
        if (uo == null && canonical != null && canonical != obj) {
            uo = CollisionObjectUtil.tryGetUserObject(canonical);
        }

        if (uo != null) {
            id = bodyIdByCollisionIdentity.getOrZero(uo);
            if (id > 0) return id;

            if (uo instanceof PhysicsBodyHandle h) return h.id;

            if (uo instanceof RigidBodyControl rbUo) {
                id = bodyIdByControl.getOrZero(rbUo);
                if (id > 0) return id;

                PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rbUo);
                if (prb != null) {
                    id = bodyIdByCollisionIdentity.getOrZero(prb);
                    if (id > 0) return id;
                }
            }
        }

        // RBC shortcut (if caller gave control directly).
        if (obj instanceof RigidBodyControl rb) {
            id = bodyIdByControl.getOrZero(rb);
            if (id > 0) return id;

            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                id = bodyIdByCollisionIdentity.getOrZero(prb);
                if (id > 0) return id;
            }
        }

        return 0;
    }

    /**
     * Index all stable identities for a body to make collision lookup O(1).
     */
    public void indexCollisionObject(PhysicsBodyHandle h) {
        Objects.requireNonNull(h, "h");

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionIdentity.put(raw, h.id);

        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null && key != raw) bodyIdByCollisionIdentity.put(key, h.id);

        if (raw instanceof RigidBodyControl rbc) {
            bodyIdByControl.put(rbc, h.id);

            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rbc);
            if (prb != null) bodyIdByCollisionIdentity.put(prb, h.id);

            Object uoRbc = CollisionObjectUtil.tryGetUserObject(rbc);
            if (uoRbc != null) bodyIdByCollisionIdentity.put(uoRbc, h.id);

            if (prb != null) {
                Object uoPrb = CollisionObjectUtil.tryGetUserObject(prb);
                if (uoPrb != null) bodyIdByCollisionIdentity.put(uoPrb, h.id);
            }
        }
    }

    public void unindexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionIdentity.removeIfEquals(raw, h.id);

        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null && key != raw) bodyIdByCollisionIdentity.removeIfEquals(key, h.id);

        if (raw instanceof RigidBodyControl rbc) {
            bodyIdByControl.removeIfEquals(rbc, h.id);

            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rbc);
            if (prb != null) bodyIdByCollisionIdentity.removeIfEquals(prb, h.id);

            Object uoRbc = CollisionObjectUtil.tryGetUserObject(rbc);
            if (uoRbc != null) bodyIdByCollisionIdentity.removeIfEquals(uoRbc, h.id);

            if (prb != null) {
                Object uoPrb = CollisionObjectUtil.tryGetUserObject(prb);
                if (uoPrb != null) bodyIdByCollisionIdentity.removeIfEquals(uoPrb, h.id);
            }
        }
    }

    public PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = bodyIdFromCollisionObject(obj);
        if (id > 0) return byId.get(id);

        // Trace-only fallback scan (debug aid).
        if (!log.isTraceEnabled() || obj == null) return null;

        for (IntObjectMap.Entry<PhysicsBodyHandle> e : byId.entries()) {
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;

            Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
            if (key == obj) return h;
            if (h.__raw() == obj) return h;
        }

        return null;
    }
}