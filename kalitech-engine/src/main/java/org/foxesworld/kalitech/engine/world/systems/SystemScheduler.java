// Author: KΛYLΛ
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
 * SystemScheduler
 *
 * English:
 * Schedules worker-mode {@link KSystem} ticks on dedicated threads or striped worker lanes.
 * Provides production stability features:
 *  - Deadline-based tick-rate control (Hz) to avoid over-updating non-critical systems.
 *  - Backpressure from {@link MainThreadBudgetQueue} to prevent apply pipeline overload.
 *  - Per-system timing statistics (EMA tick time, queue lag, skips, hard budget breaches).
 *
 * Design constraints:
 *  - ScriptRuntime is thread-confined, therefore {@link ThreadMode#WORKER_STRIPED} uses per-lane runtime profiles.
 */
public final class SystemScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SystemScheduler.class);

    private final WorldAppState world;

    private final Lane[] lanes;
    private final Map<KSystem, Slot> slots = new IdentityHashMap<>();

    // default wait budget for worker completion (ms)
    private volatile int defaultAwaitBudgetMs = 1;

    // Adaptive scaling parameters (kept simple and stable)
    private static final double HZ_RECOVERY_STEP = 1.0;     // Hz per successful tick
    private static final double HZ_SOFT_DECAY = 0.85;       // when soft budget exceeded
    private static final double HZ_HARD_DECAY = 0.60;       // when hard budget exceeded
    private static final int BACKPRESSURE_PRIORITY_THRESHOLD = 60; // below this, systems may be skipped

    public SystemScheduler(WorldAppState world) {
        this(world, defaultLaneCount());
    }

    public SystemScheduler(WorldAppState world, int laneCount) {
        this.world = Objects.requireNonNull(world, "world");
        int n = Math.max(1, laneCount);
        this.lanes = new Lane[n];
        for (int i = 0; i < n; i++) {
            lanes[i] = new Lane(i);
        }
    }

    public int getLaneCount() {
        return lanes.length;
    }

    public void setDefaultAwaitBudgetMs(int ms) {
        this.defaultAwaitBudgetMs = Math.max(0, ms);
    }

    public int getDefaultAwaitBudgetMs() {
        return defaultAwaitBudgetMs;
    }

    /** Ensure a worker system is started and sandbox globals are installed. */
    public void ensureStarted(KSystem system, SystemContext ctx) {
        if (system == null) return;
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

    /** Submit a tick request (scheduler decides whether it is due and allowed). */
    public void submitUpdate(KSystem system, SystemContext ctx, float tpf) {
        if (system == null) return;
        Slot slot;
        synchronized (slots) {
            slot = slots.get(system);
            if (slot == null) {
                slot = new Slot(system, ctx);
                slots.put(system, slot);
            }
        }
        slot.startIfNeeded();
        slot.requestTick(tpf, System.nanoTime());
    }

    /** Drain completion with default budget. */
    public void awaitDefaultBudget() {
        awaitBudgetNanos(TimeUnit.MILLISECONDS.toNanos(defaultAwaitBudgetMs));
    }

    /** Wait a limited amount for in-flight worker ticks to finish (best-effort). */
    public void awaitBudgetNanos(long budgetNanos) {
        if (budgetNanos <= 0) return;

        final long deadline = System.nanoTime() + budgetNanos;
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
        }

        for (Slot s : snapshot) {
            long now = System.nanoTime();
            long remaining = deadline - now;
            if (remaining <= 0) break;
            s.await(remaining);
        }
    }

    /** Stop a specific system slot. */
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

    /** Stop all worker systems and shutdown dedicated executors (lanes are shared and kept alive by the world). */
    @Override
    public void close() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
            slots.clear();
        }
        for (Slot s : snapshot) {
            try { s.shutdown(); } catch (Exception ignored) {}
        }
        // Lanes are owned by this scheduler; shut them down.
        for (Lane lane : lanes) {
            lane.shutdown();
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

        // Adaptive scheduling
        private final int priority;
        private final double desiredHz;
        private final double minHz;
        private final double maxHz;
        private volatile double currentHz;
        private volatile long nextRunAtNanos = 0L;

        private final long softBudgetNanos;
        private final long hardBudgetNanos;

        // stats counters
        private final AtomicInteger skippedRunning = new AtomicInteger();
        private final AtomicInteger skippedRateLimited = new AtomicInteger();
        private final AtomicInteger skippedBackpressure = new AtomicInteger();
        private final AtomicInteger hardBudgetBreaches = new AtomicInteger();
        private final AtomicInteger exceptions = new AtomicInteger();

        // profiling timestamps
        private final AtomicLong lastSubmitNanos = new AtomicLong();
        private final AtomicLong lastStartNanos = new AtomicLong();
        private final AtomicLong lastEndNanos = new AtomicLong();

        // profiling durations
        private final AtomicLong lastTickNanos = new AtomicLong();
        private final AtomicLong maxTickNanos = new AtomicLong();
        private final AtomicLong emaTickNanos = new AtomicLong();

        private final AtomicLong lastQueueLagNanos = new AtomicLong();
        private final AtomicLong emaQueueLagNanos = new AtomicLong();

        private volatile String threadName = "unknown";

        Slot(KSystem system, SystemContext ctx) {
            this.system = Objects.requireNonNull(system, "system");
            this.ctx = Objects.requireNonNull(ctx, "ctx");

            ThreadMode mode = system.threadMode();
            this.sharedLane = (mode == ThreadMode.WORKER_STRIPED);
            this.laneIndex = sharedLane ? laneFor(system) : -1;

            // Scheduling hints
            this.priority = safeInt(() -> system.priority(), 50);
            this.desiredHz = safeHz(() -> system.desiredHz(), 60.0);
            this.minHz = safeHz(() -> system.minHz(), this.desiredHz);
            this.maxHz = safeHz(() -> system.maxHz(), this.desiredHz);
            this.currentHz = clampHz(this.desiredHz, this.minHz, this.maxHz);

            this.softBudgetNanos = Math.max(0L, safeLong(() -> system.softBudgetNanos(), 0L));
            this.hardBudgetNanos = Math.max(0L, safeLong(() -> system.hardBudgetNanos(), 0L));

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
                        exceptions.incrementAndGet();
                        log.error("[scheduler] Worker system onStart failed: {}", system.getClass().getName(), t);
                    }
                });
            }
        }

        void requestTick(float tpf, long nowNanos) {
            // Deadline-based tick-rate: skip if not due.
            long dueAt = nextRunAtNanos;
            if (dueAt > 0 && nowNanos < dueAt) {
                skippedRateLimited.incrementAndGet();
                return;
            }

            double hz = currentHz;
            if (!Double.isFinite(hz) || hz <= 0) hz = desiredHz > 0 ? desiredHz : 60.0;
            long periodNanos = (long) (1_000_000_000.0 / hz);
            if (periodNanos < 1_000_000L) periodNanos = 1_000_000L; // clamp ~1ms

            // Move deadline forward immediately to avoid spam when skipping due to pressure.
            nextRunAtNanos = nowNanos + periodNanos;

            // Backpressure: when main apply queue is overloaded, throttle non-critical command generators.
            MainThreadBudgetQueue q = ctx.mainQueue();
            if (q != null && q.isOverloaded()) {
                boolean generates = safeBool(() -> system.generatesMainThreadCommands(), false);
                if (generates && priority < BACKPRESSURE_PRIORITY_THRESHOLD) {
                    skippedBackpressure.incrementAndGet();
                    PerfMarks pm = world.getPerfMarks();
                    if (pm != null) pm.markBackpressure(system.getClass().getSimpleName(), q.pending(), q.getOverloadPendingThreshold());
                    // scale down a bit while pressured
                    currentHz = clampHz(currentHz * 0.90, minHz, maxHz);
                    return;
                }
            }

            Future<?> f = inFlight;
            if (f != null && !f.isDone()) {
                int s = skippedRunning.incrementAndGet();
                if ((s & 127) == 1 || s == 60 || s == 300) {
                    log.warn("[scheduler] Skipping tick for {} (still running). skipped={} lane={} prof={}",
                            system.getClass().getSimpleName(), s, laneIndex, profile);
                }
                // Under persistent "still running", scale down a bit.
                currentHz = clampHz(currentHz * 0.95, minHz, maxHz);
                return;
            }

            final long submitAt = nowNanos;
            lastSubmitNanos.set(submitAt);

            inFlight = exec.submit(() -> {
                final long startAt = System.nanoTime();
                lastStartNanos.set(startAt);

                long qLag = Math.max(0L, startAt - submitAt);
                lastQueueLagNanos.set(qLag);
                updateEma(emaQueueLagNanos, qLag);

                boolean ok = true;
                try {
                    system.onUpdate(ctx, tpf);
                } catch (Throwable t) {
                    ok = false;
                    exceptions.incrementAndGet();
                    log.error("[scheduler] Worker system onUpdate failed: {}", system.getClass().getName(), t);
                } finally {
                    final long endAt = System.nanoTime();
                    lastEndNanos.set(endAt);

                    long tick = Math.max(0L, endAt - startAt);
                    lastTickNanos.set(tick);
                    updateMax(maxTickNanos, tick);
                    updateEma(emaTickNanos, tick);

                    // Budget policy: adjust tick rate (no hard interrupts; keep engine stable).
                    applyBudgetPolicy(ok, tick);

                    // Mark budget offenders (cheap hint)
                    if (softBudgetNanos > 0 && tick > softBudgetNanos) {
                        PerfMarks pm = world.getPerfMarks();
                        if (pm != null) pm.markBudget(system.getClass().getSimpleName(), tick, softBudgetNanos, true);
                    }
                }
            });
        }

        private void applyBudgetPolicy(boolean ok, long tickNanos) {
            if (!ok) {
                // exceptions: reduce a bit to prevent cascading.
                currentHz = clampHz(currentHz * 0.90, minHz, maxHz);
                return;
            }

            // Hard budget breach
            if (hardBudgetNanos > 0 && tickNanos > hardBudgetNanos) {
                hardBudgetBreaches.incrementAndGet();
                currentHz = clampHz(currentHz * HZ_HARD_DECAY, minHz, maxHz);
                return;
            }

            // Soft budget breach
            if (softBudgetNanos > 0 && tickNanos > softBudgetNanos) {
                currentHz = clampHz(currentHz * HZ_SOFT_DECAY, minHz, maxHz);
                return;
            }

            // Recover slowly towards desiredHz
            if (currentHz < desiredHz) {
                currentHz = clampHz(currentHz + HZ_RECOVERY_STEP, minHz, maxHz);
            } else if (currentHz > desiredHz) {
                // gentle return down to desired
                currentHz = clampHz(Math.max(desiredHz, currentHz - HZ_RECOVERY_STEP), minHz, maxHz);
            }
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

        WorkerSystemStats statsSnapshot() {
            Future<?> f = inFlight;
            boolean running = (f != null && !f.isDone());
            return new WorkerSystemStats(
                    system.getClass().getSimpleName(),
                    profile,
                    threadName,
                    running,
                    laneIndex,
                    priority,
                    currentHz,
                    lastSubmitNanos.get(),
                    lastStartNanos.get(),
                    lastEndNanos.get(),
                    lastTickNanos.get(),
                    emaTickNanos.get(),
                    maxTickNanos.get(),
                    lastQueueLagNanos.get(),
                    emaQueueLagNanos.get(),
                    skippedRunning.get(),
                    skippedRateLimited.get(),
                    skippedBackpressure.get(),
                    hardBudgetBreaches.get(),
                    exceptions.get()
            );
        }

        void shutdown() {
            // Always attempt onStop on the owning lane/thread.
            try {
                Future<?> f = exec.submit(() -> {
                    try {
                        system.onStop(ctx);
                    } catch (Throwable t) {
                        exceptions.incrementAndGet();
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
    }

    // ======================================================================
    // Lanes
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
        volatile String threadName;

        Lane(int index) {
            this.index = index;
            this.threadName = "lane-" + index;
            String base = "lane-" + index;
            this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(base, n -> this.threadName = n));
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

    // ======================================================================
    // Utils
    // ======================================================================

    private static double clampHz(double v, double min, double max) {
        if (!Double.isFinite(v) || v <= 0) return Math.max(1e-3, min);
        if (!Double.isFinite(min) || min <= 0) min = 1e-3;
        if (!Double.isFinite(max) || max < min) max = min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static void updateMax(AtomicLong max, long v) {
        long cur;
        do {
            cur = max.get();
            if (v <= cur) return;
        } while (!max.compareAndSet(cur, v));
    }

    private static void updateEma(AtomicLong ema, long sample) {
        // EMA with alpha ~ 0.1 (cheap integer math)
        long prev = ema.get();
        if (prev == 0L) {
            ema.set(sample);
            return;
        }
        long next = prev + ((sample - prev) / 10);
        ema.set(next);
    }

    private static int safeInt(IntSupplier s, int fb) {
        try { return s.getAsInt(); } catch (Throwable t) { return fb; }
    }

    private static long safeLong(LongSupplier s, long fb) {
        try { return s.getAsLong(); } catch (Throwable t) { return fb; }
    }

    private static boolean safeBool(BooleanSupplier s, boolean fb) {
        try { return s.getAsBoolean(); } catch (Throwable t) { return fb; }
    }

    private static double safeHz(DoubleSupplier s, double fb) {
        double v;
        try { v = s.getAsDouble(); } catch (Throwable t) { return fb; }
        if (!Double.isFinite(v) || v <= 0) return fb;
        return v;
    }

    private interface IntSupplier { int getAsInt(); }
    private interface LongSupplier { long getAsLong(); }
    private interface BooleanSupplier { boolean getAsBoolean(); }
    private interface DoubleSupplier { double getAsDouble(); }

    private static final class NamedThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_ID = new AtomicInteger();
        private final AtomicInteger tid = new AtomicInteger();
        private final String base;
        private final java.util.function.Consumer<String> onName;

        NamedThreadFactory(String base, java.util.function.Consumer<String> onName) {
            this.base = Objects.requireNonNull(base, "base");
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