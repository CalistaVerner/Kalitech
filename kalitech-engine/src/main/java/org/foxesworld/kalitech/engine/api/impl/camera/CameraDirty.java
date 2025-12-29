package org.foxesworld.kalitech.engine.api.impl.camera;

import java.util.concurrent.atomic.AtomicInteger;

public final class CameraDirty {
    public static final int LOC = 1;
    public static final int ROT = 2;

    private final AtomicInteger mask = new AtomicInteger(0);

    public void mark(int bits) {
        mask.getAndUpdate(m -> m | bits);
    }

    /** Returns current mask and clears it to zero. */
    public int take() {
        return mask.getAndSet(0);
    }

    public int peek() {
        return mask.get();
    }
}