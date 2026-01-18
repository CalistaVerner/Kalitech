// FILE: org/foxesworld/kalitech/engine/world/WorldTimeParams.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

/**
 * Immutable world time configuration passed during world creation.
 * The script must be the source of truth for initial time settings.
 */
public final class WorldTimeParams {

    public final double worldTime;
    public final double timeRate;
    public final boolean paused;

    /**
     * Fixed step in seconds. If null or <= 0, variable step is used.
     */
    public final Double fixedStep;

    /**
     * Maximum allowed delta in seconds (clamp). If null or <= 0, no clamp is applied.
     */
    public final Double maxDelta;

    public WorldTimeParams(double worldTime, double timeRate, boolean paused, Double fixedStep, Double maxDelta) {
        this.worldTime = sanitizeFinite(worldTime, 0.0);
        this.timeRate = sanitizeRate(timeRate);
        this.paused = paused;
        this.fixedStep = normalizeOptionalPositive(fixedStep);
        this.maxDelta = normalizeOptionalPositive(maxDelta);
    }

    public static WorldTimeParams defaults() {
        return new WorldTimeParams(0.0, 1.0, false, null, 0.1);
    }

    private static double sanitizeRate(double v) {
        if (!Double.isFinite(v)) return 1.0;
        if (v < 0.0) return 0.0;
        if (v > 1_000.0) return 1_000.0;
        return v;
    }

    private static double sanitizeFinite(double v, double def) {
        return Double.isFinite(v) ? v : def;
    }

    private static Double normalizeOptionalPositive(Double v) {
        if (v == null) return null;
        double x = v.doubleValue();
        if (!Double.isFinite(x) || x <= 0.0) return null;
        return x;
    }

    @Override
    public String toString() {
        return "WorldTimeParams{" +
                "worldTime=" + worldTime +
                ", timeRate=" + timeRate +
                ", paused=" + paused +
                ", fixedStep=" + fixedStep +
                ", maxDelta=" + maxDelta +
                '}';
    }
}