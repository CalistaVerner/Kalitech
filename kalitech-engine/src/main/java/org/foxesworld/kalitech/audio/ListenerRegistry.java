package org.foxesworld.kalitech.audio;

import com.jme3.audio.Listener;

/** Stores the current Listener used by Kalitech audio. */
public final class ListenerRegistry {
    private static volatile Listener current;

    private ListenerRegistry() {}

    public static Listener getOrCreate() {
        Listener l = current;
        if (l == null) {
            l = new Listener();
            current = l;
        }
        return l;
    }

    public static Listener get() {
        return current;
    }

    public static void set(Listener listener) {
        current = listener;
    }
}