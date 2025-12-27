// FILE: ScriptJobQueue.java
package org.foxesworld.kalitech.engine.script;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * ScriptJobQueue — официальный мост "любой поток -> owner thread (скриптовый/главный)".
 *
 * <p>Автор: Calista Verner</p>
 *
 * <p>Design:</p>
 * <ul>
 *   <li>{@link #post(Runnable)}: fire-and-forget</li>
 *   <li>{@link #call(Supplier)}: получить результат через {@link CompletableFuture}</li>
 *   <li>{@link #drain(int)} / {@link #drainBudgeted(int, long)}: вызывается строго в owner thread (обычно 1 раз/кадр)</li>
 * </ul>
 */
public final class ScriptJobQueue {

    private static final AtomicLong IDS = new AtomicLong(1);
    private final Queue<Job> q = new ConcurrentLinkedQueue<>();
    private volatile java.util.function.Consumer<Throwable> onError;

    private record Job(long id, Runnable run) {}

    /** Post a fire-and-forget job from any thread. */
    public void post(Runnable run) {
        Objects.requireNonNull(run, "run");
        q.add(new Job(IDS.getAndIncrement(), run));
    }

    /** Call supplier on owner thread; result will complete the returned future. */
    public <T> CompletableFuture<T> call(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> f = new CompletableFuture<>();
        post(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /**
     * Optional error hook for jobs. If set, drainBudgeted will forward thrown exceptions here.
     * Keep it light; it runs on the owner thread.
     */
    public ScriptJobQueue setOnError(java.util.function.Consumer<Throwable> onError) {
        this.onError = onError;
        return this;
    }

    /**
     * Drain queued jobs without time budget. Must be called on owner thread.
     * @return executed jobs count
     */
    public int drain(int maxJobs) {
        return drainBudgeted(maxJobs, 0L);
    }

    /**
     * Drain queued jobs with an optional time budget.
     *
     * @param maxJobs         maximum jobs to execute (safety cap)
     * @param timeBudgetNanos 0 to disable time budget; otherwise stop when budget exceeded
     * @return executed jobs count
     */
    public int drainBudgeted(int maxJobs, long timeBudgetNanos) {
        int limit = Math.max(0, maxJobs);
        long deadline = (timeBudgetNanos > 0L) ? (System.nanoTime() + timeBudgetNanos) : Long.MAX_VALUE;

        int n = 0;
        int checkMask = 0x3F; // check time every 64 jobs (cheap)
        while (n < limit) {
            Job j = q.poll();
            if (j == null) break;

            try {
                j.run.run();
            } catch (Throwable t) {
                // jobs must never crash engine
                java.util.function.Consumer<Throwable> h = this.onError;
                if (h != null) {
                    try { h.accept(t); } catch (Throwable ignored) {}
                }
            }
            n++;

            if ((n & checkMask) == 0 && System.nanoTime() >= deadline) break;
        }
        return n;
    }


    public void clear() {
        q.clear();
    }

    public boolean isEmpty() {
        return q.isEmpty();
    }

    public int size() {
        // Queue#size may be O(n) for CLQ; still useful for debug / telemetry.
        return q.size();
    }
}