/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsService;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;

public final class PhysicsRaycaster {
    private final PhysicsService svc;
    private final Vector3f tmpFrom = new Vector3f();
    private final Vector3f tmpTo = new Vector3f();
    private final Vector3f tmpDir = new Vector3f();
    private final Vector3f tmpHit = new Vector3f();
    private final ArrayList<PhysicsRayTestResult> tmpFiltered = new ArrayList(64);

    public PhysicsRaycaster(PhysicsService svc) {
        this.svc = svc;
    }

    private static Map<String, Object> hitObj(boolean hit, int bodyId, int surfaceId, float fraction, float distance, Vector3f point, Vector3f normal) {
        HashMap<String, Object> m = new HashMap<String, Object>(10, 1.0f);
        m.put("hit", hit);
        m.put("bodyId", bodyId);
        m.put("surfaceId", surfaceId);
        m.put("fraction", Float.valueOf(fraction));
        m.put("distance", Float.valueOf(distance));
        m.put("point", new PhysicsRayHit.Vec3(point.x, point.y, point.z));
        m.put("normal", normal == null ? new PhysicsRayHit.Vec3(0.0f, 1.0f, 0.0f) : new PhysicsRayHit.Vec3(normal.x, normal.y, normal.z));
        return m;
    }

    public PhysicsRayHit raycast(Object cfg) {
        this.svc.flushPendingAddNow();
        PhysicsSpace space = this.svc.requireSpace();
        if (cfg == null) {
            throw new IllegalArgumentException("physics.raycast(cfg) cfg required");
        }
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "from"), this.tmpFrom, 0.0f, 0.0f, 0.0f);
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "to"), this.tmpTo, 0.0f, 0.0f, 0.0f);
        List<PhysicsRayTestResult> hits = space.rayTest(this.tmpFrom, this.tmpTo);
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!(f < bestFrac)) continue;
            bestFrac = f;
            best = r;
        }
        if (best == null) {
            return null;
        }
        PhysicsBodyHandle h = this.svc.registry().findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = h != null ? h.id : 0;
        int surfaceId = h != null ? h.surfaceId : 0;
        this.tmpDir.set(this.tmpTo).subtractLocal(this.tmpFrom);
        this.tmpHit.set(this.tmpDir).multLocal(bestFrac).addLocal(this.tmpFrom);
        Vector3f n = best.getHitNormalLocal();
        return new PhysicsRayHit(bodyId, surfaceId, bestFrac, new PhysicsRayHit.Vec3(this.tmpHit.x, this.tmpHit.y, this.tmpHit.z), n == null ? new PhysicsRayHit.Vec3(0.0f, 1.0f, 0.0f) : new PhysicsRayHit.Vec3(n.x, n.y, n.z));
    }

    public Object raycastEx(Object cfg) {
        this.svc.flushPendingAddNow();
        PhysicsSpace space = this.svc.requireSpace();
        if (cfg == null) {
            throw new IllegalArgumentException("physics.raycastEx(cfg) cfg required");
        }
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "from"), this.tmpFrom, 0.0f, 0.0f, 0.0f);
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "to"), this.tmpTo, 0.0f, 0.0f, 0.0f);
        int ignoreBodyId = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0.0);
        int ignoreSurfaceId = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0.0);
        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);
        int mask = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0.0);
        List<PhysicsRayTestResult> hits = space.rayTest(this.tmpFrom, this.tmpTo);
        if (hits == null || hits.isEmpty()) {
            return PhysicsRaycaster.hitObj(false, 0, 0, 0.0f, 0.0f, this.tmpFrom, null);
        }
        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            RigidBodyControl rb;
            PhysicsBodyHandle h;
            float f = r.getHitFraction();
            if (!PhysicsMath.isFinite(f) || (h = this.svc.registry().findHandleByCollisionObject(r.getCollisionObject())) == null || ignoreBodyId > 0 && h.id == ignoreBodyId || ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId || !this.passesStaticDynamicFilter(rb = h.__raw(), staticOnly, dynamicOnly) || !this.passesMaskFilter(rb, mask) || !(f < bestFrac)) continue;
            bestFrac = f;
            best = r;
        }
        if (best == null) {
            return PhysicsRaycaster.hitObj(false, 0, 0, 0.0f, 0.0f, this.tmpFrom, null);
        }
        PhysicsBodyHandle bh = this.svc.registry().findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = bh != null ? bh.id : 0;
        int surfaceId = bh != null ? bh.surfaceId : 0;
        this.tmpDir.set(this.tmpTo).subtractLocal(this.tmpFrom);
        float rayLen = this.tmpDir.length();
        this.tmpHit.set(this.tmpDir).multLocal(bestFrac).addLocal(this.tmpFrom);
        float distance = rayLen * bestFrac;
        return PhysicsRaycaster.hitObj(true, bodyId, surfaceId, bestFrac, distance, this.tmpHit, best.getHitNormalLocal());
    }

    public Object raycastAll(Object cfg) {
        List<PhysicsRayTestResult> hits;
        this.svc.flushPendingAddNow();
        PhysicsSpace space = this.svc.requireSpace();
        if (cfg == null) {
            throw new IllegalArgumentException("physics.raycastAll(cfg) cfg required");
        }
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "from"), this.tmpFrom, 0.0f, 0.0f, 0.0f);
        PhysicsValueParsers.vec3Into(PhysicsValueParsers.member(cfg, "to"), this.tmpTo, 0.0f, 0.0f, 0.0f);
        int ignoreBodyId = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0.0);
        int ignoreSurfaceId = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0.0);
        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);
        int mask = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0.0);
        int maxHits = (int)PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "maxHits"), 16.0);
        if (maxHits <= 0) {
            maxHits = 16;
        }
        if (maxHits > 256) {
            maxHits = 256;
        }
        if ((hits = space.rayTest(this.tmpFrom, this.tmpTo)) == null || hits.isEmpty()) {
            return new Object[0];
        }
        this.tmpFiltered.clear();
        for (PhysicsRayTestResult r : hits) {
            RigidBodyControl rb;
            PhysicsBodyHandle h;
            float f = r.getHitFraction();
            if (!PhysicsMath.isFinite(f) || (h = this.svc.registry().findHandleByCollisionObject(r.getCollisionObject())) == null || ignoreBodyId > 0 && h.id == ignoreBodyId || ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId || !this.passesStaticDynamicFilter(rb = h.__raw(), staticOnly, dynamicOnly) || !this.passesMaskFilter(rb, mask)) continue;
            this.tmpFiltered.add(r);
        }
        if (this.tmpFiltered.isEmpty()) {
            return new Object[0];
        }
        this.tmpFiltered.sort((a, b) -> Float.compare(a.getHitFraction(), b.getHitFraction()));
        this.tmpDir.set(this.tmpTo).subtractLocal(this.tmpFrom);
        float rayLen = this.tmpDir.length();
        if (rayLen <= 1.0E-6f) {
            rayLen = 1.0E-6f;
        }
        int outN = Math.min(maxHits, this.tmpFiltered.size());
        Object[] out = new Object[outN];
        for (int i = 0; i < outN; ++i) {
            PhysicsRayTestResult r = this.tmpFiltered.get(i);
            float frac = r.getHitFraction();
            PhysicsBodyHandle h = this.svc.registry().findHandleByCollisionObject(r.getCollisionObject());
            int bodyId = h != null ? h.id : 0;
            int surfaceId = h != null ? h.surfaceId : 0;
            this.tmpHit.set(this.tmpDir).multLocal(frac).addLocal(this.tmpFrom);
            float distance = rayLen * frac;
            out[i] = PhysicsRaycaster.hitObj(true, bodyId, surfaceId, frac, distance, this.tmpHit, r.getHitNormalLocal());
        }
        return out;
    }

    private boolean passesStaticDynamicFilter(RigidBodyControl rb, boolean staticOnly, boolean dynamicOnly) {
        boolean stat;
        if (rb == null) {
            return false;
        }
        float mass = rb.getMass();
        boolean dynamic = mass > 0.0f && !rb.isKinematic();
        boolean bl = stat = !dynamic;
        if (staticOnly && !stat) {
            return false;
        }
        return !dynamicOnly || dynamic;
    }

    private boolean passesMaskFilter(RigidBodyControl rb, int mask) {
        if (mask == 0) {
            return true;
        }
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        }
        catch (Throwable ignored) {
            return true;
        }
    }
}

