/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.physics.core;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionObjectUtil;
import org.foxesworld.kalitech.engine.modules.physics.util.IdentityObjectIntMap;
import org.foxesworld.kalitech.engine.modules.physics.util.IntIntMap;
import org.foxesworld.kalitech.engine.modules.physics.util.IntObjectMap;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class PhysicsRegistry {
    private final Logger log;
    private final AtomicInteger ids = new AtomicInteger(1);
    private final IntObjectMap<PhysicsBodyHandle> byId = new IntObjectMap(2048);
    private final IntIntMap bodyIdBySurface = new IntIntMap(2048);
    private final IdentityObjectIntMap idByControl = new IdentityObjectIntMap(2048);
    private final IdentityObjectIntMap bodyIdByCollisionObject = new IdentityObjectIntMap(4096);

    public PhysicsRegistry(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    private static int resolveIdFromValue(LuaValueRef v, String ... members) {
        if (v == null || members == null) {
            return 0;
        }
        try {
            if (v.isNumber()) {
                return v.asInt();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        for (String m : members) {
            int r = PhysicsRegistry.getIntMember(v, m);
            if (r <= 0) continue;
            return r;
        }
        return 0;
    }

    private static int getIntMember(LuaValueRef v, String member) {
        if (v == null || member == null) {
            return 0;
        }
        try {
            LuaValueRef r;
            if (!v.hasMember(member)) {
                return 0;
            }
            LuaValueRef m = v.getMember(member);
            if (m == null) {
                return 0;
            }
            if (m.isNumber()) {
                return m.asInt();
            }
            if (m.canExecute() && (r = m.execute(new Object[0])) != null && r.isNumber()) {
                return r.asInt();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 0;
    }

    public int nextId() {
        return this.ids.getAndIncrement();
    }

    public PhysicsBodyHandle get(int bodyId) {
        if (bodyId <= 0) {
            return null;
        }
        return this.byId.get(bodyId);
    }

    public boolean exists(int bodyId) {
        return bodyId > 0 && this.byId.contains(bodyId);
    }

    public int bodyOfSurface(int surfaceId) {
        if (surfaceId <= 0) {
            return 0;
        }
        return this.bodyIdBySurface.getOrZero(surfaceId);
    }

    public PhysicsBodyHandle getExistingBySurface(int surfaceId) {
        int existing = this.bodyOfSurface(surfaceId);
        return existing > 0 ? this.byId.get(existing) : null;
    }

    public void put(PhysicsBodyHandle h) {
        Objects.requireNonNull(h, "h");
        this.byId.put(h.id, h);
        this.bodyIdBySurface.put(h.surfaceId, h.id);
        RigidBodyControl raw = h.__raw();
        if (raw != null) {
            this.idByControl.put(raw, h.id);
        }
    }

    public PhysicsBodyHandle remove(int id) {
        if (id <= 0) {
            return null;
        }
        return this.byId.remove(id);
    }

    public void removeSurfaceBinding(int surfaceId, int bodyId) {
        if (surfaceId <= 0) {
            return;
        }
        this.bodyIdBySurface.removeIfEquals(surfaceId, bodyId);
    }

    public Integer idOfControl(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        int v = this.idByControl.getOrZero(rb);
        return v > 0 ? Integer.valueOf(v) : null;
    }

    public void removeControlBinding(RigidBodyControl rb, int id) {
        if (rb == null) {
            return;
        }
        this.idByControl.removeIfEquals(rb, id);
    }

    public void clearAll() {
        this.byId.clear();
        this.bodyIdBySurface.clear();
        this.idByControl.clear();
        this.bodyIdByCollisionObject.clear();
    }

    public Iterable<IntObjectMap.Entry<PhysicsBodyHandle>> entries() {
        return this.byId.entries();
    }

    public int resolveBodyId(Object handleOrId) {
        Map m;
        Object id;
        LuaValueRef v;
        int id2;
        if (handleOrId == null) {
            return 0;
        }
        if (handleOrId instanceof Number) {
            Number n = (Number)handleOrId;
            return n.intValue();
        }
        if (handleOrId instanceof PhysicsBodyHandle) {
            PhysicsBodyHandle h = (PhysicsBodyHandle)handleOrId;
            return h.id;
        }
        if (handleOrId instanceof LuaValueRef && (id2 = PhysicsRegistry.resolveIdFromValue(v = (LuaValueRef)handleOrId, "id", "bodyId")) > 0) {
            return id2;
        }
        if (handleOrId instanceof Map && (id = (m = (Map)handleOrId).get("id")) instanceof Number) {
            Number n = (Number)id;
            return n.intValue();
        }
        return 0;
    }

    public PhysicsBodyHandle requireHandle(Object handleOrId, String where) {
        int id = this.resolveBodyId(handleOrId);
        if (id <= 0) {
            throw new IllegalArgumentException(where + ": body id/handle required");
        }
        PhysicsBodyHandle h = this.byId.get(id);
        if (h == null) {
            throw new IllegalArgumentException(where + ": unknown bodyId=" + id);
        }
        return h;
    }

    public int bodyIdFromCollisionObject(Object obj) {
        int id;
        if (obj == null) {
            return 0;
        }
        int direct = this.bodyIdByCollisionObject.getOrZero(obj);
        if (direct > 0) {
            return direct;
        }
        Object canonical = CollisionObjectUtil.canonicalCollisionKey(obj);
        if (canonical != obj && canonical != null && (id = this.bodyIdByCollisionObject.getOrZero(canonical)) > 0) {
            return id;
        }
        Object uo = CollisionObjectUtil.tryGetUserObject(obj);
        if (uo == null && canonical != null && canonical != obj) {
            uo = CollisionObjectUtil.tryGetUserObject(canonical);
        }
        if (uo != null) {
            int idUoCanon;
            RigidBodyControl rbUo;
            int idCtl;
            int idUo = this.bodyIdByCollisionObject.getOrZero(uo);
            if (idUo > 0) {
                return idUo;
            }
            if (uo instanceof PhysicsBodyHandle) {
                PhysicsBodyHandle h = (PhysicsBodyHandle)uo;
                return h.id;
            }
            if (uo instanceof RigidBodyControl && (idCtl = this.idByControl.getOrZero(rbUo = (RigidBodyControl)uo)) > 0) {
                return idCtl;
            }
            Object uoCanon = CollisionObjectUtil.canonicalCollisionKey(uo);
            if (uoCanon != null && uoCanon != uo && (idUoCanon = this.bodyIdByCollisionObject.getOrZero(uoCanon)) > 0) {
                return idUoCanon;
            }
        }
        if (obj instanceof RigidBodyControl) {
            int idPrb;
            RigidBodyControl rb = (RigidBodyControl)obj;
            int idCtl = this.idByControl.getOrZero(rb);
            if (idCtl > 0) {
                return idCtl;
            }
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null && (idPrb = this.bodyIdByCollisionObject.getOrZero(prb)) > 0) {
                return idPrb;
            }
        }
        return 0;
    }

    public void indexCollisionObject(PhysicsBodyHandle h) {
        RigidBodyControl raw;
        Objects.requireNonNull(h, "h");
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) {
            this.bodyIdByCollisionObject.put(key, h.id);
        }
        if ((raw = h.__raw()) != null) {
            this.bodyIdByCollisionObject.put(raw, h.id);
        }
        if (raw instanceof RigidBodyControl) {
            Object uoPrb;
            Object uoRb;
            RigidBodyControl rb = raw;
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                this.bodyIdByCollisionObject.put(prb, h.id);
            }
            if ((uoRb = CollisionObjectUtil.tryGetUserObject(rb)) != null) {
                this.bodyIdByCollisionObject.put(uoRb, h.id);
            }
            if (prb != null && (uoPrb = CollisionObjectUtil.tryGetUserObject(prb)) != null) {
                this.bodyIdByCollisionObject.put(uoPrb, h.id);
            }
        }
    }

    public void unindexCollisionObject(PhysicsBodyHandle h) {
        RigidBodyControl raw;
        if (h == null) {
            return;
        }
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) {
            this.bodyIdByCollisionObject.removeIfEquals(key, h.id);
        }
        if ((raw = h.__raw()) != null) {
            this.bodyIdByCollisionObject.removeIfEquals(raw, h.id);
        }
        if (raw instanceof RigidBodyControl) {
            Object uoPrb;
            Object uoRb;
            RigidBodyControl rb = raw;
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                this.bodyIdByCollisionObject.removeIfEquals(prb, h.id);
            }
            if ((uoRb = CollisionObjectUtil.tryGetUserObject(rb)) != null) {
                this.bodyIdByCollisionObject.removeIfEquals(uoRb, h.id);
            }
            if (prb != null && (uoPrb = CollisionObjectUtil.tryGetUserObject(prb)) != null) {
                this.bodyIdByCollisionObject.removeIfEquals(uoPrb, h.id);
            }
        }
    }

    public PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = this.bodyIdFromCollisionObject(obj);
        if (id > 0) {
            return this.byId.get(id);
        }
        if (!this.log.isTraceEnabled()) {
            return null;
        }
        if (obj == null) {
            return null;
        }
        for (IntObjectMap.Entry<PhysicsBodyHandle> e : this.byId.entries()) {
            PhysicsBodyHandle h = e.value();
            if (h == null) continue;
            Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
            if (key == obj) {
                return h;
            }
            if (h.__raw() != obj) continue;
            return h;
        }
        return null;
    }
}

