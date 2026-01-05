// FILE: SystemScheduler.java
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.world.WorldAppState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDPR-style system scheduler:
 * - MAIN: inline on world thread, shared "world" runtime
 * - WORKER_DEDICATED: dedicated single thread + isolated runtime profile
 * - WORKER_STRIPED: shared fixed-count lanes (single-thread executors), stable AAA parallelism
 *
 * Added:
 * - hard worker sandbox (API-level) via main-thread proxies
 * - profiling stats per worker (tick time, skip, queue lag)
 */
public final class SystemScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SystemScheduler.class);

    private final WorldAppState world;
    private final Map<KSystem, Slot> slots = new IdentityHashMap<>();

    /** Shared worker lanes for {@link ThreadMode#WORKER_STRIPED}. Each lane is single-threaded. */
    private final Lane[] lanes;

    /** Default max time to wait for worker ticks per frame. 0 = don't wait. */
    private volatile long defaultAwaitBudgetNanos = TimeUnit.MILLISECONDS.toNanos(2);

    public SystemScheduler(WorldAppState world) {
        this(world, defaultLaneCount());
    }

    /**
     * @param laneCount number of shared worker lanes for {@link ThreadMode#WORKER_STRIPED}.
     */
    public SystemScheduler(WorldAppState world, int laneCount) {
        this.world = Objects.requireNonNull(world, "world");

        int n = Math.max(1, laneCount);
        this.lanes = new Lane[n];
        for (int i = 0; i < n; i++) {
            this.lanes[i] = new Lane(i);
        }
        log.info("[scheduler] worker lanes={}", n);
    }

    public int getLaneCount() {
        return lanes.length;
    }

    public void setDefaultAwaitBudgetMs(int ms) {
        this.defaultAwaitBudgetNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, ms));
    }

    public int getDefaultAwaitBudgetMs() {
        return (int) TimeUnit.NANOSECONDS.toMillis(defaultAwaitBudgetNanos);
    }

    /** Ensure worker slot is created and started (on-demand). */
    public void ensureStarted(KSystem system, SystemContext ctx) {
        if (system == null) return;
        ThreadMode mode = system.threadMode();
        if (mode != ThreadMode.WORKER_DEDICATED && mode != ThreadMode.WORKER_STRIPED) return;

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

    /** Submit worker update tick (non-blocking). */
    public void submitUpdate(KSystem system, SystemContext ctx, float tpf) {
        if (system == null) return;
        ThreadMode mode = system.threadMode();
        if (mode != ThreadMode.WORKER_DEDICATED && mode != ThreadMode.WORKER_STRIPED) {
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

    /** Drain completion with default budget. */
    public void awaitDefaultBudget() {
        awaitBudgetNanos(defaultAwaitBudgetNanos);
    }

    /** Wait a limited amount for in-flight worker ticks to finish (best-effort). */
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

    /** Stop + release worker slot for a system. */
    public void stopSystem(KSystem system) {
        if (system == null) return;
        Slot slot;
        synchronized (slots) {
            slot = slots.remove(system);
        }
        if (slot != null) slot.shutdown();
    }

    /** Snapshot stats for all worker systems. */
    public WorkerSystemStats[] statsSnapshot() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
        }
        WorkerSystemStats[] out = new WorkerSystemStats[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            out[i] = snapshot[i].statsSnapshot();
        }
        return out;
    }

    /** Stop all worker systems. */
    @Override
    public void close() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
            slots.clear();
        }
        for (Slot slot : snapshot) {
            try { if (slot != null) slot.shutdown(); } catch (Exception ignored) {}
        }

        // Shutdown striped lanes (after systems stopped)
        for (Lane lane : lanes) {
            try { if (lane != null) lane.shutdown(); } catch (Exception ignored) {}
        }
    }

    // ======================================================================
    // Slot
    // ======================================================================

    private final class Slot {

        private final KSystem system;
        private final SystemContext ctx;
        private final String profile;

        private final boolean sharedLane;
        private final int laneIndex; // -1 for dedicated

        private final ExecutorService exec;
        private final ScriptRuntime runtime;

        private volatile boolean started = false;
        private volatile Future<?> inFlight;
        private final AtomicInteger skippedTicks = new AtomicInteger();

        // profiling
        private final AtomicLong lastSubmitNanos = new AtomicLong();
        private final AtomicLong lastStartNanos = new AtomicLong();
        private final AtomicLong lastEndNanos = new AtomicLong();
        private final AtomicLong lastTickNanos = new AtomicLong();
        private final AtomicLong maxTickNanos = new AtomicLong();
        private final AtomicLong emaTickNanos = new AtomicLong();
        private final AtomicLong lastQueueLagNanos = new AtomicLong();

        private volatile String threadName = "unknown";

        Slot(KSystem system, SystemContext ctx) {
            this.system = Objects.requireNonNull(system, "system");
            this.ctx = Objects.requireNonNull(ctx, "ctx");

            ThreadMode mode = system.threadMode();
            this.sharedLane = (mode == ThreadMode.WORKER_STRIPED);
            this.laneIndex = sharedLane ? laneFor(system) : -1;

            String requested = safeProfile(system);
            // IMPORTANT: any worker must not use "world" runtime profile.
            if (requested == null || requested.isBlank() || "world".equalsIgnoreCase(requested.trim())) {
                requested = "sys." + system.getClass().getSimpleName().toLowerCase();
            }

            // For striped: profile is per-lane to preserve ScriptRuntime thread ownership.
            if (sharedLane) {
                requested = requested + ".lane" + laneIndex;
            }

            this.profile = ctx.runtimePolicy().resolveProfile(requested, WorldAppState.RequestOrigin.JAVA_PROVIDER);

            if (sharedLane) {
                Lane lane = lanes[laneIndex];
                this.exec = lane.exec;
                this.threadName = lane.threadName;
            } else {
                String baseName = "sys-" + system.getClass().getSimpleName();
                this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(baseName, n -> this.threadName = n));
            }

            // Runtime from pool (MUST be used only on its owner thread).
            this.runtime = world.getRuntime(profile);
        }

        void startIfNeeded() {
            if (started) return;
            synchronized (this) {
                if (started) return;
                started = true;

                inFlight = exec.submit(() -> {
                    // Bind sandbox globals inside the worker runtime (on its owner thread!)
                    try {
                        runtime.ctx(); // claim owner thread
                    } catch (Throwable t) {
                        log.error("[scheduler] Failed to init runtime profile={} for system={}", profile, system.getClass().getName(), t);
                    }

                    try {
                        world.installWorkerSandboxGlobals(runtime, ctx, system.getClass().getSimpleName());
                    } catch (Throwable t) {
                        log.error("[scheduler] Failed to install worker sandbox globals for {} profile={}",
                                system.getClass().getName(), profile, t);
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
            Future<?> f = inFlight;
            if (f != null && !f.isDone()) {
                int s = skippedTicks.incrementAndGet();
                // CDPR-style: don't spam logs, but escalate when a system is persistently over budget.
                if ((s & 127) == 1 || s == 60 || s == 300) {
                    log.warn("[scheduler] Skipping tick for {} (still running). skipped={} lane={} prof={}",
                            system.getClass().getSimpleName(), s, laneIndex, profile);
                }
                return;
            }

            final long submitAt = System.nanoTime();
            lastSubmitNanos.set(submitAt);

            inFlight = exec.submit(() -> {
                final long startAt = System.nanoTime();
                lastStartNanos.set(startAt);
                lastQueueLagNanos.set(Math.max(0L, startAt - submitAt));

                try {
                    system.onUpdate(ctx, tpf);
                } catch (Throwable t) {
                    log.error("[scheduler] Worker system onUpdate failed: {}", system.getClass().getName(), t);
                } finally {
                    final long endAt = System.nanoTime();
                    lastEndNanos.set(endAt);

                    final long tick = Math.max(0L, endAt - startAt);
                    lastTickNanos.set(tick);

                    maxTickNanos.accumulateAndGet(tick, Math::max);

                    // EMA (alpha ~ 0.10) in integer nanos
                    long prev = emaTickNanos.get();
                    long next = (prev == 0L) ? tick : (prev + ((tick - prev) / 10L));
                    emaTickNanos.set(next);
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
            } catch (ExecutionException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void shutdown() {
            // Always attempt onStop on the owning lane/thread.
            try {
                Future<?> f = exec.submit(() -> {
                    try {
                        system.onStop(ctx);
                    } catch (Throwable t) {
                        log.error("[scheduler] Worker system onStop failed: {}", system.getClass().getName(), t);
                    }
                });
                try { f.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
            } catch (RejectedExecutionException ignored) {}

            // Only dedicated slots own their thread.
            if (!sharedLane) {
                exec.shutdown();
                try {
                    if (!exec.awaitTermination(2, TimeUnit.SECONDS)) exec.shutdownNow();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exec.shutdownNow();
                }
            }
        }

        WorkerSystemStats statsSnapshot() {
            Future<?> f = inFlight;
            boolean running = (f != null && !f.isDone());
            return new WorkerSystemStats(
                    system.getClass().getSimpleName(),
                    profile,
                    threadName,
                    running,
                    lastSubmitNanos.get(),
                    lastStartNanos.get(),
                    lastEndNanos.get(),
                    lastTickNanos.get(),
                    emaTickNanos.get(),
                    maxTickNanos.get(),
                    lastQueueLagNanos.get(),
                    skippedTicks.get()
            );
        }
    }

    // ======================================================================
    // Striped lanes
    // ======================================================================

    private static int defaultLaneCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        int n = Math.max(1, cores - 1);     // keep 1 for main/render when possible
        return Math.min(n, 12);             // sane dev cap; can override via ctor
    }

    private int laneFor(KSystem system) {
        int h = System.identityHashCode(system);
        h ^= (h >>> 16);
        h &= 0x7fffffff;
        return (lanes.length == 1) ? 0 : (h % lanes.length);
    }

    private final class Lane {
        final int index;
        final ExecutorService exec;
        volatile String threadName = "lane-";

        Lane(int index) {
            this.index = index;
            String baseName = "lane" + index;
            this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(baseName, n -> this.threadName = n));
        }

        void shutdown() {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(2, TimeUnit.SECONDS)) exec.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exec.shutdownNow();
            }
        }
    }

    private static String safeProfile(KSystem system) {
        try { return system.runtimeProfile(); } catch (Throwable t) { return "world"; }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_ID = new AtomicInteger();
        private final AtomicInteger tid = new AtomicInteger();
        private final String base;
        private final java.util.function.Consumer<String> onName;

        NamedThreadFactory(String base, java.util.function.Consumer<String> onName) {
            this.base = (base == null || base.isBlank()) ? "sys" : base.trim();
            this.onName = onName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            String name = base + "-" + POOL_ID.get() + "-" + tid.incrementAndGet();
            t.setName(name);
            if (onName != null) onName.accept(name);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) ->
                    log.error("[scheduler] Uncaught exception in thread {}", th.getName(), ex));
            return t;
        }
    }
}