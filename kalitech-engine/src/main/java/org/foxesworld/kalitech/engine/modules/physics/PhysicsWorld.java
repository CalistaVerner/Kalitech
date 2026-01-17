package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;

/**
 * World-level operations (not tied to a single body).
 */
final class PhysicsWorld {

    private final PhysicsState S;
    private final PhysicsContacts contacts;

    PhysicsWorld(PhysicsState state, PhysicsContacts contacts) {
        this.S = state;
        this.contacts = contacts;
    }

    void debug(boolean enabled) {
        BulletAppState b = S.app.getStateManager().getState(BulletAppState.class);
        if (b == null) {
            PhysicsState.log.warn("[physics] debug({}) ignored: BulletAppState not attached", enabled);
            return;
        }
        b.setDebugEnabled(enabled);
    }

    void gravity(Object vec3) {
        PhysicsSpace space = S.requireSpace();
        contacts.ensureBound(space);
        Vector3f g = PhysicsValueParsers.vec3(vec3, 0, -9.81f, 0);
        space.setGravity(g);
    }

    void cleanupSurface(int surfaceId) {
        if (surfaceId <= 0) return;
        Integer id = S.bodyIdBySurface.get(surfaceId);
        if (id != null) {
            // remove uses bodies service normally, but to avoid circular dependencies
            // keep a direct minimal remove at world level.
            PhysicsBodyHandle h = S.byId.get(id);
            if (h != null) {
                // direct minimal removal
                removeBodyDirect(id, h);
            }
        }
    }

    void clearAll() {
        S.pendingAdd.clear();
        S.pendingRemove.clear();
        S.shapeCache.clear();
        S.bodyState.clear();

        // contacts live in PhysicsContacts (not in PhysicsState)
        //contacts.currContacts.clear();
        //contacts.prevContacts.clear();
        S.bodyIdByCollisionObject.clear();

        PhysicsSpace sp = S.engine.__getPhysicsSpaceOrNull();
        if (sp == null) {
            S.byId.clear();
            S.bodyIdBySurface.clear();
            return;
        }

        contacts.ensureBound(sp);

        for (PhysicsBodyHandle h : S.byId.values()) {
            if (h == null) continue;

            int surfaceId = h.surfaceId;
            RigidBodyControl rb = h.__raw();

            // Queue removal; actual PhysicsSpace mutation is flushed in PhysicsContacts.prePhysicsTick()
            S.pendingRemove.add(rb);

            try {
                Spatial s = S.surfaces.get(surfaceId);
                if (s != null) s.removeControl(rb);
            } catch (Throwable ignored) {
            }
        }

        S.byId.clear();
        S.bodyIdBySurface.clear();
        S.idByControl.clear();

        PhysicsState.log.info("[physics] cleared all bodies");
    }

    private void removeBodyDirect(int id, PhysicsBodyHandle h) {
        int surfaceId = h.surfaceId;
        RigidBodyControl rb = h.__raw();

        S.byId.remove(id);
        S.bodyState.remove(id);
        S.bodyIdBySurface.remove(surfaceId, id);
        S.idByControl.remove(rb, id);
        S.unindexCollisionObject(h);

        try {
            S.pendingAdd.remove(rb);
        } catch (Throwable ignored) {
        }

        // Queue removal; actual PhysicsSpace mutation is flushed in PhysicsContacts.prePhysicsTick()
        S.pendingRemove.add(rb);

        try {
            Spatial s = S.surfaces.get(surfaceId);
            if (s != null) s.removeControl(rb);
        } catch (Throwable ignored) {
        }
    }
}