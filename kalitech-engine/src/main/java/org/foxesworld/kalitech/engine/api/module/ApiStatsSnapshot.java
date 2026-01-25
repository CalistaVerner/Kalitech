package org.foxesworld.kalitech.engine.api.module;

public final class ApiStatsSnapshot {
    public final long calls;
    public final long errors;
    public final long nanosTotal;
    public final long nanosMax;
    public final double avgMicros;
    public final double maxMicros;

    private ApiStatsSnapshot(long calls,
                             long errors,
                             long nanosTotal,
                             long nanosMax,
                             double avgMicros,
                             double maxMicros) {
        this.calls = calls;
        this.errors = errors;
        this.nanosTotal = nanosTotal;
        this.nanosMax = nanosMax;
        this.avgMicros = avgMicros;
        this.maxMicros = maxMicros;
    }

    public static ApiStatsSnapshot from(ApiStats stats) {
        if (stats == null) return null;
        return new ApiStatsSnapshot(
                stats.calls(),
                stats.errors(),
                stats.nanosTotal(),
                stats.nanosMax(),
                stats.avgMicros(),
                stats.maxMicros()
        );
    }
}
