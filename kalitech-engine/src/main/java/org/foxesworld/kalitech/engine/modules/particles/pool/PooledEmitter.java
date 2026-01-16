// FILE: org/foxesworld/kalitech/engine/modules/particles/pool/PooledEmitter.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.pool;

import com.jme3.effect.ParticleEmitter;

/**
 * Wrapper for pooled emitter.
 */
public final class PooledEmitter {

    private final ParticleEmitter emitter;
    private boolean disposed;

    public PooledEmitter(ParticleEmitter emitter) {
        this.emitter = emitter;
    }

    public ParticleEmitter emitter() {
        return emitter;
    }

    public boolean disposed() {
        return disposed;
    }

    public void resetForPool() {
        if (disposed) return;
        emitter.setParticlesPerSec(0f);
        emitter.killAllParticles();
        emitter.setEnabled(false);
        emitter.removeFromParent();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            emitter.removeFromParent();
        } catch (Throwable ignored) {
        }
    }
}
