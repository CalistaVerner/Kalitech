// FILE: org/foxesworld/kalitech/engine/world/WorldTime.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

/**
 * Mutable world time state.
 * Owned by the world and exposed to scripts through {@code SystemContext.time}.
 */
public final class WorldTime {

    private final Double fixedStepSec;
    private final Double maxDeltaSec;
    private double worldTimeSec;
    private double timeRate;
    private boolean paused;
    private double accumulatorSec;

    private long frameIndex;
    private long tickIndex;

    private double lastRealDtSec;
    private double lastSimDtSec;
    private double lastStepDtSec;

    private double lastWorldTimeBefore;
    private double lastWorldTimeAfter;

    private double dayLengthSec;
    private double dayOffsetSec;

    public WorldTime(WorldTimeParams params) {
        WorldTimeParams p = (params != null) ? params : WorldTimeParams.defaults();
        this.worldTimeSec = p.worldTime;
        this.timeRate = p.timeRate;
        this.paused = p.paused;
        this.fixedStepSec = p.fixedStep;
        this.maxDeltaSec = p.maxDelta;
        this.accumulatorSec = 0.0;

        this.frameIndex = 0L;
        this.tickIndex = 0L;

        this.lastRealDtSec = 0.0;
        this.lastSimDtSec = 0.0;
        this.lastStepDtSec = 0.0;

        this.lastWorldTimeBefore = this.worldTimeSec;
        this.lastWorldTimeAfter = this.worldTimeSec;

        this.dayLengthSec = p.dayLengthSec;
        this.dayOffsetSec = p.dayOffsetSec;
    }

    private static double sanitizeNonNegativeFinite(double v) {
        if (!Double.isFinite(v) || v < 0.0) return 0.0;
        return v;
    }

    public double worldTimeSec() {
        return worldTimeSec;
    }

    public double timeRate() {
        return timeRate;
    }

    public boolean paused() {
        return paused;
    }

    public Double fixedStepSec() {
        return fixedStepSec;
    }

    public Double maxDeltaSec() {
        return maxDeltaSec;
    }

    public double accumulatorSec() {
        return accumulatorSec;
    }

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

    public double lastWorldTimeBefore() {
        return lastWorldTimeBefore;
    }

    public double lastWorldTimeAfter() {
        return lastWorldTimeAfter;
    }

    public double dayLengthSec() {
        return dayLengthSec;
    }

    public double dayOffsetSec() {
        return dayOffsetSec;
    }

    public double dayTimeSec() {
        return wrap(worldTimeSec + dayOffsetSec, dayLengthSec);
    }

    public double dayFraction() {
        if (dayLengthSec <= 0.0) return 0.0;
        return dayTimeSec() / dayLengthSec;
    }

    public double timeOfDayHours() {
        return dayFraction() * 24.0;
    }

    public void seek(double newWorldTimeSec) {
        if (!Double.isFinite(newWorldTimeSec)) return;
        this.worldTimeSec = newWorldTimeSec;
        this.accumulatorSec = 0.0;
        this.lastWorldTimeBefore = newWorldTimeSec;
        this.lastWorldTimeAfter = newWorldTimeSec;
    }

    public void advanceWorldTime(double dtSec) {
        if (dtSec <= 0.0 || !Double.isFinite(dtSec)) return;
        this.worldTimeSec += dtSec;
    }

    public void addAccumulator(double dtSec) {
        if (dtSec <= 0.0 || !Double.isFinite(dtSec)) return;
        this.accumulatorSec += dtSec;
        if (this.accumulatorSec < 0.0) this.accumulatorSec = 0.0;
    }

    public void consumeAccumulator(double dtSec) {
        if (dtSec <= 0.0 || !Double.isFinite(dtSec)) return;
        this.accumulatorSec -= dtSec;
        if (this.accumulatorSec < 0.0) this.accumulatorSec = 0.0;
    }

    /**
     * Called by the world exactly once per rendered frame.
     */
    void beginFrame(double realDtSec, double simDtSec) {
        frameIndex++;

        this.lastRealDtSec = sanitizeNonNegativeFinite(realDtSec);
        this.lastSimDtSec = sanitizeNonNegativeFinite(simDtSec);
        this.lastStepDtSec = 0.0;

        this.lastWorldTimeBefore = this.worldTimeSec;
        this.lastWorldTimeAfter = this.worldTimeSec;
    }

    /**
     * Called by the world for each simulation step (variable or fixed).
     */
    void beginStep(double stepDtSec) {
        tickIndex++;
        this.lastStepDtSec = sanitizeNonNegativeFinite(stepDtSec);
        this.lastWorldTimeBefore = this.worldTimeSec;
        this.lastWorldTimeAfter = this.worldTimeSec;
    }

    /**
     * Called by the world after step completion.
     */
    void endStep() {
        this.lastWorldTimeAfter = this.worldTimeSec;
    }

    public double getWorldTimeSec() {
        return worldTimeSec;
    }

    public double getTimeRate() {
        return timeRate;
    }

    public void setTimeRate(double rate) {
        if (!Double.isFinite(rate)) return;
        if (rate < 0.0) rate = 0.0;
        if (rate > 1_000.0) rate = 1_000.0;
        this.timeRate = rate;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void setDayLengthSec(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) return;
        double fraction = dayFraction();
        this.dayLengthSec = seconds;
        setTimeOfDayHours(fraction * 24.0);
    }

    public void setTimeOfDayHours(double hours) {
        if (!Double.isFinite(hours)) return;
        double h = hours % 24.0;
        if (h < 0.0) h += 24.0;
        double length = this.dayLengthSec;
        if (!Double.isFinite(length) || length <= 0.0) return;
        double targetSec = length * (h / 24.0);
        double currentSec = wrap(this.worldTimeSec, length);
        this.dayOffsetSec = wrap(targetSec - currentSec, length);
    }

    public void setDayOffsetSec(double offsetSec) {
        if (!Double.isFinite(offsetSec)) return;
        this.dayOffsetSec = wrap(offsetSec, this.dayLengthSec);
    }

    public Double getFixedStepSec() {
        return fixedStepSec;
    }

    public Double getMaxDeltaSec() {
        return maxDeltaSec;
    }

    public double getAccumulatorSec() {
        return accumulatorSec;
    }

    public long getFrameIndex() {
        return frameIndex;
    }

    public long getTickIndex() {
        return tickIndex;
    }

    public double getLastRealDtSec() {
        return lastRealDtSec;
    }

    public double getLastSimDtSec() {
        return lastSimDtSec;
    }

    public double getLastStepDtSec() {
        return lastStepDtSec;
    }

    public double getLastWorldTimeBefore() {
        return lastWorldTimeBefore;
    }

    public double getLastWorldTimeAfter() {
        return lastWorldTimeAfter;
    }

    public double getDayLengthSec() {
        return dayLengthSec;
    }

    public double getDayOffsetSec() {
        return dayOffsetSec;
    }

    public double getDayTimeSec() {
        return dayTimeSec();
    }

    public double getDayFraction() {
        return dayFraction();
    }

    public double getTimeOfDayHours() {
        return timeOfDayHours();
    }

    private static double wrap(double value, double length) {
        if (!Double.isFinite(value) || !Double.isFinite(length) || length <= 0.0) return 0.0;
        double m = value % length;
        if (m < 0.0) m += length;
        return m;
    }
}
