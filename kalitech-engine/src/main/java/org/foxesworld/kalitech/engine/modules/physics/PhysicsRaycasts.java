package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsRayHit;

import java.util.List;

/**
 * Ray queries.
 *
 * Threading:
 *  - MUST NOT mutate PhysicsSpace.
 *  - Safe to call from game/JS thread.
 */
public final class PhysicsRaycasts {

    private static final ThreadLocal<Temps> TL = ThreadLocal.withInitial(Temps::new);
    private final PhysicsState S;
    private final PhysicsContacts contacts;

    public PhysicsRaycasts(PhysicsState state, PhysicsContacts contacts) {
        this.S = state;
        this.contacts = contacts;
    }

    private static boolean isFinite(float v) {
        return Float.isFinite(v);
    }

    private static boolean passesStaticDynamicFilter(RigidBodyControl rb, boolean staticOnly, boolean dynamicOnly) {
        if (rb == null) return false;
        float mass = rb.getMass();
        boolean dynamic = mass > 0f && !rb.isKinematic();
        boolean stat = !dynamic;
        if (staticOnly && !stat) return false;
        if (dynamicOnly && !dynamic) return false;
        return true;
    }

    private static boolean passesMaskFilter(RigidBodyControl rb, int mask) {
        if (mask == 0) return true;
        try {
            return (rb.getCollideWithGroups() & mask) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private PhysicsBodyHandle findHandleByCollisionObject(Object obj) {
        int id = S.bodyIdFromCollisionObject(obj);
        return (id > 0) ? S.byId.get(id) : null;
    }

    public PhysicsRayHit raycast(Object cfg) {
        PhysicsSpace space = S.requireSpace();
        contacts.ensureBound(space);

        if (cfg == null) throw new IllegalArgumentException("physics.raycast(cfg) cfg required");

        Vector3f from = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "from"), 0, 0, 0);
        Vector3f to = PhysicsValueParsers.vec3(PhysicsValueParsers.member(cfg, "to"), 0, 0, 0);

        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return null;

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;

        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;
            if (f < bestFrac) {
                bestFrac = f;
                best = r;
            }
        }

        if (best == null) return null;

        int bodyId = 0;
        int surfaceId = 0;
        PhysicsBodyHandle h = findHandleByCollisionObject(best.getCollisionObject());
        if (h != null) {
            bodyId = h.id;
            surfaceId = h.surfaceId;
        }

        Temps t = TL.get();
        t.dir.set(to).subtractLocal(from);
        t.hit.set(t.dir).multLocal(bestFrac).addLocal(from);

        Vector3f n = best.getHitNormalLocal();
        PhysicsRayHit.Vec3 nn = (n == null) ? new PhysicsRayHit.Vec3(0, 1, 0) : new PhysicsRayHit.Vec3(n.x, n.y, n.z);

        return new PhysicsRayHit(
                bodyId,
                surfaceId,
                bestFrac,
                new PhysicsRayHit.Vec3(t.hit.x, t.hit.y, t.hit.z),
                nn
        );
    }

    public Object raycastEx(Object cfg) {
        PhysicsSpace space = S.requireSpace();
        contacts.ensureBound(space);

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
            return PhysicsState.hitObj(false, 0, 0, 0f, 0f, from, null);
        }

        PhysicsRayTestResult best = null;
        float bestFrac = Float.POSITIVE_INFINITY;

        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = findHandleByCollisionObject(r.getCollisionObject());
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

        if (best == null) return PhysicsState.hitObj(false, 0, 0, 0f, 0f, from, null);

        PhysicsBodyHandle bh = findHandleByCollisionObject(best.getCollisionObject());
        int bodyId = (bh != null) ? bh.id : 0;
        int surfaceId = (bh != null) ? bh.surfaceId : 0;

        Temps t = TL.get();
        t.dir.set(to).subtractLocal(from);
        float rayLen = t.dir.length();
        if (rayLen <= 1e-6f) rayLen = 1e-6f;

        t.hit.set(t.dir).multLocal(bestFrac).addLocal(from);
        float distance = rayLen * bestFrac;

        return PhysicsState.hitObj(true, bodyId, surfaceId, bestFrac, distance, t.hit, best.getHitNormalLocal());
    }

    public Object raycastAll(Object cfg) {
        PhysicsSpace space = S.requireSpace();
        contacts.ensureBound(space);

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

        // Select up to maxHits best by fraction without sorting whole list.
        PhysicsRayTestResult[] best = new PhysicsRayTestResult[maxHits];
        float[] fracs = new float[maxHits];
        int n = 0;

        for (PhysicsRayTestResult r : hits) {
            float f = r.getHitFraction();
            if (!isFinite(f)) continue;

            PhysicsBodyHandle h = findHandleByCollisionObject(r.getCollisionObject());
            if (h == null) continue;

            if (ignoreBodyId > 0 && h.id == ignoreBodyId) continue;
            if (ignoreSurfaceId > 0 && h.surfaceId == ignoreSurfaceId) continue;

            RigidBodyControl rb = h.__raw();
            if (!passesStaticDynamicFilter(rb, staticOnly, dynamicOnly)) continue;
            if (!passesMaskFilter(rb, mask)) continue;

            // insert into small sorted arrays (ascending)
            int ins = n;
            if (ins < maxHits) {
                best[ins] = r;
                fracs[ins] = f;
                n++;
            } else {
                // array full; if worse than worst -> skip
                if (f >= fracs[maxHits - 1]) continue;
                best[maxHits - 1] = r;
                fracs[maxHits - 1] = f;
                ins = maxHits;
            }

            // insertion sort step
            int i = Math.min(n, maxHits) - 1;
            while (i > 0 && fracs[i] < fracs[i - 1]) {
                float tf = fracs[i - 1];
                fracs[i - 1] = fracs[i];
                fracs[i] = tf;

                PhysicsRayTestResult tr = best[i - 1];
                best[i - 1] = best[i];
                best[i] = tr;
                i--;
            }
        }

        if (n == 0) return new Object[0];
        int outN = Math.min(n, maxHits);

        Temps t = TL.get();
        t.dir.set(to).subtractLocal(from);
        float rayLen = t.dir.length();
        if (rayLen <= 1e-6f) rayLen = 1e-6f;

        Object[] out = new Object[outN];
        for (int i = 0; i < outN; i++) {
            PhysicsRayTestResult r = best[i];
            float frac = fracs[i];

            PhysicsBodyHandle h = findHandleByCollisionObject(r.getCollisionObject());
            int bodyId = (h != null) ? h.id : 0;
            int surfaceId = (h != null) ? h.surfaceId : 0;

            t.hit.set(t.dir).multLocal(frac).addLocal(from);
            float distance = rayLen * frac;

            out[i] = PhysicsState.hitObj(true, bodyId, surfaceId, frac, distance, t.hit, r.getHitNormalLocal());
        }

        return out;
    }

    private static final class Temps {
        final Vector3f dir = new Vector3f();
        final Vector3f hit = new Vector3f();
    }
}