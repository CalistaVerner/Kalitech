// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.graalvm.polyglot.HostAccess;

/**
 * WorkerSystemStats
 *
 * Immutable snapshot of a worker system slot (thread/lane, runtime profile and timing statistics).
 * Designed for diagnostics and adaptive scheduling.
 */
public final class WorkerSystemStats {

    @HostAccess.Export public final String systemName;
    @HostAccess.Export public final String profile;
    @HostAccess.Export public final String threadName;

    /** True while the system has an in-flight tick. */
    @HostAccess.Export public final boolean running;

    /** -1 for dedicated threads, otherwise lane index for striped scheduling. */
    @HostAccess.Export public final int laneIndex;

    /** Scheduling priority 0..100. */
    @HostAccess.Export public final int priority;

    /**
     * System desired tick rate (Hz).
     */
    @HostAccess.Export
    public final double desiredHz;

    /** Current adaptive tick rate (Hz) used by the scheduler. */
    @HostAccess.Export public final double currentHz;

    // --- timestamps / durations (nanos) ---
    @HostAccess.Export public final long lastSubmitNanos;
    @HostAccess.Export public final long lastStartNanos;
    @HostAccess.Export public final long lastEndNanos;

    /** Last tick duration. */
    @HostAccess.Export public final long lastTickNanos;

    /** Exponential moving average tick duration. */
    @HostAccess.Export public final long emaTickNanos;

    /** Worst observed tick duration. */
    @HostAccess.Export public final long maxTickNanos;

    /** Last queue lag (start - submit). */
    @HostAccess.Export public final long lastQueueLagNanos;

    /** EMA of queue lag (optional). */
    @HostAccess.Export public final long emaQueueLagNanos;

    // --- skip / fault counters ---
    @HostAccess.Export public final int skippedRunning;
    @HostAccess.Export public final int skippedRateLimited;
    @HostAccess.Export public final int skippedBackpressure;

    /** Number of times a tick exceeded hard budget (best-effort, non-interrupting). */
    @HostAccess.Export public final int hardBudgetBreaches;

    /** Number of exceptions thrown by this system tick. */
    @HostAccess.Export public final int exceptions;

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