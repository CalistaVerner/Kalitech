package org.foxesworld.kalitech.engine.world.physics;

// Author: Calista Verner

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public final class BulletPhysicsAccess implements PhysicsAccess {

    private final PhysicsSpace space;
    private final ConcurrentHashMap<Integer, PhysicsRigidBody> bodies = new ConcurrentHashMap<>();

    public BulletPhysicsAccess(PhysicsSpace space) {
        this.space = space;
    }

    /** Call this when you CREATE bodyId in your PhysicsApiImpl. */
    public void registerBody(int bodyId, PhysicsRigidBody body) {
        if (bodyId <= 0 || body == null) return;
        bodies.put(bodyId, body);
    }

    /** Call this when you DESTROY bodyId. */
    public void unregisterBody(int bodyId) {
        if (bodyId <= 0) return;
        bodies.remove(bodyId);
    }

    @Override
    public boolean getBodyPosition(int bodyId, Vector3f out) {
        if (bodyId <= 0 || out == null) return false;
        PhysicsRigidBody b = bodies.get(bodyId);
        if (b == null) return false;
        b.getPhysicsLocation(out);
        return true;
    }

    @Override
    public float rayFraction(Vector3f from, Vector3f to) {
        List<PhysicsRayTestResult> hits = space.rayTest(from, to);
        if (hits == null || hits.isEmpty()) return 1f;

        float best = 1f;
        for (PhysicsRayTestResult h : hits) {
            float f = h.getHitFraction(); // already 0..1 in jME
            if (f < best) best = f;
        }
        return best;
    }
}