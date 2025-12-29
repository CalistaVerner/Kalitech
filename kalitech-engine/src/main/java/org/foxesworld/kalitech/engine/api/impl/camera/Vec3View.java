package org.foxesworld.kalitech.engine.api.impl.camera;

import org.graalvm.polyglot.HostAccess;

/**
 * JS-friendly vector view without per-call allocations.
 * NOTE: this object is reused; treat it as a live view, not a snapshot.
 */
public final class Vec3View {
    private volatile double x;
    private volatile double y;
    private volatile double z;

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @HostAccess.Export public double x() { return x; }
    @HostAccess.Export public double y() { return y; }
    @HostAccess.Export public double z() { return z; }
}