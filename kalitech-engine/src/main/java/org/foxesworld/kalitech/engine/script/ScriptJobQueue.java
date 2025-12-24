// FILE: ScriptJobQueue.java
package org.foxesworld.kalitech.engine.script;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class ScriptJobQueue {

    private final Queue<Runnable> q = new ConcurrentLinkedQueue<>();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();

    /** From any thread. */
    public void post(Runnable job) {
        if (job == null) return;
        q.add(job);
        enqueued.incrementAndGet();
    }

    /** From any thread. */
    public <T> CompletableFuture<T> call(Supplier<T> job) {
        CompletableFuture<T> f = new CompletableFuture<>();
        post(() -> {
            try {
                f.complete(job.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /**
     * Drain jobs on main thread. Budget prevents long stalls.
     * @return number of executed jobs
     */
    public int drain(int maxJobs) {
        int n = 0;
        while (n < maxJobs) {
            Runnable r = q.poll();
            if (r == null) break;
            try {
                r.run();
            } finally {
                executed.incrementAndGet();
            }
            n++;
        }
        return n;
    }

    public long enqueuedCount() { return enqueued.get(); }
    public long executedCount() { return executed.get(); }
    public int pending() { return Math.max(0, (int) (enqueued.get() - executed.get())); }
}