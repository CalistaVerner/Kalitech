// FILE: WorkerSystemStats.java
package org.foxesworld.kalitech.engine.world.systems;

import org.graalvm.polyglot.HostAccess;

/**
 * Immutable snapshot of a worker system slot.
 *
 * <p>Designed to be safely exposed to JS via {@link HostAccess.Export}.
 */
public final class WorkerSystemStats {

    @HostAccess.Export public final String systemName;
    @HostAccess.Export public final String profile;
    @HostAccess.Export public final String threadName;
    @HostAccess.Export public final boolean running;

    @HostAccess.Export public final long lastSubmitNanos;
    @HostAccess.Export public final long lastStartNanos;
    @HostAccess.Export public final long lastEndNanos;

    @HostAccess.Export public final long lastTickNanos;
    @HostAccess.Export public final long emaTickNanos;
    @HostAccess.Export public final long maxTickNanos;
    @HostAccess.Export public final long lastQueueLagNanos;
    @HostAccess.Export public final int skippedTicks;

    public WorkerSystemStats(
            String systemName,
            String profile,
            String threadName,
            boolean running,
            long lastSubmitNanos,
            long lastStartNanos,
            long lastEndNanos,
            long lastTickNanos,
            long emaTickNanos,
            long maxTickNanos,
            long lastQueueLagNanos,
            int skippedTicks
    ) {
        this.systemName = systemName;
        this.profile = profile;
        this.threadName = threadName;
        this.running = running;
        this.lastSubmitNanos = lastSubmitNanos;
        this.lastStartNanos = lastStartNanos;
        this.lastEndNanos = lastEndNanos;
        this.lastTickNanos = lastTickNanos;
        this.emaTickNanos = emaTickNanos;
        this.maxTickNanos = maxTickNanos;
        this.lastQueueLagNanos = lastQueueLagNanos;
        this.skippedTicks = skippedTicks;
    }
}