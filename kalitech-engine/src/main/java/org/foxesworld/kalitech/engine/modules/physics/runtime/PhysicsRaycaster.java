// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsRaycaster.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;
import org.foxesworld.kalitech.engine.modules.physics.util.PhysicsValueParsers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.foxesworld.kalitech.engine.modules.physics.util.PhysicsMath.isFinite;

public final class PhysicsRaycaster {

    private final PhysicsService svc;

    public PhysicsRaycaster(PhysicsService svc) {
        this.svc = svc;
    }

    private static Map<String, Object> hitObj(
            boolean hit,
            int bodyId,
            int surfaceId,
            float fraction,
            float distance,
            Vector3f point,
            Vector3f normal
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("hit", hit);
        m.put("bodyId", bodyId);
        m.put("surfaceId", surfaceId);
        m.put("fraction", fraction);
        m.put("distance", distance);
        m.put("point", new PhysicsRayHit.Vec3(point.x, point.y, point.z));
        m.put("normal", normal == null
                ? new PhysicsRayHit.Vec3(0, 1, 0)
                : new PhysicsRayHit.Vec3(normal.x, normal.y, normal.z));
        return m;
    }

    public PhysicsRayHit raycast(Object cfg) {
        svc.flushPendingAddNow();
        PhysicsSpace space = svc.requireSpace();
        if (cfg == null) throw new IllegalArgumentException("physics.raycast(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return null;

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (f < bestFrac) {
                bestFrac = f;
                best = r;
            }
        }
        if (best == null) return null;

        PhysicsBodyHandle h = svc.registry().findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = (h != null) ? h.id : 0;
        int surfaceId = (h != null) ? h.surfaceId : 0;

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

    public Object raycastEx(Object cfg) {
        svc.flushPendingAddNow();
        PhysicsSpace space = svc.requireSpace();
        if (cfg == null) throw new IllegalArgumentException("physics.raycastEx(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);

        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) {
            return hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;

        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = svc.registry().findHandleByCollisionObject(r.getCollisionObject());
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            if (f < bestFrac) {
                bestFrac = f;
                best = r;
            }
        }

        if (best == null) {
            return hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsBodyHandle bh = svc.registry().findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = (bh != null) ? bh.id : 0;
        int surfaceId = (bh != null) ? bh.surfaceId : 0;

        Vector3f dir = to.subtract(from);
        float rayLen = dir.length();
        Vector3f hitPoint = from.add(dir.mult(bestFrac));
        float distance = rayLen * bestFrac;

        return hitObj(true, bodyId, surfaceId, bestFrac, distance, hitPoint, best.getHitNormalLocal());
    }

    public Object raycastAll(Object cfg) {
        svc.flushPendingAddNow();
        PhysicsSpace space = svc.requireSpace();
        if (cfg == null) throw new IllegalArgumentException("physics.raycastAll(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        int ignoreBodyId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreBodyId"), 0);
        int ignoreSurfaceId = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "ignoreSurfaceId"), 0);

        boolean staticOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "staticOnly"), false);
        boolean dynamicOnly = PhysicsValueParsers.asBool(PhysicsValueParsers.member(cfg, "dynamicOnly"), false);

        int mask = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "mask"), 0);

        int maxHits = (int) PhysicsValueParsers.asNum(PhysicsValueParsers.member(cfg, "maxHits"), 16);
        if (maxHits <= 0) maxHits = 16;
        if (maxHits > 256) maxHits = 256;

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return new Object[0];

        ArrayList<PhysicsRayTestResult> filtered = new ArrayList<>(hits.size());
        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = svc.registry().findHandleByCollisionObject(r.getCollisionObject());
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            filtered.add(r);
        }

        if (filtered.isEmpty()) return new Object[0];

        filtered.sort((a, b) -> Float.compare(a.getHitFraction(), b.getHitFraction()));

        Vector3f dir = to.subtract(from);
        float rayLen = dir.length();
        if (rayLen <= 1e-6f) rayLen = 1e-6f;

        int outN = Math.min(maxHits, filtered.size());
        Object[] out = new Object[outN];

        for (int i = 0; i < outN; i++) {
            PhysicsRayTestResult r = filtered.get(i);
            float frac = r.getHitFraction();

            PhysicsBodyHandle h = svc.registry().findHandleByCollisionObject(r.getCollisionObject());
            int bodyId = (h != null) ? h.id : 0;
            int surfaceId = (h != null) ? h.surfaceId : 0;

            Vector3f hitPoint = from.add(dir.mult(frac));
            float distance = rayLen * frac;

            out[i] = hitObj(true, bodyId, surfaceId, frac, distance, hitPoint, r.getHitNormalLocal());
        }

        return out;
    }

    private boolean passesStaticDynamicFilter(RigidBodyControl rb, boolean staticOnly, boolean dynamicOnly) {
        if (rb == null) return false;
        float mass = rb.getMass();
        boolean dynamic = mass > 0f && !rb.isKinematic();
        boolean stat = !dynamic;
        if (staticOnly && !stat) return false;
        if (dynamicOnly && !dynamic) return false;
        return true;
    }

    private boolean passesMaskFilter(RigidBodyControl rb, int mask) {
        if (mask == 0) return true;
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }
}