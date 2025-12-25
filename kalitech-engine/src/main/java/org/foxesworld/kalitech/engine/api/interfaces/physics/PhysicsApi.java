// FILE: org/foxesworld/kalitech/engine/api/interfaces/PhysicsApi.java
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.graalvm.polyglot.HostAccess;

@SuppressWarnings("unused")
public interface PhysicsApi {

    /**
     * Create & attach rigidbody to a surface.
     *
     * JS:
     *   const rb = engine.physics().body({
     *     surface: surfOrId,
     *     mass: 5,
     *     collider: { type:"box" }, // box|sphere|mesh|capsule|cylinder
     *     kinematic: false,
     *     gravity: {x:0,y:-9.81,z:0}, // optional per-body gravity override (rare)
     *     friction: 0.8,
     *     restitution: 0.1,
     *     damping: { linear:0.05, angular:0.1 }
     *   })
     */
    @HostAccess.Export PhysicsBodyHandle body(Object cfg);

    /** Remove rigidbody by handle or id */
    @HostAccess.Export void remove(Object handleOrId);

    /** Raycast in physics space. Returns PhysicsRayHit or null. */
    @HostAccess.Export PhysicsRayHit raycast(Object cfg);

    /** Enable/disable Bullet debug draw (editor-friendly) */
    @HostAccess.Export void debug(boolean enabled);

    /** Global gravity setter */
    @HostAccess.Export void gravity(Object vec3);
}