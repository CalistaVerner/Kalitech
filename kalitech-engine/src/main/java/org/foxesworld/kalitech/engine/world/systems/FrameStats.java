// FILE: FrameStats.java
package org.foxesworld.kalitech.engine.world.systems;

import org.graalvm.polyglot.HostAccess;

/**
 * Per-frame performance snapshot (main/world thread).
 *
 * <p>All fields are nanoseconds unless noted.
 * Exposed to JS so you can build in-game profiler/overlay without touching Java.
 */
public final class FrameStats {

    @HostAccess.Export public final long frameIndex;
    @HostAccess.Export public final long budgetNanos;

    @HostAccess.Export public final long frameNanos;
    @HostAccess.Export public final long drainJobsNanos;
    @HostAccess.Export public final long hotReloadNanos;
    @HostAccess.Export public final long eventsNanos;
    @HostAccess.Export public final long worldUpdateNanos;
    @HostAccess.Export public final long awaitWorkersNanos;
    @HostAccess.Export public final long poolMaintenanceNanos;

    @HostAccess.Export public final int jobDrainBudget;
    @HostAccess.Export public final long dispatcherCalls;
    @HostAccess.Export public final long dispatcherTimeouts;

    public FrameStats(
            long frameIndex,
            long budgetNanos,
            long frameNanos,
            long drainJobsNanos,
            long hotReloadNanos,
            long eventsNanos,
            long worldUpdateNanos,
            long awaitWorkersNanos,
            long poolMaintenanceNanos,
            int jobDrainBudget,
            long dispatcherCalls,
            long dispatcherTimeouts
    ) {
        this.frameIndex = frameIndex;
        this.budgetNanos = budgetNanos;
        this.frameNanos = frameNanos;
        this.drainJobsNanos = drainJobsNanos;
        this.hotReloadNanos = hotReloadNanos;
        this.eventsNanos = eventsNanos;
        this.worldUpdateNanos = worldUpdateNanos;
        this.awaitWorkersNanos = awaitWorkersNanos;
        this.poolMaintenanceNanos = poolMaintenanceNanos;
        this.jobDrainBudget = jobDrainBudget;
        this.dispatcherCalls = dispatcherCalls;
        this.dispatcherTimeouts = dispatcherTimeouts;
    }
}