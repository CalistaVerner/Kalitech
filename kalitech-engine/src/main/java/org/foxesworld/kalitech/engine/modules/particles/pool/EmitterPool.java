// FILE: org/foxesworld/kalitech/engine/modules/particles/pool/EmitterPool.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.pool;

import com.jme3.effect.ParticleEmitter;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Simple emitter pool for zero-allocation bursts.
 * Pooling is optional and enabled by config.
 */
public final class EmitterPool {

    private final ArrayDeque<PooledEmitter> free = new ArrayDeque<>();
    private final int hardCap;

    public EmitterPool(int hardCap) {
        this.hardCap = Math.max(1, hardCap);
    }

    public int freeCount() {
        return free.size();
    }

    public void warm(int count, Factory factory) {
        Objects.requireNonNull(factory, "factory");
        int n = Math.min(count, hardCap - free.size());
        for (int i = 0; i < n; i++) {
            ParticleEmitter em = factory.create();
            free.addLast(new PooledEmitter(em));
        }
    }

    public PooledEmitter acquire(Factory factory) {
        Objects.requireNonNull(factory, "factory");
        PooledEmitter pe = free.pollLast();
        if (pe != null) return pe;
        return new PooledEmitter(factory.create());
    }

    public void release(PooledEmitter emitter) {
        if (emitter == null) return;
        emitter.resetForPool();
        if (free.size() < hardCap) {
            free.addLast(emitter);
        } else {
            emitter.dispose();
        }
    }

    public interface Factory {
        ParticleEmitter create();
    }
}