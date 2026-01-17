// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/PhysicsEntityResolver.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime;

import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

import java.util.Objects;

/**
 * Resolves engine entity identifiers from surfaces/spatials.
 *
 * <p>Central place for mapping surfaceId -> entity id/uuid string.</p>
 */
public final class PhysicsEntityResolver {

    private volatile SurfaceRegistry surfaces;

    public PhysicsEntityResolver() {
    }

    /**
     * Returns entity identifier from spatial userData (entityUuid/entityId/uuid) or null.
     */
    public static String entityOfSpatial(Spatial sp) {
        if (sp == null) return null;

        try {
            Object v = sp.getUserData("entityUuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("entityId");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("uuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        return null;
    }

    public void bind(SurfaceRegistry surfaces) {
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    public void unbind() {
        this.surfaces = null;
    }

    /**
     * Returns entity identifier for a surfaceId or null if not available.
     */
    public String entityOfSurface(int surfaceId) {
        if (surfaceId <= 0) return null;

        SurfaceRegistry sr = this.surfaces;
        if (sr == null) return null;

        Spatial sp;
        try {
            sp = sr.get(surfaceId);
        } catch (Throwable ignored) {
            return null;
        }
        return entityOfSpatial(sp);
    }
}