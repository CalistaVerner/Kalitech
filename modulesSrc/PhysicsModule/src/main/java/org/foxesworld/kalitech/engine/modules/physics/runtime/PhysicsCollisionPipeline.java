/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 *  org.foxesworld.kalitech.engine.script.events.ScriptEventBus
 *  org.foxesworld.kalitech.engine.util.LongHashSet
 *  org.foxesworld.kalitech.engine.util.LongHashSet$LongConsumer
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.ContactAgg;
import org.foxesworld.kalitech.engine.modules.physics.LongContactMap;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsLua;
import org.foxesworld.kalitech.engine.modules.physics.collision.CollisionPairKey;
import org.foxesworld.kalitech.engine.modules.physics.core.PhysicsRegistry;
import org.foxesworld.kalitech.engine.modules.physics.runtime.PhysicsBodyStateTracker;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.util.LongHashSet;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class PhysicsCollisionPipeline {
    private static final float IMPACT_MIN_IMPULSE = 0.25f;
    private static final float IMPACT_MIN_REL_SPEED = 0.2f;
    private final Logger log;
    private final PhysicsService svc;
    private final AtomicLong stepCounter = new AtomicLong(0L);
    private final AtomicBoolean collisionListenerBound = new AtomicBoolean(false);
    private final AtomicBoolean tickListenerBound = new AtomicBoolean(false);
    private final LongContactMap currContacts = new LongContactMap(4096);
    private volatile float lastDt = 0.0f;
    private volatile long currentStep = 0L;
    private final LongHashSet.LongConsumer emitStayConsumer = k -> {
        if (k == 0L) {
            return;
        }
        this.emitCollision("engine.physics.collision.stay", this.currentStep, this.lastDt, k, this.currContacts.get(k));
    };
    private volatile PhysicsBodyStateTracker bodyStateTracker;
    private LongHashSet currPairs = new LongHashSet(4096);
    private final LongHashSet.LongConsumer emitEndConsumer = k -> {
        if (k == 0L) {
            return;
        }
        if (this.currPairs.contains(k)) {
            return;
        }
        this.emitCollision("engine.physics.collision.end", this.currentStep, this.lastDt, k, null);
    };
    private LongHashSet prevPairs = new LongHashSet(4096);
    private final LongHashSet.LongConsumer emitBeginConsumer = k -> {
        if (k == 0L) {
            return;
        }
        if (this.prevPairs.contains(k)) {
            return;
        }
        ContactAgg agg = this.currContacts.get(k);
        this.emitCollision("engine.physics.collision.begin", this.currentStep, this.lastDt, k, agg);
        this.emitImpact(this.currentStep, this.lastDt, k, agg);
    };
    private final Vector3f tmpMid = new Vector3f();

    public PhysicsCollisionPipeline(PhysicsService svc, Logger log) {
        this.svc = Objects.requireNonNull(svc, "svc");
        this.log = Objects.requireNonNull(log, "log");
    }

    private static LuaObject luaVec3SafePos(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        try {
            Vector3f v = rb.getPhysicsLocation();
            return v == null ? null : PhysicsLua.luaVec3(v);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static LuaObject luaVec3SafeVel(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        try {
            Vector3f v = rb.getLinearVelocity();
            return v == null ? null : PhysicsLua.luaVec3(v);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static LuaObject luaVec3SafeAngVel(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        try {
            Vector3f v = rb.getAngularVelocity();
            return v == null ? null : PhysicsLua.luaVec3(v);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static LuaObject luaQuatSafe(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        try {
            Quaternion q = rb.getPhysicsRotation();
            return q == null ? null : PhysicsLua.luaQuat(q);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isActiveSafe(RigidBodyControl rb) {
        if (rb == null) {
            return false;
        }
        try {
            return rb.isActive();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static float massSafe(RigidBodyControl rb) {
        if (rb == null) {
            return 0.0f;
        }
        try {
            return rb.getMass();
        }
        catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private static boolean isKinematicSafe(RigidBodyControl rb) {
        if (rb == null) {
            return false;
        }
        try {
            return rb.isKinematic();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static LuaObject groupsSafe(RigidBodyControl rb) {
        if (rb == null) {
            return null;
        }
        int group = 0;
        int mask = 0;
        try {
            group = rb.getCollisionGroup();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            mask = rb.getCollideWithGroups();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (group == 0 && mask == 0) {
            return null;
        }
        return PhysicsLua.evtLua("group", group, "mask", mask);
    }

    private static float relativeSpeedApprox(RigidBodyControl a, RigidBodyControl b) {
        if (a == null || b == null) {
            return 0.0f;
        }
        try {
            Vector3f va = a.getLinearVelocity();
            Vector3f vb = b.getLinearVelocity();
            if (va == null || vb == null) {
                return 0.0f;
            }
            float dx = va.x - vb.x;
            float dy = va.y - vb.y;
            float dz = va.z - vb.z;
            float s2 = dx * dx + dy * dy + dz * dz;
            return s2 > 0.0f ? (float)Math.sqrt(s2) : 0.0f;
        }
        catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private static float reducedMassSafe(float ma, float mb) {
        if (!Float.isFinite(ma) || !Float.isFinite(mb)) {
            return 0.0f;
        }
        if (ma <= 0.0f || mb <= 0.0f) {
            return 0.0f;
        }
        float sum = ma + mb;
        if (!(sum > 1.0E-6f)) {
            return 0.0f;
        }
        return ma * mb / sum;
    }

    private static boolean hasCollision(RigidBodyControl rb) {
        if (rb == null) {
            return false;
        }
        try {
            return rb.getCollisionShape() != null;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isHardSurface(RigidBodyControl rb, Spatial sp) {
        if (!PhysicsCollisionPipeline.hasCollision(rb)) {
            return false;
        }
        if (sp != null) {
            try {
                Boolean hard = (Boolean)sp.getUserData("hardSurface");
                if (hard != null) {
                    return hard;
                }
            }
            catch (Throwable hard) {
                // empty catch block
            }
        }
        try {
            if (rb.isKinematic()) {
                return true;
            }
        }
        catch (Throwable hard) {
            // empty catch block
        }
        try {
            float m = rb.getMass();
            if (Float.isFinite(m) && m <= 0.0f) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public long step() {
        return this.stepCounter.get();
    }

    public float lastDt() {
        return this.lastDt;
    }

    public void bind(PhysicsSpace sp) {
        Objects.requireNonNull(sp, "sp");
        this.ensureCollisionListenerBound(sp);
        this.ensureTickListenerBound(sp);
    }

    public void setBodyStateTracker(PhysicsBodyStateTracker tracker) {
        this.bodyStateTracker = tracker;
    }

    private ScriptEventBus bus() {
        return this.svc.engine().getBus();
    }

    private PhysicsRegistry registry() {
        return this.svc.registry();
    }

    private SurfaceRegistry surfaces() {
        return this.svc.surfaces();
    }

    private void ensureCollisionListenerBound(PhysicsSpace sp) {
        if (!this.collisionListenerBound.compareAndSet(false, true)) {
            return;
        }
        sp.addCollisionListener(new PhysicsCollisionListener(){

            @Override
            public void collision(PhysicsCollisionEvent e) {
                int b;
                if (e == null) {
                    return;
                }
                int a = PhysicsCollisionPipeline.this.registry().bodyIdFromCollisionObject(e.getObjectA());
                long key = CollisionPairKey.pairKey(a, b = PhysicsCollisionPipeline.this.registry().bodyIdFromCollisionObject(e.getObjectB()));
                if (key == 0L) {
                    return;
                }
                PhysicsCollisionPipeline.this.currPairs.add(key);
                ContactAgg agg = PhysicsCollisionPipeline.this.currContacts.getOrCreate(key);
                if (agg == null) {
                    return;
                }
                float impulse = 0.0f;
                Vector3f point = null;
                Vector3f normal = null;
                try {
                    impulse = e.getAppliedImpulse();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                try {
                    Vector3f pa = e.getPositionWorldOnA();
                    Vector3f pb = e.getPositionWorldOnB();
                    point = pa != null && pb != null ? PhysicsCollisionPipeline.this.tmpMid.set(pa).addLocal(pb).multLocal(0.5f) : (pa != null ? pa : pb);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                try {
                    normal = e.getNormalWorldOnB();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                agg.add(impulse, point, normal);
            }
        });
    }

    private void ensureTickListenerBound(PhysicsSpace sp) {
        if (!this.tickListenerBound.compareAndSet(false, true)) {
            return;
        }
        sp.addTickListener(new PhysicsTickListener(){

            @Override
            public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                PhysicsCollisionPipeline.this.svc.flushPendingAddNow();
            }

            @Override
            public void physicsTick(PhysicsSpace space, float timeStep) {
                try {
                    PhysicsCollisionPipeline.this.flush(timeStep);
                }
                catch (Throwable t) {
                    PhysicsCollisionPipeline.this.log.error("[physics] collision pipeline flush failed", t);
                }
            }
        });
    }

    private void flush(float dt) {
        long step;
        this.lastDt = dt;
        this.currentStep = step = this.stepCounter.incrementAndGet();
        this.currPairs.forEach(this.emitBeginConsumer);
        this.currPairs.forEach(this.emitStayConsumer);
        this.prevPairs.forEach(this.emitEndConsumer);
        LongHashSet tmp = this.prevPairs;
        this.prevPairs = this.currPairs;
        this.currPairs = tmp;
        this.currPairs.clear();
        this.currContacts.clear();
        this.bus().emit("engine.physics.postStep", (Object)PhysicsLua.evtLua("step", step, "dt", Float.valueOf(dt)));
        PhysicsBodyStateTracker tracker = this.bodyStateTracker;
        if (tracker != null) {
            try {
                tracker.emit(step, dt);
            }
            catch (Throwable t) {
                this.log.error("[physics] body state tracker emit failed", t);
            }
        }
    }

    private LuaObject contactPayload(ContactAgg agg) {
        if (agg == null || agg.points <= 0) {
            return PhysicsLua.evtLua("maxImpulse", Float.valueOf(0.0f), "points", 0, "point", PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(0.0f), "z", Float.valueOf(0.0f)), "normal", PhysicsLua.evtLua("x", Float.valueOf(0.0f), "y", Float.valueOf(1.0f), "z", Float.valueOf(0.0f)));
        }
        float inv = 1.0f / (float)Math.max(1, agg.points);
        float px = agg.sumPx * inv;
        float py = agg.sumPy * inv;
        float pz = agg.sumPz * inv;
        float nx = agg.sumNx * inv;
        float ny = agg.sumNy * inv;
        float nz = agg.sumNz * inv;
        float nLen2 = nx * nx + ny * ny + nz * nz;
        if (nLen2 > 1.0E-12f) {
            float invN = 1.0f / (float)Math.sqrt(nLen2);
            nx *= invN;
            ny *= invN;
            nz *= invN;
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }
        return PhysicsLua.evtLua("maxImpulse", Float.valueOf(agg.maxImpulse), "points", agg.points, "point", PhysicsLua.evtLua("x", Float.valueOf(px), "y", Float.valueOf(py), "z", Float.valueOf(pz)), "normal", PhysicsLua.evtLua("x", Float.valueOf(nx), "y", Float.valueOf(ny), "z", Float.valueOf(nz)));
    }

    private void emitCollision(String topic, long step, float dt, long k, ContactAgg agg) {
        RigidBodyControl ra;
        int aId = CollisionPairKey.keyA(k);
        int bId = CollisionPairKey.keyB(k);
        PhysicsBodyHandle a = this.registry().get(aId);
        PhysicsBodyHandle b = this.registry().get(bId);
        if (a == null || b == null) {
            return;
        }
        try {
            ra = a.__raw();
        }
        catch (Throwable ignored) {
            ra = null;
        }
        try {
            RigidBodyControl rb = b.__raw();
        }
        finally {
            Spatial sa = null;
        }
    }

    private void emitImpact(long step, float dt, long k, ContactAgg agg) {
        RigidBodyControl ra;
        if (agg == null || agg.points <= 0) {
            return;
        }
        int aId = CollisionPairKey.keyA(k);
        int bId = CollisionPairKey.keyB(k);
        PhysicsBodyHandle a = this.registry().get(aId);
        PhysicsBodyHandle b = this.registry().get(bId);
        if (a == null || b == null) {
            return;
        }
        try {
            ra = a.__raw();
        }
        catch (Throwable ignored) {
            ra = null;
        }
        try {
            RigidBodyControl rb = b.__raw();
        }
        finally {
            Spatial sa = null;
        }
    }

    public void reset() {
        this.currPairs.clear();
        this.prevPairs.clear();
        this.currContacts.clear();
        this.lastDt = 0.0f;
        this.currentStep = 0L;
        this.stepCounter.set(0L);
    }
}

