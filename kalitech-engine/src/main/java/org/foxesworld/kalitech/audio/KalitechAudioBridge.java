package org.foxesworld.kalitech.audio;

import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.Listener;
import com.jme3.audio.ListenerParam;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/** Syncs Kalitech camera pose -> jME listener each frame. */
public final class KalitechAudioBridge {

    private KalitechAudioBridge() {}

    public static void syncListener(Vector3f worldPos, Quaternion worldRot) {
        syncListener(worldPos, worldRot, null);
    }

    public static void syncListener(Vector3f worldPos, Quaternion worldRot, Vector3f worldVel) {
        AudioRenderer r = AudioContext.getAudioRenderer();
        if (r == null) return;

        Listener l = ListenerRegistry.getOrCreate();

        if (worldPos != null) l.setLocation(worldPos);
        if (worldRot != null) l.setRotation(worldRot);
        if (worldVel != null) l.setVelocity(worldVel);

        r.updateListenerParam(l, ListenerParam.Position);
        r.updateListenerParam(l, ListenerParam.Rotation);
        if (worldVel != null) r.updateListenerParam(l, ListenerParam.Velocity);
    }
}