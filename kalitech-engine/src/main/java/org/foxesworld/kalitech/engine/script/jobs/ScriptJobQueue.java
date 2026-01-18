// FILE: ScriptJobQueue.java
package org.foxesworld.kalitech.engine.script.jobs;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ScriptJobQueue is a thread-safe bridge from "any thread" to an "owner thread"
 * (typically script/main thread).
 *
 * <p>Design:</p>
 * <ul>
 *   <li>{@link #post(Runnable)}: fire-and-forget</li>
 *   <li>{@link #call(Supplier)}: execute on owner thread and complete a {@link CompletableFuture}</li>
 *   <li>{@link #drain(int)} / {@link #drainBudgeted(int, long)}: must be called on owner thread (usually once per frame)</li>
 * </ul>
 */
public final class ScriptJobQueue {

    private static final AtomicLong IDS = new AtomicLong(1);

    private final Queue<Job> q = new ConcurrentLinkedQueue<>();
    private volatile Consumer<Throwable> onError;

    private static void safeStderr(String msg, Throwable t) {
        try {
            System.err.println(msg + ": " + t);
            t.printStackTrace(System.err);
        } catch (Throwable ignored) {
            // Intentionally left empty: last-resort logging must never crash the engine.
        }
    }

    /**
     * Post a fire-and-forget job from any thread.
     *
     * @param run runnable to execute on owner thread
     */
    public void post(Runnable run) {
        Objects.requireNonNull(run, "run");
        q.add(new Job(IDS.getAndIncrement(), run));
    }

    /**
     * Call supplier on owner thread; result will complete the returned future.
     *
     * @param supplier supplier to execute
     * @param <T> result type
     * @return future completed on owner thread
     */
    public <T> CompletableFuture<T> call(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> f = new CompletableFuture<>();
        post(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
                reportError(t);
            }
        });
        return f;
    }

    /**
     * Set error hook for job failures. This hook is executed on the owner thread.
     * If the hook itself throws, the failure is reported to {@code System.err}.
     *
     * <p>If no hook is set, job failures are reported to {@code System.err} by default
     * to prevent silent breakage.</p>
     *
     * @param onError error consumer
     * @return this queue
     */
    public ScriptJobQueue setOnError(Consumer<Throwable> onError) {
        this.onError = onError;
        return this;
    }

    /**
     * Drain queued jobs without time budget. Must be called on owner thread.
     *
     * @param maxJobs maximum number of jobs to execute
     * @return executed jobs count
     */
    public int drain(int maxJobs) {
        return drainBudgeted(maxJobs, 0L);
    }

    /**
     * Drain queued jobs with an optional time budget. Must be called on owner thread.
     *
     * @param maxJobs maximum number of jobs to execute (safety cap)
     * @param timeBudgetNanos 0 to disable time budget; otherwise stop when budget exceeded
     * @return executed jobs count
     */
    public int drainBudgeted(int maxJobs, long timeBudgetNanos) {
        final int limit = Math.max(0, maxJobs);
        final boolean budgetEnabled = timeBudgetNanos > 0L;
        final long deadline = budgetEnabled ? (System.nanoTime() + timeBudgetNanos) : Long.MAX_VALUE;

        int n = 0;
        final int checkMask = 0x3F;

        while (n < limit) {
            final Job j = q.poll();
            if (j == null) break;

            try {
                j.run.run();
            } catch (Throwable t) {
                reportError(new JobFailedException(j.id, t));
            }

            n++;

            if (budgetEnabled) {
                if ((n & checkMask) == 0) {
                    if (System.nanoTime() >= deadline) break;
                }
            }
        }
        return n;
    }

    /**
     * Clear all queued jobs.
     */
    public void clear() {
        q.clear();
    }

    /**
     * @return true if queue is empty
     */
    public boolean isEmpty() {
        return q.isEmpty();
    }

    /**
     * Note: {@link Queue#size()} may be O(n) for {@link ConcurrentLinkedQueue}.
     *
     * @return approximate queue size
     */
    public int size() {
        return q.size();
    }

    private void reportError(Throwable t) {
        final Consumer<Throwable> h = this.onError;
        if (h != null) {
            try {
                h.accept(t);
                return;
            } catch (Throwable hookFailure) {
                safeStderr("[ScriptJobQueue] onError hook failed", hookFailure);
            }
        }
        safeStderr("[ScriptJobQueue] job failed", t);
    }

    private record Job(long id, Runnable run) {
    }

    /**
     * Wraps a job failure with its job id for diagnostics.
     */
    private static final class JobFailedException extends RuntimeException {
        private final long jobId;

        /**
         * @param jobId job id
         * @param cause original failure
         */
        JobFailedException(long jobId, Throwable cause) {
            super("Job failed: id=" + jobId, cause);
            this.jobId = jobId;
        }

        /**
         * @return failed job id
         */
        public long getJobId() {
            return jobId;
        }
    }
}