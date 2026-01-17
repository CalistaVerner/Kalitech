// FILE: org/foxesworld/kalitech/engine/modules/physics/core/PhysicsRegistry.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
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
 * <p>Optimized for physics tick thread: primitive maps, identity collision indexing.</p>
 */
public final class PhysicsRegistry {

    private final Logger log;
    private final AtomicInteger ids = new AtomicInteger(1);

    private final IntObjectMap<PhysicsBodyHandle> byId = new IntObjectMap<>(2048);
    private final IntIntMap bodyIdBySurface = new IntIntMap(2048);

    private final IdentityObjectIntMap idByControl = new IdentityObjectIntMap(2048);
    private final IdentityObjectIntMap bodyIdByCollisionObject = new IdentityObjectIntMap(4096);

    public PhysicsRegistry(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
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

    public void put(PhysicsBodyHandle h) {
        Objects.requireNonNull(h, "h");
        byId.put(h.id, h);
        bodyIdBySurface.put(h.surfaceId, h.id);

        Object raw = h.__raw();
        if (raw != null) {
            idByControl.put(raw, h.id);
        }
    }

    public PhysicsBodyHandle remove(int id) {
        if (id <= 0) return null;
        return byId.remove(id);
    }

    public void removeSurfaceBinding(int surfaceId, int bodyId) {
        if (surfaceId <= 0) return;
        bodyIdBySurface.removeIfEquals(surfaceId, bodyId);
    }

    public Integer idOfControl(RigidBodyControl rb) {
        if (rb == null) return null;
        int v = idByControl.getOrZero(rb);
        return v > 0 ? v : null;
    }

    public void removeControlBinding(RigidBodyControl rb, int id) {
        if (rb == null) return;
        idByControl.removeIfEquals(rb, id);
    }

    public void clearAll() {
        byId.clear();
        bodyIdBySurface.clear();
        idByControl.clear();
        bodyIdByCollisionObject.clear();
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

    public int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;

        int direct = bodyIdByCollisionObject.getOrZero(obj);
        if (direct > 0) return direct;

        Object canonical = CollisionObjectUtil.canonicalCollisionKey(obj);
        if (canonical != obj && canonical != null) {
            int id = bodyIdByCollisionObject.getOrZero(canonical);
            if (id > 0) return id;
        }

        Object uo = CollisionObjectUtil.tryGetUserObject(obj);
        if (uo == null && canonical != null && canonical != obj) {
            uo = CollisionObjectUtil.tryGetUserObject(canonical);
        }

        if (uo != null) {
            int idUo = bodyIdByCollisionObject.getOrZero(uo);
            if (idUo > 0) return idUo;

            if (uo instanceof PhysicsBodyHandle h) return h.id;

            if (uo instanceof RigidBodyControl rbUo) {
                int idCtl = idByControl.getOrZero(rbUo);
                if (idCtl > 0) return idCtl;
            }

            Object uoCanon = CollisionObjectUtil.canonicalCollisionKey(uo);
            if (uoCanon != null && uoCanon != uo) {
                int idUoCanon = bodyIdByCollisionObject.getOrZero(uoCanon);
                if (idUoCanon > 0) return idUoCanon;
            }
        }

        if (obj instanceof RigidBodyControl rb) {
            int idCtl = idByControl.getOrZero(rb);
            if (idCtl > 0) return idCtl;

            Object prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                int idPrb = bodyIdByCollisionObject.getOrZero(prb);
                if (idPrb > 0) return idPrb;
            }
        }

        return 0;
    }

    public void indexCollisionObject(PhysicsBodyHandle h) {
        Objects.requireNonNull(h, "h");

        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.put(key, h.id);

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionObject.put(raw, h.id);

        if (raw instanceof RigidBodyControl rb) {
            Object prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) bodyIdByCollisionObject.put(prb, h.id);

            Object uoRb = CollisionObjectUtil.tryGetUserObject(rb);
            if (uoRb != null) bodyIdByCollisionObject.put(uoRb, h.id);

            if (prb != null) {
                Object uoPrb = CollisionObjectUtil.tryGetUserObject(prb);
                if (uoPrb != null) bodyIdByCollisionObject.put(uoPrb, h.id);
            }
        }
    }

    public void unindexCollisionObject(PhysicsBodyHandle h) {
        if (h == null) return;

        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.removeIfEquals(key, h.id);

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionObject.removeIfEquals(raw, h.id);

        if (raw instanceof RigidBodyControl rb) {
            Object prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) bodyIdByCollisionObject.removeIfEquals(prb, h.id);

            Object uoRb = CollisionObjectUtil.tryGetUserObject(rb);
            if (uoRb != null) bodyIdByCollisionObject.removeIfEquals(uoRb, h.id);

            if (prb != null) {
                Object uoPrb = CollisionObjectUtil.tryGetUserObject(prb);
                if (uoPrb != null) bodyIdByCollisionObject.removeIfEquals(uoPrb, h.id);
            }
        }
    }

    public PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = bodyIdFromCollisionObject(obj);
        if (id > 0) return byId.get(id);

        if (!log.isTraceEnabled()) return null;
        if (obj == null) return null;

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