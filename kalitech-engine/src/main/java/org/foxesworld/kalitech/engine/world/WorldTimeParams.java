// FILE: org/foxesworld/kalitech/engine/world/WorldTimeParams.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

/**
 * Immutable world time configuration passed during world creation.
 * The script must be the source of truth for initial time settings.
 */
public final class WorldTimeParams {

    public static final double DEFAULT_DAY_LENGTH_SEC = 3600.0;

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

    /**
     * Length of a full day/night cycle in world seconds.
     */
    public final double dayLengthSec;

    /**
     * Offset (in seconds) applied to {@link #worldTime} before wrapping into the day cycle.
     * Use this to align {@code worldTime} to a desired time of day.
     */
    public final double dayOffsetSec;

    public WorldTimeParams(
            double worldTime,
            double timeRate,
            boolean paused,
            Double fixedStep,
            Double maxDelta,
            Double dayLengthSec,
            Double dayOffsetSec
    ) {
        this.worldTime = sanitizeFinite(worldTime, 0.0);
        this.timeRate = sanitizeRate(timeRate);
        this.paused = paused;
        this.fixedStep = normalizeOptionalPositive(fixedStep);
        this.maxDelta = normalizeOptionalPositive(maxDelta);
        this.dayLengthSec = normalizeDayLength(dayLengthSec);
        this.dayOffsetSec = normalizeDayOffset(dayOffsetSec, this.dayLengthSec);
    }

    public static WorldTimeParams defaults() {
        return new WorldTimeParams(
                0.0,
                1.0,
                false,
                null,
                0.1,
                DEFAULT_DAY_LENGTH_SEC,
                DEFAULT_DAY_LENGTH_SEC * 0.5
        );
    }

    public static double resolveDayOffsetSec(
            double worldTimeSec,
            Double dayLengthSec,
            Double dayOffsetSec,
            Double timeOfDayHours
    ) {
        final double length = normalizeDayLength(dayLengthSec);
        double offset;
        if (dayOffsetSec == null && timeOfDayHours == null) {
            offset = (worldTimeSec == 0.0) ? length * 0.5 : 0.0;
        } else {
            offset = (dayOffsetSec != null) ? dayOffsetSec.doubleValue() : 0.0;
        }
        if (timeOfDayHours != null && Double.isFinite(timeOfDayHours)) {
            double hours = timeOfDayHours.doubleValue();
            hours = normalizeHours(hours);
            double targetSec = length * (hours / 24.0);
            double currentSec = wrap(worldTimeSec, length);
            offset = targetSec - currentSec;
        }
        return normalizeDayOffset(offset, length);
    }

    private static double normalizeDayLength(Double v) {
        if (v == null) return DEFAULT_DAY_LENGTH_SEC;
        double length = v.doubleValue();
        if (!Double.isFinite(length) || length <= 0.0) return DEFAULT_DAY_LENGTH_SEC;
        return length;
    }

    private static double normalizeDayOffset(Double v, double length) {
        if (length <= 0.0) return 0.0;
        double offset = (v != null) ? v.doubleValue() : 0.0;
        if (!Double.isFinite(offset)) offset = 0.0;
        return wrap(offset, length);
    }

    private static double normalizeHours(double hours) {
        if (!Double.isFinite(hours)) return 0.0;
        double h = hours % 24.0;
        if (h < 0.0) h += 24.0;
        return h;
    }

    private static double wrap(double value, double length) {
        if (!Double.isFinite(value) || !Double.isFinite(length) || length <= 0.0) return 0.0;
        double m = value % length;
        if (m < 0.0) m += length;
        return m;
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
                ", dayLengthSec=" + dayLengthSec +
                ", dayOffsetSec=" + dayOffsetSec +
                '}';
    }
}
