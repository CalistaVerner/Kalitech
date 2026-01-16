// FILE: org/foxesworld/kalitech/engine/modules/physics/internal/PhysicsRegistry.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionObjectUtil;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Physics body registry + collision object indexing.
 */
public final class PhysicsRegistry {

    private final Logger log;

    private final AtomicInteger ids = new AtomicInteger(1);

    private final ConcurrentHashMap<Integer, PhysicsBodyHandle> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> bodyIdBySurface = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<RigidBodyControl, Integer> idByControl = new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject = new ConcurrentHashMap<>(1024);

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
        return bodyId > 0 && byId.containsKey(bodyId);
    }

    public int bodyOfSurface(int surfaceId) {
        if (surfaceId <= 0) return 0;
        Integer id = bodyIdBySurface.get(surfaceId);
        return (id == null) ? 0 : id;
    }

    public PhysicsBodyHandle getExistingBySurface(int surfaceId) {
        Integer existing = bodyIdBySurface.get(surfaceId);
        if (existing == null) return null;
        return byId.get(existing);
    }

    public void put(PhysicsBodyHandle h) {
        byId.put(h.id, h);
        bodyIdBySurface.put(h.surfaceId, h.id);
        idByControl.put(h.__raw(), h.id);
    }

    /**
     * Re-indexes collision objects after the body is attached to the {@code PhysicsSpace}.
     *
     * <p>JME/Bullet can create/attach the underlying {@link com.jme3.bullet.objects.PhysicsRigidBody}
     * lazily on {@code space.add(rb)}. If we index only at construction time, collision callbacks
     * may later deliver a PRB instance that is not yet in our index. This method ensures deterministic
     * collision resolution without changing any existing userObject semantics.</p>
     */
    public void onAddedToSpace(RigidBodyControl rb) {
        if (rb == null) return;

        Integer id = idByControl.get(rb);
        if (id == null || id <= 0) return;

        PhysicsBodyHandle h = byId.get(id);
        if (h == null) return;

        // Re-run indexing: now extractPhysicsRigidBody(rb) is much more likely to succeed.
        indexCollisionObject(h);
    }

    public PhysicsBodyHandle remove(int id) {
        return byId.remove(id);
    }

    public void removeSurfaceBinding(int surfaceId, int bodyId) {
        bodyIdBySurface.remove(surfaceId, bodyId);
    }

    public Integer idOfControl(RigidBodyControl rb) {
        return (rb == null) ? null : idByControl.get(rb);
    }

    public void removeControlBinding(RigidBodyControl rb, int id) {
        if (rb != null) idByControl.remove(rb, id);
    }

    public void clearAll() {
        byId.clear();
        bodyIdBySurface.clear();
        idByControl.clear();
        bodyIdByCollisionObject.clear();
    }

    public Iterable<PhysicsBodyHandle> values() {
        return byId.values();
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

        Integer direct = bodyIdByCollisionObject.get(obj);
        if (direct != null) return direct;

        Object canonical = CollisionObjectUtil.canonicalCollisionKey(obj);
        if (canonical != obj) {
            Integer id = bodyIdByCollisionObject.get(canonical);
            if (id != null) return id;
        }

        Object uo = CollisionObjectUtil.tryGetUserObject(obj);
        if (uo == null && canonical != null && canonical != obj) {
            uo = CollisionObjectUtil.tryGetUserObject(canonical);
        }

        if (uo != null) {
            Integer idUo = bodyIdByCollisionObject.get(uo);
            if (idUo != null) {
                cacheResolved(obj, canonical, uo, idUo);
                return idUo;
            }

            if (uo instanceof PhysicsBodyHandle h) {
                cacheResolved(obj, canonical, uo, h.id);
                return h.id;
            }

            if (uo instanceof Number n) {
                int id = n.intValue();
                if (id > 0) {
                    cacheResolved(obj, canonical, uo, id);
                    return id;
                }
            }

            if (uo instanceof RigidBodyControl rbUo) {
                Integer idCtl = idByControl.get(rbUo);
                if (idCtl != null) {
                    cacheResolved(obj, canonical, uo, idCtl);
                    return idCtl;
                }
            }

            Object uoCanon = CollisionObjectUtil.canonicalCollisionKey(uo);
            if (uoCanon != null && uoCanon != uo) {
                Integer idUoCanon = bodyIdByCollisionObject.get(uoCanon);
                if (idUoCanon != null) {
                    cacheResolved(obj, canonical, uoCanon, idUoCanon);
                    return idUoCanon;
                }
            }
        }

        if (obj instanceof RigidBodyControl rb) {
            Integer idCtl = idByControl.get(rb);
            if (idCtl != null) {
                cacheResolved(obj, canonical, rb, idCtl);
                return idCtl;
            }

            var prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                Integer idPrb = bodyIdByCollisionObject.get(prb);
                if (idPrb != null) {
                    cacheResolved(obj, prb, rb, idPrb);
                    return idPrb;
                }
            }
        }

        return 0;
    }

    private void cacheResolved(Object obj, Object canonical, Object extraKey, int id) {
        if (id <= 0) return;
        try {
            bodyIdByCollisionObject.putIfAbsent(obj, id);
            if (canonical != null) bodyIdByCollisionObject.putIfAbsent(canonical, id);
            if (extraKey != null) bodyIdByCollisionObject.putIfAbsent(extraKey, id);
        } catch (Throwable ignored) {
        }
    }

    public void indexCollisionObject(PhysicsBodyHandle h) {
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.put(key, h.id);

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionObject.put(raw, h.id);

        if (raw instanceof RigidBodyControl rb) {
            var prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
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
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.remove(key, h.id);

        Object raw = h.__raw();
        if (raw != null) bodyIdByCollisionObject.remove(raw, h.id);

        if (raw instanceof RigidBodyControl rb) {
            var prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) bodyIdByCollisionObject.remove(prb, h.id);

            Object uoRb = CollisionObjectUtil.tryGetUserObject(rb);
            if (uoRb != null) bodyIdByCollisionObject.remove(uoRb, h.id);

            if (prb != null) {
                Object uoPrb = CollisionObjectUtil.tryGetUserObject(prb);
                if (uoPrb != null) bodyIdByCollisionObject.remove(uoPrb, h.id);
            }
        }
    }

    public PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = bodyIdFromCollisionObject(obj);
        if (id > 0) return byId.get(id);

        if (!log.isTraceEnabled()) return null;

        if (obj == null) return null;
        for (PhysicsBodyHandle h : byId.values()) {
            if (h == null) continue;
            Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
            if (key == obj) return h;
            if (h.__raw() == obj) return h;
        }
        return null;
    }
}