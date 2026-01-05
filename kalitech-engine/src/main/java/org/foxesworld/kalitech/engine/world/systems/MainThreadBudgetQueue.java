// FILE: MainThreadBudgetQueue.java
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AAA main-thread budget queue.
 *
 * Problem:
 *  - Creating models/surfaces/physics bodies every shot causes main-thread spikes (e.g. 205ms).
 *
 * Solution:
 *  - Enqueue heavy tasks.
 *  - Execute at most N tasks per frame, and/or up to time budget from remaining frame time.
 *
 * Works great with:
 *  - spawn batching, physics batching, asset instantiation, scene registry ops, etc.
 */
public final class MainThreadBudgetQueue {

    private static final Logger log = LogManager.getLogger(MainThreadBudgetQueue.class);

    private final ConcurrentLinkedQueue<Runnable> q = new ConcurrentLinkedQueue<>();

    private volatile int maxOpsPerFrame = 8;           // default: 8 heavy ops per frame
    private volatile long maxNanosPerFrame = TimeUnit.MILLISECONDS.toNanos(2); // default: 2ms

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong throttled = new AtomicLong();

    private final AtomicInteger maxDepth = new AtomicInteger();

    // Optional: log throttle events not too often
    private volatile long lastThrottleLogNanos = 0L;
    private volatile long throttleLogEveryNanos = TimeUnit.SECONDS.toNanos(2);

    public void setMaxOpsPerFrame(int n) {
        this.maxOpsPerFrame = Math.max(0, n);
    }

    public int getMaxOpsPerFrame() {
        return maxOpsPerFrame;
    }

    public void setMaxMsPerFrame(int ms) {
        this.maxNanosPerFrame = TimeUnit.MILLISECONDS.toNanos(Math.max(0, ms));
    }

    public int getMaxMsPerFrame() {
        return (int) TimeUnit.NANOSECONDS.toMillis(maxNanosPerFrame);
    }

    public void setThrottleLogEverySeconds(int sec) {
        this.throttleLogEveryNanos = TimeUnit.SECONDS.toNanos(Math.max(0, sec));
    }

    public int size() {
        // O(n) for CLQ, but ok for debug; avoid calling every frame in hot path
        int n = 0;
        for (Runnable ignored : q) n++;
        return n;
    }

    public void enqueue(Runnable r) {
        if (r == null) return;
        q.add(r);
        long e = enqueued.incrementAndGet();

        // track max depth (approx)
        int depth = (int) Math.min(Integer.MAX_VALUE, e - executed.get());
        maxDepth.accumulateAndGet(depth, Math::max);
    }

    /**
     * Convenience: enqueue a JS function (must be executable).
     * WARNING: the Value is executed on the world thread. Use ONLY if this Value
     * belongs to the world runtime/context.
     */
    public void enqueueJs(Value fn) {
        if (fn == null || fn.isNull()) return;
        if (!fn.canExecute()) throw new IllegalArgumentException("mainQueue.enqueue(fn): fn is not executable");
        enqueue(() -> fn.executeVoid());
    }

    /**
     * Drain with budgets. Intended to be called once per frame on the world thread.
     *
     * @param remainingFrameNanos how much time is left in this frame (best-effort).
     * @return drain result snapshot for this frame.
     */
    public DrainResult drain(long remainingFrameNanos) {
        int maxOps = this.maxOpsPerFrame;
        long maxNanos = this.maxNanosPerFrame;

        // If remaining frame time is smaller than our max budget, respect the remaining time.
        if (remainingFrameNanos > 0) maxNanos = Math.min(maxNanos, remainingFrameNanos);
        if (maxOps <= 0 || maxNanos <= 0) {
            int pending = approxPending();
            if (pending > 0) throttled.addAndGet(pending);
            return new DrainResult(0, 0L, pending, true);
        }

        int ops = 0;
        long start = System.nanoTime();
        long deadline = start + maxNanos;

        while (ops < maxOps) {
            Runnable r = q.poll();
            if (r == null) break;

            try {
                r.run();
                executed.incrementAndGet();
            } catch (Throwable t) {
                failed.incrementAndGet();
                log.error("[mainQueue] task failed", t);
            }

            ops++;
            if (System.nanoTime() >= deadline) break;
        }

        long spent = Math.max(0L, System.nanoTime() - start);
        int pending = approxPending();
        boolean throttledThisFrame = pending > 0;

        if (throttledThisFrame) {
            throttled.addAndGet(pending);
            maybeLogThrottle(pending, ops, spent);
        }

        return new DrainResult(ops, spent, pending, throttledThisFrame);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                enqueued.get(),
                executed.get(),
                failed.get(),
                throttled.get(),
                maxDepth.get(),
                approxPending(),
                maxOpsPerFrame,
                TimeUnit.NANOSECONDS.toMillis(maxNanosPerFrame)
        );
    }

    // ---- DTOs (JS-friendly if you export them later) ----

    public static final class DrainResult {
        public final int executedOps;
        public final long spentNanos;
        public final int pendingAfter;
        public final boolean throttled;

        public DrainResult(int executedOps, long spentNanos, int pendingAfter, boolean throttled) {
            this.executedOps = executedOps;
            this.spentNanos = spentNanos;
            this.pendingAfter = pendingAfter;
            this.throttled = throttled;
        }
    }

    public static final class Snapshot {
        public final long enqueued;
        public final long executed;
        public final long failed;
        public final long throttled;
        public final int maxDepth;
        public final int pending;
        public final int maxOpsPerFrame;
        public final long maxMsPerFrame;

        public Snapshot(long enqueued, long executed, long failed, long throttled, int maxDepth, int pending, int maxOpsPerFrame, long maxMsPerFrame) {
            this.enqueued = enqueued;
            this.executed = executed;
            this.failed = failed;
            this.throttled = throttled;
            this.maxDepth = maxDepth;
            this.pending = pending;
            this.maxOpsPerFrame = maxOpsPerFrame;
            this.maxMsPerFrame = maxMsPerFrame;
        }
    }

    // ---- internals ----

    private int approxPending() {
        long p = enqueued.get() - executed.get();
        if (p < 0) p = 0;
        if (p > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) p;
    }

    private void maybeLogThrottle(int pending, int ranOps, long spentNanos) {
        long every = throttleLogEveryNanos;
        if (every <= 0) return;

        long now = System.nanoTime();
        long last = lastThrottleLogNanos;
        if (now - last < every) return;

        lastThrottleLogNanos = now;
        log.warn("[mainQueue] throttled pending={} ranOps={} spentMs={} (maxOps={} maxMs={})",
                pending,
                ranOps,
                TimeUnit.NANOSECONDS.toMillis(spentNanos),
                maxOpsPerFrame,
                TimeUnit.NANOSECONDS.toMillis(maxNanosPerFrame));
    }
}