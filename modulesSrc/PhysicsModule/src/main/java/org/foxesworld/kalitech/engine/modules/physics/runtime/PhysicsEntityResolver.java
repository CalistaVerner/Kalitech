/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.scene.Spatial
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.scene.Spatial;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

public final class PhysicsEntityResolver {
    private volatile SurfaceRegistry surfaces;

    public static String entityOfSpatial(Spatial sp) {
        Object v2;
        if (sp == null) {
            return null;
        }
        try {
            v2 = sp.getUserData("entityUuid");
            if (v2 != null) {
                return String.valueOf(v2);
            }
        }
        catch (Throwable ignored) {
            // empty catch block
        }
        try {
            v2 = sp.getUserData("uuid");
            if (v2 != null) {
                return String.valueOf(v2);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    public void bind(SurfaceRegistry surfaces) {
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    public void unbind() {
        this.surfaces = null;
    }

    public String entityOfSurface(int surfaceId) {
        if (surfaceId <= 0) {
            return null;
        }
        SurfaceRegistry sr = this.surfaces;
        if (sr == null) {
            return null;
        }
        String uuid = sr.attachedEntityUuid(surfaceId);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return uuid;
    }
}

