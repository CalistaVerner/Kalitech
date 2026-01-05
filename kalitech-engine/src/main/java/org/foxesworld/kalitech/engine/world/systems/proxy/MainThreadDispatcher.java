// FILE: MainThreadDispatcher.java
package org.foxesworld.kalitech.engine.world.systems.proxy;

import org.foxesworld.kalitech.engine.script.ScriptJobQueue;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Marshals calls from worker threads to the world/main thread via ScriptJobQueue.
 *
 * принцип не меняем:
 *  - JS по-прежнему вызывает engine/api как синхронные методы
 *  - если вызов пришёл из worker thread -> ставим job в world queue и ждём результат
 *
 * WARNING:
 *  - If the world thread doesn't drain jobs frequently enough, worker will block.
 */
public final class MainThreadDispatcher {

    private final Thread worldThread;
    private final ScriptJobQueue worldJobs;

    private volatile long defaultTimeoutMs = 2000;

    // simple counters
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();

    public MainThreadDispatcher(Thread worldThread, ScriptJobQueue worldJobs) {
        this.worldThread = Objects.requireNonNull(worldThread, "worldThread");
        this.worldJobs = Objects.requireNonNull(worldJobs, "worldJobs");
    }

    public void setDefaultTimeoutMs(long ms) {
        this.defaultTimeoutMs = Math.max(1, ms);
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public boolean isWorldThread() {
        return Thread.currentThread() == worldThread;
    }

    public long getCalls() { return calls.get(); }
    public long getTimeouts() { return timeouts.get(); }

    public <T> T call(Callable<T> action) {
        return call(action, defaultTimeoutMs);
    }

    public void run(Runnable action) {
        call(Executors.callable(action, null), defaultTimeoutMs);
    }

    public <T> T call(Callable<T> action, long timeoutMs) {
        Objects.requireNonNull(action, "action");

        // If already on world thread -> direct.
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

        final CompletableFuture<T> fut = new CompletableFuture<>();
        worldJobs.post(() -> {
            try {
                T r = action.call();
                fut.complete(r);
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });

        try {
            return fut.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            timeouts.incrementAndGet();
            throw new RuntimeException("Main-thread call timed out after " + timeoutMs + "ms", te);
        } catch (ExecutionException ee) {
            Throwable c = ee.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(c);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for main-thread call", ie);
        }
    }
}