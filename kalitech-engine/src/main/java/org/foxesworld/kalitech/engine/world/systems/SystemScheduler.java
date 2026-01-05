// FILE: SystemScheduler.java
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.world.WorldAppState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CDPR-style system scheduler:
 *
 * <ul>
 *   <li>{@link ThreadMode#MAIN}: runs inline on world thread (as before).</li>
 *   <li>{@link ThreadMode#WORKER_DEDICATED}: each system gets a dedicated single thread + isolated runtime profile.</li>
 * </ul>
 *
 * <p>Key property: worker system never runs concurrently with itself (no re-entrancy);
 * if a tick is still running, next tick is skipped.
 */
public final class SystemScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SystemScheduler.class);

    private final WorldAppState world;
    private final Map<KSystem, Slot> slots = new IdentityHashMap<>();

    /**
     * Default max time to wait for worker ticks per frame. 0 = don't wait.
     */
    private volatile long defaultAwaitBudgetNanos = TimeUnit.MILLISECONDS.toNanos(2);

    public SystemScheduler(WorldAppState world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public void setDefaultAwaitBudgetMs(int ms) {
        this.defaultAwaitBudgetNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, ms));
    }

    public int getDefaultAwaitBudgetMs() {
        return (int) TimeUnit.NANOSECONDS.toMillis(defaultAwaitBudgetNanos);
    }

    /**
     * Ensure worker slot is created and started (on-demand).
     */
    public void ensureStarted(KSystem system, SystemContext ctx) {
        if (system == null) return;
        if (system.threadMode() != ThreadMode.WORKER_DEDICATED) return;

        Slot slot;
        synchronized (slots) {
            slot = slots.get(system);
            if (slot == null) {
                slot = new Slot(system, ctx);
                slots.put(system, slot);
            }
        }

        slot.startIfNeeded();
    }

    /**
     * Submit worker update tick (non-blocking).
     */
    public void submitUpdate(KSystem system, SystemContext ctx, float tpf) {
        if (system == null) return;
        if (system.threadMode() != ThreadMode.WORKER_DEDICATED) {
            throw new IllegalArgumentException("submitUpdate() called for non-worker system: " + system.getClass().getName());
        }

        ensureStarted(system, ctx);
        Slot slot;
        synchronized (slots) {
            slot = slots.get(system);
        }
        if (slot == null) return;

        slot.submitUpdate(tpf);
    }

    /**
     * Drain completion with default budget.
     */
    public void awaitDefaultBudget() {
        awaitBudgetNanos(defaultAwaitBudgetNanos);
    }

    /**
     * Wait a limited amount for in-flight worker ticks to finish (best-effort).
     */
    public void awaitBudgetNanos(long budgetNanos) {
        if (budgetNanos <= 0) return;

        final long deadline = System.nanoTime() + budgetNanos;
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
        }

        for (Slot slot : snapshot) {
            if (slot == null) continue;
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            slot.await(left);
        }
    }

    /**
     * Stop + release worker slot for a system.
     */
    public void stopSystem(KSystem system) {
        if (system == null) return;
        Slot slot;
        synchronized (slots) {
            slot = slots.remove(system);
        }
        if (slot != null) slot.shutdown();
    }

    /**
     * Stop all worker systems.
     */
    @Override
    public void close() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
            slots.clear();
        }
        for (Slot slot : snapshot) {
            try {
                if (slot != null) slot.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    // ======================================================================
    // Slot
    // ======================================================================

    private final class Slot {

        private final KSystem system;
        private final SystemContext ctx;
        private final String profile;

        private final ExecutorService exec;
        private final GraalScriptRuntime runtime;

        private volatile boolean started = false;
        private volatile Future<?> inFlight;
        private final AtomicInteger skippedTicks = new AtomicInteger();

        Slot(KSystem system, SystemContext ctx) {
            this.system = Objects.requireNonNull(system, "system");
            this.ctx = Objects.requireNonNull(ctx, "ctx");

            String requested = safeProfile(system);
            // IMPORTANT: worker must not share world runtime.
            if (requested == null || requested.isBlank() || "world".equalsIgnoreCase(requested.trim())) {
                requested = "sys." + system.getClass().getSimpleName().toLowerCase();
            }
            this.profile = ctx.runtimePolicy().resolveProfile(requested, WorldAppState.RequestOrigin.JAVA_PROVIDER);

            // Dedicated thread name
            String threadName = "sys-" + system.getClass().getSimpleName();
            this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(threadName));

            // NOTE: runtime is created from pool, but must be USED only on this thread.
            // We obtain it here and then "warm" it in startIfNeeded() on the owner thread.
            this.runtime = world.getRuntime(profile);
        }

        void startIfNeeded() {
            if (started) return;
            synchronized (this) {
                if (started) return;
                started = true;

                // Run onStart on the worker thread so that runtime owner-thread confinement is respected.
                inFlight = exec.submit(() -> {
                    try {
                        // Touch runtime on this thread to make it the owner (GraalScriptRuntime semantics).
                        runtime.ctx();
                    } catch (Throwable t) {
                        log.error("[scheduler] Failed to initialize runtime profile={} for system={}", profile, system.getClass().getName(), t);
                    }

                    try {
                        system.onStart(ctx);
                    } catch (Throwable t) {
                        log.error("[scheduler] Worker system onStart failed: {}", system.getClass().getName(), t);
                    }
                });
            }
        }

        void submitUpdate(float tpf) {
            // Do not overlap update ticks for the same system.
            Future<?> f = inFlight;
            if (f != null && !f.isDone()) {
                int s = skippedTicks.incrementAndGet();
                // log occasionally
                if ((s & 127) == 1) {
                    log.warn("[scheduler] Skipping tick for {} (still running). skipped={}", system.getClass().getSimpleName(), s);
                }
                return;
            }

            inFlight = exec.submit(() -> {
                try {
                    system.onUpdate(ctx, tpf);
                } catch (Throwable t) {
                    log.error("[scheduler] Worker system onUpdate failed: {}", system.getClass().getName(), t);
                }
            });
        }

        void await(long nanos) {
            Future<?> f = inFlight;
            if (f == null || f.isDone()) return;
            try {
                f.get(Math.max(1L, nanos), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ignored) {
            } catch (CancellationException ignored) {
            } catch (ExecutionException e) {
                // already logged in task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void shutdown() {
            // Stop: run onStop on the worker thread, then shutdown executor.
            try {
                Future<?> f = exec.submit(() -> {
                    try {
                        system.onStop(ctx);
                    } catch (Throwable t) {
                        log.error("[scheduler] Worker system onStop failed: {}", system.getClass().getName(), t);
                    }
                });
                try {
                    f.get(2, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            } catch (RejectedExecutionException ignored) {
            }

            exec.shutdown();
            try {
                if (!exec.awaitTermination(2, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exec.shutdownNow();
            }
        }
    }

    private static String safeProfile(KSystem system) {
        try {
            return system.runtimeProfile();
        } catch (Throwable t) {
            return "world";
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_ID = new AtomicInteger();
        private final AtomicInteger tid = new AtomicInteger();
        private final String base;

        NamedThreadFactory(String base) {
            this.base = (base == null || base.isBlank()) ? "sys" : base.trim();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(base + "-" + POOL_ID.get() + "-" + tid.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) ->
                    log.error("[scheduler] Uncaught exception in thread {}", th.getName(), ex));
            return t;
        }
    }
}