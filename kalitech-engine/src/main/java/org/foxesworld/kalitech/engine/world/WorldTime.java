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

    public double interpolationAlpha() {
        if (fixedStepSec == null || fixedStepSec <= 0.0) return 1.0;
        double acc = accumulatorSec;
        if (!Double.isFinite(acc) || acc < 0.0) acc = 0.0;
        double alpha = acc / fixedStepSec;
        if (alpha < 0.0) return 0.0;
        if (alpha > 1.0) return 1.0;
        return alpha;
    }

    public double lastWorldTimeBefore() {
        return lastWorldTimeBefore;
    }

    public double lastWorldTimeAfter() {
        return lastWorldTimeAfter;
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
}
