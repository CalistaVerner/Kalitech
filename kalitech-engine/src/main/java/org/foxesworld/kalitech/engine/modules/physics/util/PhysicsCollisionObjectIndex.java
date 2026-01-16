// FILE: org/foxesworld/kalitech/engine/modules/physics/util/PhysicsCollisionObjectIndex.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.util;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.modules.physics.CollisionObjectUtil;

import java.util.concurrent.ConcurrentHashMap;

public final class PhysicsCollisionObjectIndex {

    private final ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject;

    public PhysicsCollisionObjectIndex(ConcurrentHashMap<Object, Integer> bodyIdByCollisionObject) {
        this.bodyIdByCollisionObject = bodyIdByCollisionObject;
    }

    public int bodyIdFromCollisionObject(Object obj) {
        if (obj == null) return 0;

        Integer id = bodyIdByCollisionObject.get(obj);
        if (id != null) return id;

        if (obj instanceof RigidBodyControl rb) {
            PhysicsRigidBody prb = CollisionObjectUtil.extractPhysicsRigidBody(rb);
            if (prb != null) {
                Integer id2 = bodyIdByCollisionObject.get(prb);
                if (id2 != null) return id2;
            }
        }

        return 0;
    }

    public void indexCollisionObject(PhysicsBodyHandle h) {
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.put(key, h.id);
    }

    public void unindexCollisionObject(PhysicsBodyHandle h) {
        Object key = CollisionObjectUtil.collisionKeyFromHandle(h);
        if (key != null) bodyIdByCollisionObject.remove(key, h.id);
    }
}