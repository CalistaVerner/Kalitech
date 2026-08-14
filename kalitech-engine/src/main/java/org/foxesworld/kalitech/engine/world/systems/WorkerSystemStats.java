// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * WorkerSystemStats
 *
 * Immutable snapshot of a worker system slot (thread/lane, runtime profile and timing statistics).
 * Designed for diagnostics and adaptive scheduling.
 */
public final class WorkerSystemStats {

    @LuaExport public final String systemName;
    @LuaExport public final String profile;
    @LuaExport public final String threadName;

    /** True while the system has an in-flight tick. */
    @LuaExport public final boolean running;

    /** -1 for dedicated threads, otherwise lane index for striped scheduling. */
    @LuaExport public final int laneIndex;

    /** Scheduling priority 0..100. */
    @LuaExport public final int priority;

    /**
     * System desired tick rate (Hz).
     */
    @LuaExport
    public final double desiredHz;

    /** Current adaptive tick rate (Hz) used by the scheduler. */
    @LuaExport public final double currentHz;

    // --- timestamps / durations (nanos) ---
    @LuaExport public final long lastSubmitNanos;
    @LuaExport public final long lastStartNanos;
    @LuaExport public final long lastEndNanos;

    /** Last tick duration. */
    @LuaExport public final long lastTickNanos;

    /** Exponential moving average tick duration. */
    @LuaExport public final long emaTickNanos;

    /** Worst observed tick duration. */
    @LuaExport public final long maxTickNanos;

    /** Last queue lag (start - submit). */
    @LuaExport public final long lastQueueLagNanos;

    /** EMA of queue lag (optional). */
    @LuaExport public final long emaQueueLagNanos;

    // --- skip / fault counters ---
    @LuaExport public final int skippedRunning;
    @LuaExport public final int skippedRateLimited;
    @LuaExport public final int skippedBackpressure;

    /** Number of times a tick exceeded hard budget (best-effort, non-interrupting). */
    @LuaExport public final int hardBudgetBreaches;

    /** Number of exceptions thrown by this system tick. */
    @LuaExport public final int exceptions;

    public WorkerSystemStats(
            String systemName,
            String profile,
            String threadName,
            boolean running,
            int laneIndex,
            int priority,
            double desiredHz,
            double currentHz,
            long lastSubmitNanos,
            long lastStartNanos,
            long lastEndNanos,
            long lastTickNanos,
            long emaTickNanos,
            long maxTickNanos,
            long lastQueueLagNanos,
            long emaQueueLagNanos,
            int skippedRunning,
            int skippedRateLimited,
            int skippedBackpressure,
            int hardBudgetBreaches,
            int exceptions
    ) {
        this.systemName = systemName;
        this.profile = profile;
        this.threadName = threadName;
        this.running = running;
        this.laneIndex = laneIndex;
        this.priority = priority;
        this.desiredHz = desiredHz;
        this.currentHz = currentHz;
        this.lastSubmitNanos = lastSubmitNanos;
        this.lastStartNanos = lastStartNanos;
        this.lastEndNanos = lastEndNanos;
        this.lastTickNanos = lastTickNanos;
        this.emaTickNanos = emaTickNanos;
        this.maxTickNanos = maxTickNanos;
        this.lastQueueLagNanos = lastQueueLagNanos;
        this.emaQueueLagNanos = emaQueueLagNanos;
        this.skippedRunning = skippedRunning;
        this.skippedRateLimited = skippedRateLimited;
        this.skippedBackpressure = skippedBackpressure;
        this.hardBudgetBreaches = hardBudgetBreaches;
        this.exceptions = exceptions;
    }
}