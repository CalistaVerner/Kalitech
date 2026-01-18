// FILE: org/foxesworld/kalitech/engine/world/systems/proxy/MainThreadDispatcher.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems.proxy;

import org.foxesworld.kalitech.engine.script.jobs.ScriptJobQueue;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MainThreadDispatcher
 *
 * English:
 * Marshals synchronous calls from worker threads to the world/main thread using {@link ScriptJobQueue}.
 *
 * Contract (do not break):
 * - JS still calls engine/api as synchronous methods.
 * - If the call originates from a worker thread, we enqueue a job to the world queue and wait for the result.
 *
 * Stability upgrades:
 * - Fast-fail if the world queue is not draining (timeout with clear diagnostics).
 * - Counters for calls/timeouts + last-latency samples (useful for perf overlays / logs).
 * - Safe timeout normalization and better error propagation.
 */
public final class MainThreadDispatcher {

    private final Thread worldThread;
    private final ScriptJobQueue worldJobs;

    // counters
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();

    // latency stats (best-effort, lock-free)
    private final AtomicLong lastWaitNanos = new AtomicLong();
    private final AtomicLong lastExecNanos = new AtomicLong();
    private final AtomicLong lastTotalNanos = new AtomicLong();
    private final AtomicLong maxTotalNanos = new AtomicLong();

    private volatile long defaultTimeoutMs = 2000;

    public MainThreadDispatcher(Thread worldThread, ScriptJobQueue worldJobs) {
        this.worldThread = Objects.requireNonNull(worldThread, "worldThread");
        this.worldJobs = Objects.requireNonNull(worldJobs, "worldJobs");
    }

    public long getDefaultTimeoutMs() { return defaultTimeoutMs; }

    public void setDefaultTimeoutMs(long ms) {
        this.defaultTimeoutMs = Math.max(1, ms);
    }

    public boolean isWorldThread() {
        return Thread.currentThread() == worldThread;
    }

    public Thread getWorldThread() { return worldThread; }

    public ScriptJobQueue getWorldJobs() { return worldJobs; }

    public long getCalls() { return calls.get(); }

    public long getTimeouts() { return timeouts.get(); }

    /** Last worker wait time (enqueue -> start executing on world thread), in nanos. */
    public long getLastWaitNanos() { return lastWaitNanos.get(); }

    /** Last execution time (time spent inside action on world thread), in nanos. */
    public long getLastExecNanos() { return lastExecNanos.get(); }

    /** Last total time (enqueue -> completion), in nanos. */
    public long getLastTotalNanos() { return lastTotalNanos.get(); }

    /** Max observed total time (enqueue -> completion), in nanos. */
    public long getMaxTotalNanos() { return maxTotalNanos.get(); }

    public <T> T call(Callable<T> action) {
        return call(action, defaultTimeoutMs);
    }

    public void run(Runnable action) {
        call(Executors.callable(action, null), defaultTimeoutMs);
    }

    public <T> T call(Callable<T> action, long timeoutMs) {
        Objects.requireNonNull(action, "action");

        final long timeout = Math.max(1, timeoutMs);

        // If already on world thread -> execute directly.
        if (isWorldThread()) {
            try {
                return action.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        calls.incrementAndGet();

        final long tEnqueue = System.nanoTime();
        final CompletableFuture<T> fut = new CompletableFuture<>();

        // Post to world job queue. We keep this tiny: measure wait/exec/total for diagnostics.
        worldJobs.post(() -> {
            final long tStart = System.nanoTime();
            lastWaitNanos.set(Math.max(0L, tStart - tEnqueue));

            try {
                T r = action.call();
                fut.complete(r);
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            } finally {
                final long tEnd = System.nanoTime();
                final long exec = Math.max(0L, tEnd - tStart);
                final long total = Math.max(0L, tEnd - tEnqueue);

                lastExecNanos.set(exec);
                lastTotalNanos.set(total);
                updateMax(maxTotalNanos, total);
            }
        });

        try {
            return fut.get(timeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException te) {
            timeouts.incrementAndGet();

            long waitNs = lastWaitNanos.get();
            long execNs = lastExecNanos.get();
            long totalNs = lastTotalNanos.get();

            // Keep message short but actionable: "world queue not draining" is the usual cause.
            String msg = "Main-thread call timed out after " + timeout + "ms"
                    + " (worldThread=" + safeThreadName(worldThread)
                    + ", lastWaitMs=" + toMs(waitNs)
                    + ", lastExecMs=" + toMs(execNs)
                    + ", lastTotalMs=" + toMs(totalNs) + ").";

            throw new RuntimeException(msg, te);

        } catch (ExecutionException ee) {
            Throwable c = ee.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error err) throw err;
            throw new RuntimeException(c);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for main-thread call", ie);
        }
    }

    private static void updateMax(AtomicLong max, long v) {
        for (;;) {
            long cur = max.get();
            if (v <= cur) return;
            if (max.compareAndSet(cur, v)) return;
        }
    }

    private static String safeThreadName(Thread t) {
        if (t == null) return "null";
        String n = t.getName();
        return (n != null && !n.isBlank()) ? n : ("tid=" + t.getId());
    }

    private static long toMs(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }
}