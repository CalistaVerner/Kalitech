// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundEventContext.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

public final class SoundEventContext {

    public final long a;
    public final long b;
    public final long c;

    public SoundEventContext(long a, long b, long c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public static SoundEventContext none() {
        return new SoundEventContext(0L, 0L, 0L);
    }
}