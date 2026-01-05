// FILE: WorkerSystemStats.java
package org.foxesworld.kalitech.engine.world.systems;

public final class WorkerSystemStats {

    public final String systemName;
    public final String profile;
    public final String threadName;

    public final boolean running;
    public final long lastSubmitNanos;
    public final long lastStartNanos;
    public final long lastEndNanos;

    public final long lastTickNanos;
    public final long emaTickNanos;
    public final long maxTickNanos;

    public final long lastQueueLagNanos;
    public final long skippedTicks;

    WorkerSystemStats(
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
            long skippedTicks
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