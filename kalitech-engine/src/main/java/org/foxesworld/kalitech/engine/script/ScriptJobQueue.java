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
 * Design:
 * - post(Runnable): fire-and-forget
 * - call(Supplier<T>): получить результат через Future
 * - drain(max): вызывается строго в owner thread (обычно 1 раз/кадр)
 */
public final class ScriptJobQueue {

    private static final class Job {
        final long id;
        final Runnable run;
        Job(long id, Runnable run) { this.id = id; this.run = run; }
    }

    private final Queue<Job> q = new ConcurrentLinkedQueue<>();
    private final AtomicLong ids = new AtomicLong(1);

    public long post(Runnable r) {
        Objects.requireNonNull(r, "r");
        long id = ids.getAndIncrement();
        q.add(new Job(id, r));
        return id;
    }

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
     * Drain up to max jobs (defensive against infinite producer).
     * Returns executed job count.
     */
    public int drain(int max) {
        if (max <= 0) return 0;
        int n = 0;
        while (n < max) {
            Job j = q.poll();
            if (j == null) break;
            j.run.run();
            n++;
        }
        return n;
    }

    public void clear() {
        q.clear();
    }

    public boolean isEmpty() {
        return q.isEmpty();
    }
}