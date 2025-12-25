// FILE: org/foxesworld/kalitech/engine/api/interfaces/PhysicsRayHit.java
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.graalvm.polyglot.HostAccess;

@SuppressWarnings("unused")
public final class PhysicsRayHit {

    public final int bodyId;      // our handle id
    public final int surfaceId;   // surface that owns body
    public final float fraction;  // [0..1]
    public final Vec3 hitPoint;
    public final Vec3 hitNormal;

    public PhysicsRayHit(int bodyId, int surfaceId, float fraction, Vec3 hitPoint, Vec3 hitNormal) {
        this.bodyId = bodyId;
        this.surfaceId = surfaceId;
        this.fraction = fraction;
        this.hitPoint = hitPoint;
        this.hitNormal = hitNormal;
    }

    /** Tiny vec class JS-friendly (plain fields). */
    public static final class Vec3 {
        public final float x, y, z;
        public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        @HostAccess.Export public float x() { return x; }
        @HostAccess.Export public float y() { return y; }
        @HostAccess.Export public float z() { return z; }
    }
}
