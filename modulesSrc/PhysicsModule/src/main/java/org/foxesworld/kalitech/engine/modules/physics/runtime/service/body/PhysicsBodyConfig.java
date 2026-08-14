/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service.body;

public final class PhysicsBodyConfig {
    static final float DEFAULT_FRICTION = 0.8f;
    static final float DEFAULT_RESTITUTION = 0.1f;
    static final float DEFAULT_DAMPING_LINEAR = 0.05f;
    static final float DEFAULT_DAMPING_ANGULAR = 0.1f;
    static final float DEFAULT_CCD_MOTION_THRESHOLD = 0.001f;
    static final float DEFAULT_CCD_SWEPT_SPHERE_RADIUS = 0.2f;
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

    PhysicsBodyConfig(int surfaceId, float mass, float friction, float restitution, float dampingLinear, float dampingAngular, boolean kinematic, boolean lockRotation, float ccdMotionThreshold, float ccdSweptSphereRadius, Object colliderCfg) {
        this.surfaceId = surfaceId;
        this.mass = mass;
        this.dynamic = mass > 0.0f;
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
        return this.surfaceId;
    }

    public float getMass() {
        return this.mass;
    }

    public boolean isDynamic() {
        return this.dynamic;
    }

    public float getFriction() {
        return this.friction;
    }

    public float getRestitution() {
        return this.restitution;
    }

    public float getDampingLinear() {
        return this.dampingLinear;
    }

    public float getDampingAngular() {
        return this.dampingAngular;
    }

    public boolean isKinematic() {
        return this.kinematic;
    }

    public boolean isLockRotation() {
        return this.lockRotation;
    }

    public float getCcdMotionThreshold() {
        return this.ccdMotionThreshold;
    }

    public float getCcdSweptSphereRadius() {
        return this.ccdSweptSphereRadius;
    }

    public Object getColliderCfg() {
        return this.colliderCfg;
    }
}

