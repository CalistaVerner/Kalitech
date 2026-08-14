/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.interfaces.physics;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class PhysicsRayHit {
    public final int bodyId;
    public final int surfaceId;
    public final float fraction;
    public final Vec3 hitPoint;
    public final Vec3 hitNormal;

    public PhysicsRayHit(int bodyId, int surfaceId, float fraction, Vec3 hitPoint, Vec3 hitNormal) {
        this.bodyId = bodyId;
        this.surfaceId = surfaceId;
        this.fraction = fraction;
        this.hitPoint = hitPoint;
        this.hitNormal = hitNormal;
    }

    public static final class Vec3 {
        public final float x;
        public final float y;
        public final float z;

        public Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @LuaExport
        public float x() {
            return this.x;
        }

        @LuaExport
        public float y() {
            return this.y;
        }

        @LuaExport
        public float z() {
            return this.z;
        }
    }
}

