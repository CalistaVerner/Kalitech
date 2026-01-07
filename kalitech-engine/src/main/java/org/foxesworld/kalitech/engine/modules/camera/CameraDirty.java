package org.foxesworld.kalitech.engine.modules.camera;

import java.util.concurrent.atomic.AtomicInteger;

public final class CameraDirty {
    public static final int LOC = 1;
    public static final int ROT = 2;

    private final AtomicInteger mask = new AtomicInteger(0);

    public void mark(int bits) {
        int prev;
        int next;
        do {
            prev = mask.get();
            next = prev | bits;
            if (prev == next) return;
        } while (!mask.compareAndSet(prev, next));
    }

    public int take() {
        return mask.getAndSet(0);
    }

    public int peek() {
        return mask.get();
    }
}
