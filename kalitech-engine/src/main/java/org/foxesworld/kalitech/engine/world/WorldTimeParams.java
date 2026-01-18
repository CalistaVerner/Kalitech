// FILE: org/foxesworld/kalitech/engine/world/WorldTimeParams.java
package org.foxesworld.kalitech.engine.world;

/**
 * Immutable configuration for world time.
 *
 * <p>Units:</p>
 * <ul>
 *   <li>worldTimeSec: absolute game seconds since day 0 midnight</li>
 *   <li>daySeconds: game seconds per one day (default 86400)</li>
 *   <li>dayLengthSec: real seconds per one game day (if set, overrides timeRate)</li>
 *   <li>timeRate: multiplier applied to real dt (gameSec += realSec * timeRate)</li>
 * </ul>
 *
 * <p>Start time:</p>
 * <ul>
 *   <li>If {@code timeOfDaySec} is provided, start time is computed as
 *       {@code dayIndex * daySeconds + (timeOfDaySec mod daySeconds)}</li>
 *   <li>Otherwise {@code worldTimeSec} is used</li>
 * </ul>
 */
public final class WorldTimeParams {

    public static final double DEFAULT_DAY_SECONDS = 86_400.0;

    public final double worldTimeSec;
    public final double timeRate;
    public final boolean paused;

    public final Double fixedStepSec;
    public final Double maxDeltaSec;

    public final double daySeconds;
    public final Double dayLengthSec;

    public final Integer dayIndex;
    public final Double timeOfDaySec;

    public WorldTimeParams(
            double worldTimeSec,
            double timeRate,
            boolean paused,
            Double fixedStepSec,
            Double maxDeltaSec,
            double daySeconds,
            Double dayLengthSec,
            Integer dayIndex,
            Double timeOfDaySec
    ) {
        this.daySeconds = sanitizeDaySeconds(daySeconds);
        this.dayLengthSec = normalizeOptionalPositive(dayLengthSec);

        double rate = sanitizeRate(timeRate);
        if (this.dayLengthSec != null) {
            rate = this.daySeconds / this.dayLengthSec.doubleValue();
            rate = sanitizeRate(rate);
        }
        this.timeRate = rate;

        this.paused = paused;
        this.fixedStepSec = normalizeOptionalPositive(fixedStepSec);
        this.maxDeltaSec = normalizeOptionalPositive(maxDeltaSec);

        this.dayIndex = normalizeOptionalNonNegativeInt(dayIndex);
        this.timeOfDaySec = normalizeOptionalFinite(timeOfDaySec);

        double wt = sanitizeFinite(worldTimeSec, 0.0);
        if (this.timeOfDaySec != null) {
            int d = (this.dayIndex != null) ? this.dayIndex.intValue() : 0;
            double tod = modPositive(this.timeOfDaySec.doubleValue(), this.daySeconds);
            wt = (double) d * this.daySeconds + tod;
        }
        this.worldTimeSec = wt;
    }

    public static WorldTimeParams defaults() {
        return new WorldTimeParams(
                0.0,
                1.0,
                false,
                null,
                0.25,
                DEFAULT_DAY_SECONDS,
                null,
                null,
                null
        );
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

    private static double sanitizeDaySeconds(double v) {
        if (!Double.isFinite(v) || v <= 0.0) return DEFAULT_DAY_SECONDS;
        if (v < 60.0) return 60.0;
        if (v > 10_000_000.0) return 10_000_000.0;
        return v;
    }

    private static Double normalizeOptionalPositive(Double v) {
        if (v == null) return null;
        double x = v.doubleValue();
        if (!Double.isFinite(x) || x <= 0.0) return null;
        return x;
    }

    private static Double normalizeOptionalFinite(Double v) {
        if (v == null) return null;
        double x = v.doubleValue();
        if (!Double.isFinite(x)) return null;
        return x;
    }

    private static Integer normalizeOptionalNonNegativeInt(Integer v) {
        if (v == null) return null;
        int x = v.intValue();
        return (x < 0) ? null : x;
    }

    private static double modPositive(double x, double m) {
        if (!Double.isFinite(x) || !Double.isFinite(m) || m <= 0.0) return 0.0;
        double r = x % m;
        if (r < 0.0) r += m;
        return r;
    }

    @Override
    public String toString() {
        return "WorldTimeParams{" +
                "worldTimeSec=" + worldTimeSec +
                ", timeRate=" + timeRate +
                ", paused=" + paused +
                ", fixedStepSec=" + fixedStepSec +
                ", maxDeltaSec=" + maxDeltaSec +
                ", daySeconds=" + daySeconds +
                ", dayLengthSec=" + dayLengthSec +
                ", dayIndex=" + dayIndex +
                ", timeOfDaySec=" + timeOfDaySec +
                '}';
    }
}