package org.foxesworld.kalitech.audio;

import com.jme3.audio.Listener;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public record ListenerSnapshot(Vector3f position, Vector3f forward, Vector3f up) {

    public static ListenerSnapshot fallback() {
        return new ListenerSnapshot(Vector3f.ZERO, Vector3f.UNIT_Z, Vector3f.UNIT_Y);
    }

    public static ListenerSnapshot tryRead() {
        try {
            Listener l = ListenerRegistry.get();
            if (l == null) return fallback();

            Vector3f pos = l.getLocation() != null ? l.getLocation().clone() : Vector3f.ZERO;
            Quaternion rot = l.getRotation() != null ? l.getRotation() : Quaternion.IDENTITY;

            Vector3f fwd = rot.mult(Vector3f.UNIT_Z);
            Vector3f up = rot.mult(Vector3f.UNIT_Y);

            if (!StereoMath.isFinite(fwd) || fwd.lengthSquared() < 1e-8f) fwd = Vector3f.UNIT_Z.clone();
            if (!StereoMath.isFinite(up)  || up.lengthSquared()  < 1e-8f) up  = Vector3f.UNIT_Y.clone();

            fwd.normalizeLocal();
            up.normalizeLocal();

            return new ListenerSnapshot(pos, fwd, up);
        } catch (Throwable ignored) {
            return fallback();
        }
    }
}