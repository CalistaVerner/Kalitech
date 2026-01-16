// FILE: org/foxesworld/kalitech/engine/modules/particles/scalability/FxScalability.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.scalability;

import java.util.Objects;

/**
 * Scalability + budgets for particle FX.
 * Designed to degrade gracefully under load without breaking gameplay.
 */
public final class FxScalability {

    private final Budget low = new Budget();
    private final Budget medium = new Budget();
    private final Budget high = new Budget();
    private final Budget ultra = new Budget();
    private FxQuality quality = FxQuality.AUTO;

    public FxScalability() {
        low.maxAliveEmitters = 128;
        low.maxSpawnParticlesPerFrame = 2048;
        low.globalCullDistanceMeters = 80f;

        medium.maxAliveEmitters = 256;
        medium.maxSpawnParticlesPerFrame = 4096;
        medium.globalCullDistanceMeters = 110f;

        high.maxAliveEmitters = 512;
        high.maxSpawnParticlesPerFrame = 8192;
        high.globalCullDistanceMeters = 140f;

        ultra.maxAliveEmitters = 1024;
        ultra.maxSpawnParticlesPerFrame = 16384;
        ultra.globalCullDistanceMeters = 180f;
    }

    public FxQuality getQuality() {
        return quality;
    }

    public void setQuality(FxQuality quality) {
        this.quality = Objects.requireNonNull(quality, "quality");
    }

    public Budget active() {
        return switch (quality) {
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case ULTRA -> ultra;
            case AUTO -> high;
        };
    }

    public static final class Budget {
        public int maxAliveEmitters = 512;
        public int maxSpawnParticlesPerFrame = 8192;
        public float globalCullDistanceMeters = 140f;
    }
}
