// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsBodyConfig.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

/**
 * Parsed and validated body configuration.
 */
public final class PhysicsBodyConfig {

    static final float DEFAULT_FRICTION = 0.8f;
    static final float DEFAULT_RESTITUTION = 0.1f;
    static final float DEFAULT_DAMPING_LINEAR = 0.05f;
    static final float DEFAULT_DAMPING_ANGULAR = 0.1f;

    static final float DEFAULT_CCD_MOTION_THRESHOLD = 0.001f;
    static final float DEFAULT_CCD_SWEPT_SPHERE_RADIUS = 0.20f;

    final int surfaceId;

    final float mass;
    final boolean dynamic;

    final float friction;
    final float restitution;

    final float dampingLinear;
    final float dampingAngular;

    final boolean kinematic;
    final boolean lockRotation;

    final float ccdMotionThreshold;
    final float ccdSweptSphereRadius;

    final Object colliderCfg;

    PhysicsBodyConfig(
            int surfaceId,
            float mass,
            float friction,
            float restitution,
            float dampingLinear,
            float dampingAngular,
            boolean kinematic,
            boolean lockRotation,
            float ccdMotionThreshold,
            float ccdSweptSphereRadius,
            Object colliderCfg
    ) {
        this.surfaceId = surfaceId;
        this.mass = mass;
        this.dynamic = mass > 0f;
        this.friction = friction;
        this.restitution = restitution;
        this.dampingLinear = dampingLinear;
        this.dampingAngular = dampingAngular;
        this.kinematic = kinematic;
        this.lockRotation = lockRotation;
        this.ccdMotionThreshold = ccdMotionThreshold;
        this.ccdSweptSphereRadius = ccdSweptSphereRadius;
        this.colliderCfg = colliderCfg;
    }

    public int getSurfaceId() {
        return surfaceId;
    }

    public float getMass() {
        return mass;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public float getFriction() {
        return friction;
    }

    public float getRestitution() {
        return restitution;
    }

    public float getDampingLinear() {
        return dampingLinear;
    }

    public float getDampingAngular() {
        return dampingAngular;
    }

    public boolean isKinematic() {
        return kinematic;
    }

    public boolean isLockRotation() {
        return lockRotation;
    }

    public float getCcdMotionThreshold() {
        return ccdMotionThreshold;
    }

    public float getCcdSweptSphereRadius() {
        return ccdSweptSphereRadius;
    }

    public Object getColliderCfg() {
        return colliderCfg;
    }
}
