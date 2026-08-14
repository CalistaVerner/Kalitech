/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.camera;

import java.util.concurrent.atomic.AtomicInteger;

public final class CameraDirty {
    public static final int LOC = 1;
    public static final int ROT = 2;
    private final AtomicInteger mask = new AtomicInteger(0);

    public void mark(int bits) {
        int next;
        int prev;
        do {
            if ((prev = this.mask.get()) != (next = prev | bits)) continue;
            return;
        } while (!this.mask.compareAndSet(prev, next));
    }

    public int take() {
        return this.mask.getAndSet(0);
    }

    public int peek() {
        return this.mask.get();
    }
}

