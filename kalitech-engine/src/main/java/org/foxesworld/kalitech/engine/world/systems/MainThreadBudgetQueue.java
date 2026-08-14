// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MainThreadBudgetQueue
 *
 * A bounded, prioritized command queue executed on the world (main) thread.
 * It protects frame time by draining commands within a per-frame budget and provides backpressure signals
 * so worker systems can throttle themselves when the apply pipeline is overloaded.
 */
public final class MainThreadBudgetQueue {

    private static final Logger log = LogManager.getLogger(MainThreadBudgetQueue.class);

    // Prioritized queues (HIGH first)
    private final ConcurrentLinkedQueue<Runnable> high = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> normal = new ConcurrentLinkedQueue<>();

    // Budget per frame (defaults are intentionally conservative)
    private volatile int maxOpsPerFrame = 8;
    private volatile long maxNanosPerFrame = TimeUnit.MILLISECONDS.toNanos(2);

    // Hard bounds (memory safety)
    private volatile int maxPending = 50_000;
    private volatile int overloadPendingThreshold = 10_000;

    // Counters (monotonic)
    private final AtomicLong enqueuedHigh = new AtomicLong();
    private final AtomicLong enqueuedNormal = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    // Pending counts (O(1) pressure signal)
    private final AtomicInteger pendingHigh = new AtomicInteger();
    private final AtomicInteger pendingNormal = new AtomicInteger();

    private final AtomicInteger maxDepth = new AtomicInteger();

    // Optional: log throttle events not too often
    private volatile long lastThrottleLogNanos = 0L;
    private volatile long throttleLogEveryNanos = TimeUnit.SECONDS.toNanos(2);

    // ---------------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------------

    public void setMaxOpsPerFrame(int n) { this.maxOpsPerFrame = Math.max(0, n); }
    public int getMaxOpsPerFrame() { return maxOpsPerFrame; }

    public void setMaxMsPerFrame(int ms) { this.maxNanosPerFrame = TimeUnit.MILLISECONDS.toNanos(Math.max(0, ms)); }
    public int getMaxMsPerFrame() { return (int) TimeUnit.NANOSECONDS.toMillis(maxNanosPerFrame); }

    public void setThrottleLogEverySeconds(int sec) { this.throttleLogEveryNanos = TimeUnit.SECONDS.toNanos(Math.max(0, sec)); }

    /** Absolute bound for pending commands (across all priorities). */
    public void setMaxPending(int n) { this.maxPending = Math.max(0, n); }
    public int getMaxPending() { return maxPending; }

    /** Pending threshold to consider the queue overloaded (backpressure signal). */
    public void setOverloadPendingThreshold(int n) { this.overloadPendingThreshold = Math.max(0, n); }
    public int getOverloadPendingThreshold() { return overloadPendingThreshold; }

    // ---------------------------------------------------------------------
    // Pressure / diagnostics
    // ---------------------------------------------------------------------

    public int pending() {
        return Math.max(0, pendingHigh.get()) + Math.max(0, pendingNormal.get());
    }

    public int pendingHigh() { return Math.max(0, pendingHigh.get()); }
    public int pendingNormal() { return Math.max(0, pendingNormal.get()); }

    public boolean isOverloaded() {
        int thr = overloadPendingThreshold;
        return thr > 0 && pending() >= thr;
    }

    /**
     * O(n) for CLQ; for debug only. Prefer {@link #pending()} in hot paths.
     */
    public int sizeSlow() {
        int n = 0;
        for (Runnable ignored : high) n++;
        for (Runnable ignored : normal) n++;
        return n;
    }

    // ---------------------------------------------------------------------
    // Enqueue
    // ---------------------------------------------------------------------

    public void enqueue(Runnable r) { tryEnqueueNormal(r); }

    public void enqueueHigh(Runnable r) { tryEnqueueHigh(r); }

    public void enqueue(LuaValueRef fn) {
        if (fn == null || fn.isNull()) return;
        if (!fn.canExecute()) throw new IllegalArgumentException("mainQueue.enqueue(fn): fn is not executable");
        enqueue(() -> fn.executeVoid());
    }

    public void enqueueHigh(LuaValueRef fn) {
        if (fn == null || fn.isNull()) return;
        if (!fn.canExecute()) throw new IllegalArgumentException("mainQueue.enqueueHigh(fn): fn is not executable");
        enqueueHigh(() -> fn.executeVoid());
    }

    public boolean tryEnqueueNormal(Runnable r) {
        return tryEnqueue(normal, pendingNormal, enqueuedNormal, r);
    }

    public boolean tryEnqueueHigh(Runnable r) {
        return tryEnqueue(high, pendingHigh, enqueuedHigh, r);
    }

    private boolean tryEnqueue(ConcurrentLinkedQueue<Runnable> q, AtomicInteger pendingCtr, AtomicLong enqCtr, Runnable r) {
        if (r == null) return false;

        // Hard bound: refuse when full.
        int p = pending();
        int max = maxPending;
        if (max > 0 && p >= max) {
            rejected.incrementAndGet();
            return false;
        }

        q.add(r);
        enqCtr.incrementAndGet();

        int depth = pendingCtr.incrementAndGet() + pending(); // approx across both
        maxDepth.accumulateAndGet(depth, Math::max);
        return true;
    }

    // ---------------------------------------------------------------------
    // Drain
    // ---------------------------------------------------------------------

    /**
     * Drain with budgets. Intended to be called once per frame on the world thread.
     *
     * @param remainingFrameNanos how much time is left in this frame (best-effort).
     * @return drain result snapshot for this frame.
     */
    public DrainResult drain(long remainingFrameNanos) {
        int maxOps = this.maxOpsPerFrame;
        long maxNanos = this.maxNanosPerFrame;

        // If remaining frame time is smaller than our max budget, respect remaining time.
        if (remainingFrameNanos > 0) maxNanos = Math.min(maxNanos, remainingFrameNanos);

        int pBefore = pending();
        if (maxOps <= 0 || maxNanos <= 0) {
            boolean throttledThisFrame = pBefore > 0;
            if (throttledThisFrame) maybeLogThrottle(pBefore, 0, 0L);
            return new DrainResult(0, 0, 0, 0L, pBefore, pendingHigh(), pendingNormal(), throttledThisFrame);
        }

        int ops = 0;
        int opsHigh = 0;
        int opsNormal = 0;

        long start = System.nanoTime();
        long deadline = start + maxNanos;

        while (ops < maxOps) {
            Runnable r = pollPrioritized();
            if (r == null) break;

            try {
                r.run();
            } catch (Throwable t) {
                failed.incrementAndGet();
                log.error("[mainQueue] command failed", t);
            } finally {
                executed.incrementAndGet();
            }

            ops++;
            if (r instanceof TaggedHigh) opsHigh++;
            else opsNormal++;

            if (System.nanoTime() >= deadline) break;
        }

        long spent = Math.max(0L, System.nanoTime() - start);

        int pAfter = pending();
        boolean throttledThisFrame = pAfter > 0;

        if (throttledThisFrame) {
            maybeLogThrottle(pAfter, ops, spent);
        }

        return new DrainResult(ops, opsHigh, opsNormal, spent, pAfter, pendingHigh(), pendingNormal(), throttledThisFrame);
    }

    private Runnable pollPrioritized() {
        Runnable r = high.poll();
        if (r != null) {
            pendingHigh.decrementAndGet();
            return new TaggedHigh(r);
        }
        r = normal.poll();
        if (r != null) {
            pendingNormal.decrementAndGet();
            return r;
        }
        return null;
    }

    /**
     * Tiny wrapper to attribute executed ops to HIGH without changing external Runnable type.
     */
    private static final class TaggedHigh implements Runnable {
        private final Runnable inner;
        TaggedHigh(Runnable inner) { this.inner = inner; }
        @Override public void run() { inner.run(); }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                enqueuedHigh.get(),
                enqueuedNormal.get(),
                executed.get(),
                failed.get(),
                rejected.get(),
                maxDepth.get(),
                pending(),
                pendingHigh(),
                pendingNormal(),
                maxOpsPerFrame,
                TimeUnit.NANOSECONDS.toMillis(maxNanosPerFrame),
                maxPending,
                overloadPendingThreshold
        );
    }

    // ---------------------------------------------------------------------
    // DTOs
    // ---------------------------------------------------------------------

    public static final class DrainResult {
        public final int executedOps;
        public final int executedHighOps;
        public final int executedNormalOps;
        public final long spentNanos;
        public final int pending;
        public final int pendingHigh;
        public final int pendingNormal;
        public final boolean throttled;

        public DrainResult(
                int executedOps,
                int executedHighOps,
                int executedNormalOps,
                long spentNanos,
                int pending,
                int pendingHigh,
                int pendingNormal,
                boolean throttled
        ) {
            this.executedOps = executedOps;
            this.executedHighOps = executedHighOps;
            this.executedNormalOps = executedNormalOps;
            this.spentNanos = spentNanos;
            this.pending = pending;
            this.pendingHigh = pendingHigh;
            this.pendingNormal = pendingNormal;
            this.throttled = throttled;
        }
    }

    public static final class Snapshot {
        public final long enqueuedHigh;
        public final long enqueuedNormal;
        public final long executed;
        public final long failed;
        public final long rejected;
        public final int maxDepth;
        public final int pending;
        public final int pendingHigh;
        public final int pendingNormal;
        public final int maxOpsPerFrame;
        public final long maxMsPerFrame;
        public final int maxPending;
        public final int overloadPendingThreshold;

        public Snapshot(
                long enqueuedHigh,
                long enqueuedNormal,
                long executed,
                long failed,
                long rejected,
                int maxDepth,
                int pending,
                int pendingHigh,
                int pendingNormal,
                int maxOpsPerFrame,
                long maxMsPerFrame,
                int maxPending,
                int overloadPendingThreshold
        ) {
            this.enqueuedHigh = enqueuedHigh;
            this.enqueuedNormal = enqueuedNormal;
            this.executed = executed;
            this.failed = failed;
            this.rejected = rejected;
            this.maxDepth = maxDepth;
            this.pending = pending;
            this.pendingHigh = pendingHigh;
            this.pendingNormal = pendingNormal;
            this.maxOpsPerFrame = maxOpsPerFrame;
            this.maxMsPerFrame = maxMsPerFrame;
            this.maxPending = maxPending;
            this.overloadPendingThreshold = overloadPendingThreshold;
        }
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

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