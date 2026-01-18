// FILE: org/foxesworld/kalitech/engine/world/WorldTime.java
package org.foxesworld.kalitech.engine.world;

/**
 * Mutable world time state in game seconds.
 * Supports calendar view (24h day) and simulation telemetry.
 */
public final class WorldTime {

    private double worldTimeSec;
    private double timeRate;
    private boolean paused;

    private final Double fixedStepSec;
    private final Double maxDeltaSec;

    private final double daySeconds;
    private final Double dayLengthSec;

    private double accumulatorSec;

    private long frameIndex;
    private long tickIndex;

    private double lastRealDtSec;
    private double lastSimDtSec;
    private double lastStepDtSec;

    public WorldTime(WorldTimeParams params) {
        WorldTimeParams p = (params != null) ? params : WorldTimeParams.defaults();

        this.worldTimeSec = p.worldTimeSec;
        this.timeRate = p.timeRate;
        this.paused = p.paused;

        this.fixedStepSec = p.fixedStepSec;
        this.maxDeltaSec = p.maxDeltaSec;

        this.daySeconds = p.daySeconds;
        this.dayLengthSec = p.dayLengthSec;

        this.accumulatorSec = 0.0;

        this.frameIndex = 0L;
        this.tickIndex = 0L;

        this.lastRealDtSec = 0.0;
        this.lastSimDtSec = 0.0;
        this.lastStepDtSec = 0.0;
    }

    // ---------------------------------------------------------------------
    // Base getters
    // ---------------------------------------------------------------------

    private static double sanitizeNonNegFinite(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(v, 0.0);
    }

    private static double sanitizeRate(double v) {
        if (!Double.isFinite(v)) return 1.0;
        if (v < 0.0) return 0.0;
        if (v > 1_000.0) return 1_000.0;
        return v;
    }

    public double getWorldTimeSec() {
        return worldTimeSec;
    }

    public void setWorldTimeSec(double worldTimeSec) {
        if (!Double.isFinite(worldTimeSec)) return;
        this.worldTimeSec = worldTimeSec;
    }

    public Double fixedStepSec() {
        return fixedStepSec;
    }

    public double getTimeRate() {
        return timeRate;
    }

    public void setTimeRate(double timeRate) {
        this.timeRate = sanitizeRate(timeRate);
    }

    public boolean isPaused() {
        return paused;
    }

    // ---------------------------------------------------------------------
    // Telemetry (required by your exported accessors)
    // ---------------------------------------------------------------------

    public long frameIndex() {
        return frameIndex;
    }

    public long tickIndex() {
        return tickIndex;
    }

    public double lastRealDtSec() {
        return lastRealDtSec;
    }

    public double lastSimDtSec() {
        return lastSimDtSec;
    }

    public double lastStepDtSec() {
        return lastStepDtSec;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Double getMaxDeltaSec() {
        return maxDeltaSec;
    }

    public double daySeconds() {
        return daySeconds;
    }

    // ---------------------------------------------------------------------
    // Calendar view (24h day)
    // ---------------------------------------------------------------------

    public Double dayLengthSec() {
        return dayLengthSec;
    }

    public Double effectiveDayLengthSec() {
        if (daySeconds <= 0.0) return null;
        if (Double.isFinite(timeRate) && timeRate > 0.0) {
            double length = daySeconds / timeRate;
            if (Double.isFinite(length) && length > 0.0) return length;
        }
        if (dayLengthSec != null && Double.isFinite(dayLengthSec) && dayLengthSec > 0.0) {
            return dayLengthSec;
        }
        return null;
    }

    /**
     * Call once per rendered frame.
     */
    public void markFrame(double realDtSec, double simDtSec, double stepDtSec) {
        this.frameIndex++;
        this.lastRealDtSec = sanitizeNonNegFinite(realDtSec);
        this.lastSimDtSec = sanitizeNonNegFinite(simDtSec);
        this.lastStepDtSec = sanitizeNonNegFinite(stepDtSec);
    }

    public void seek(double newWorldTimeSec) {
        if (!Double.isFinite(newWorldTimeSec)) return;
        this.worldTimeSec = newWorldTimeSec;
        this.accumulatorSec = 0.0;
        //this.lastWorldTimeBefore = newWorldTimeSec;
        //this.lastWorldTimeAfter = newWorldTimeSec;
    }

    /**
     * Call once per simulation tick (especially when fixedStep is used).
     */
    public void markTick() {
        this.tickIndex++;
    }

    public int dayIndex() {
        if (daySeconds <= 0.0) return 0;
        double d = Math.floor(worldTimeSec / daySeconds);
        if (!Double.isFinite(d)) return 0;
        if (d > (double) Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (d < (double) Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) d;
    }

    public double timeOfDaySec() {
        if (daySeconds <= 0.0) return 0.0;
        double r = worldTimeSec % daySeconds;
        if (r < 0.0) r += daySeconds;
        return Double.isFinite(r) ? r : 0.0;
    }

    // ---------------------------------------------------------------------
    // Simulation helpers
    // ---------------------------------------------------------------------

    public double timeOfDay01() {
        if (daySeconds <= 0.0) return 0.0;
        return timeOfDaySec() / daySeconds;
    }

    public int hour() {
        double t = timeOfDaySec();
        int h = (int) Math.floor(t / 3600.0);
        if (h < 0) return 0;
        if (h > 23) return 23;
        return h;
    }

    public int minute() {
        double t = timeOfDaySec();
        int m = (int) Math.floor((t % 3600.0) / 60.0);
        if (m < 0) return 0;
        if (m > 59) return 59;
        return m;
    }

    public int second() {
        double t = timeOfDaySec();
        int s = (int) Math.floor(t % 60.0);
        if (s < 0) return 0;
        if (s > 59) return 59;
        return s;
    }

    public double accumulatorSec() {
        return accumulatorSec;
    }

    public void addAccumulator(double dtSec) {
        if (dtSec <= 0.0 || !Double.isFinite(dtSec)) return;
        accumulatorSec += dtSec;
        if (accumulatorSec < 0.0) accumulatorSec = 0.0;
    }

    public void consumeAccumulator(double dtSec) {
        if (dtSec <= 0.0 || !Double.isFinite(dtSec)) return;
        accumulatorSec -= dtSec;
        if (accumulatorSec < 0.0) accumulatorSec = 0.0;
    }

    public void advanceWorldTime(double dtGameSec) {
        if (dtGameSec <= 0.0 || !Double.isFinite(dtGameSec)) return;
        worldTimeSec += dtGameSec;
    }

    public double clampRealDt(double realDtSec) {
        double dt = sanitizeNonNegFinite(realDtSec);
        if (dt <= 0.0) return 0.0;

        if (maxDeltaSec != null) {
            double md = maxDeltaSec;
            if (Double.isFinite(md) && md > 0.0 && dt > md) dt = md;
        }
        return dt;
    }

    public double computeSimDt(double realDtSec) {
        double dt = sanitizeNonNegFinite(realDtSec);
        if (dt <= 0.0) return 0.0;
        if (paused) return 0.0;
        double s = dt * timeRate;
        return Double.isFinite(s) ? s : 0.0;
    }

    public void setAccumulatorSec(double accumulatorSec) {
        this.accumulatorSec = accumulatorSec;
    }

    public void setFrameIndex(long frameIndex) {
        this.frameIndex = frameIndex;
    }

    public void setTickIndex(long tickIndex) {
        this.tickIndex = tickIndex;
    }

    public void setLastRealDtSec(double lastRealDtSec) {
        this.lastRealDtSec = lastRealDtSec;
    }

    public void setLastSimDtSec(double lastSimDtSec) {
        this.lastSimDtSec = lastSimDtSec;
    }

    public void setLastStepDtSec(double lastStepDtSec) {
        this.lastStepDtSec = lastStepDtSec;
    }
}
