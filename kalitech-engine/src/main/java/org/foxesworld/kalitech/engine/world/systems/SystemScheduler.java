// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SystemScheduler executes {@link KSystem} updates on worker lanes (striped or dedicated),
 * applying rate limiting (Hz) and soft/hard time budgets with adaptive frequency control.
 *
 * <p>All system errors are observable (logged). The scheduler never throws from worker execution paths.</p>
 */
public final class SystemScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SystemScheduler.class);

    private static final double HZ_RECOVERY_STEP = 1.0;
    private static final double HZ_SOFT_DECAY = 0.85;
    private static final double HZ_HARD_DECAY = 0.60;

    private static final int BACKPRESSURE_PRIORITY_THRESHOLD = 60;

    private final Lane[] lanes;
    private final Map<KSystem, Slot> slots = new IdentityHashMap<>();

    private final Object owner;

    private volatile int defaultAwaitBudgetMs = 1;

    public SystemScheduler(Object owner) {
        this(owner, defaultLaneCount());
    }

    public SystemScheduler(Object owner, int laneCount) {
        this.owner = Objects.requireNonNull(owner, "owner");
        int n = Math.max(1, laneCount);
        this.lanes = new Lane[n];
        for (int i = 0; i < n; i++) lanes[i] = new Lane(i);
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

    private static int defaultLaneCount() {
        int p = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(8, Math.max(2, p / 2)));
    }

    private static int safeInt(IntSupplier s, int def) {
        try {
            return s.getAsInt();
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            return def;
        }
    }

    private static long safeLong(LongSupplier s, long def) {
        try {
            return s.getAsLong();
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            return def;
        }
    }

    private static double safeHz(DoubleSupplier s, double def) {
        try {
            double v = s.getAsDouble();
            return (v > 0 && Double.isFinite(v)) ? v : def;
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            return def;
        }
    }

    public void awaitBudgetNanos(long budgetNanos) {
        if (budgetNanos <= 0) return;

        final long deadline = System.nanoTime() + budgetNanos;

        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
        }

        for (Slot s : snapshot) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            s.await(remaining);
        }
    }

    public WorkerSystemStats[] statsSnapshot() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
        }
        WorkerSystemStats[] out = new WorkerSystemStats[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) out[i] = snapshot[i].statsSnapshot();
        return out;
    }

    private static String safeProfile(KSystem sys) {
        try {
            return sys.runtimeProfile();
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            return null;
        }
    }

    // ======================================================================

    private static String safeSysName(KSystem sys) {
        try {
            return sys.getClass().getSimpleName();
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            return "KSystem";
        }
    }

    private static double clampHz(double v, double a, double b) {
        if (!(v > 0) || !Double.isFinite(v)) v = a;
        return Math.max(a, Math.min(b, v));
    }

    /**
     * IMPORTANT: must use actual lanes.length, not defaultLaneCount().
     */
    private int laneFor(KSystem sys) {
        int n = lanes.length;
        if (n <= 1) return 0;
        return (System.identityHashCode(sys) & 0x7fffffff) % n;
    }

    private static long computeNextRun(long now, double hz) {
        if (!(hz > 0)) return now;
        long period = (long) (1_000_000_000.0 / hz);
        if (period <= 0) period = 1;
        return now + period;
    }

    private static long ema(long prev, long sample) {
        if (prev <= 0) return sample;
        return prev + (long) ((sample - prev) * 0.15);
    }

    public void ensureStarted(KSystem system, SystemContext ctx) {
        if (system == null) return;
        Slot slot = getOrCreateSlot(system, ctx);
        slot.startIfNeeded();
    }

    public void submitUpdate(KSystem system, SystemContext ctx, float tpf) {
        if (system == null) return;
        Slot slot = getOrCreateSlot(system, ctx);
        slot.startIfNeeded();
        slot.requestTick(tpf, System.nanoTime());
    }

    public void stopSystem(KSystem system) {
        if (system == null) return;

        Slot slot;
        synchronized (slots) {
            slot = slots.remove(system);
        }
        if (slot != null) {
            try {
                slot.shutdown();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.warn("[scheduler] stopSystem shutdown failed: {}", safeSysName(system), t);
            }
        }
    }

    public void awaitDefaultBudget() {
        awaitBudgetNanos(TimeUnit.MILLISECONDS.toNanos(defaultAwaitBudgetMs));
    }

    @Override
    public void close() {
        Slot[] snapshot;
        synchronized (slots) {
            snapshot = slots.values().toArray(new Slot[0]);
            slots.clear();
        }

        for (Slot s : snapshot) {
            try {
                s.shutdown();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.warn("[scheduler] slot shutdown failed: {}", s.safeId(), t);
            }
        }

        for (Lane lane : lanes) {
            try {
                lane.shutdown();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.warn("[scheduler] lane shutdown failed: index={}", lane.index, t);
            }
        }
    }

    private Slot getOrCreateSlot(KSystem system, SystemContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        Slot slot;
        synchronized (slots) {
            slot = slots.get(system);
            if (slot == null) {
                slot = new Slot(system, ctx);
                slots.put(system, slot);
            }
        }
        return slot;
    }

    private interface IntSupplier {
        int getAsInt();
    }

    private interface LongSupplier {
        long getAsLong();
    }

    private interface DoubleSupplier {
        double getAsDouble();
    }

    private static final class Lane {
        final int index;
        final ExecutorService exec;
        volatile String threadName;

        Lane(int index) {
            this.index = index;
            this.threadName = "lane-" + index;
            this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory("lane-" + index, n -> threadName = n));
        }

        void shutdown() {
            exec.shutdownNow();
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String base;
        private final java.util.function.Consumer<String> nameSink;
        private final AtomicInteger seq = new AtomicInteger();

        NamedThreadFactory(String base, java.util.function.Consumer<String> nameSink) {
            this.base = base;
            this.nameSink = nameSink;
        }

        @Override
        public Thread newThread(Runnable r) {
            String n = base + "-" + seq.incrementAndGet();
            Thread t = new Thread(r, n);
            t.setDaemon(true);
            try {
                nameSink.accept(n);
            } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            }
            return t;
        }
    }

    // ======================================================================

    private final class Slot {

        private final KSystem system;
        private final SystemContext ctx;

        private final boolean sharedLane;
        private final int laneIndex;

        private final ExecutorService exec;
        private final String profile;

        @SuppressWarnings("unused")
        private final ScriptRuntime runtime;

        private final int priority;
        private final double desiredHz;
        private final double minHz;
        private final double maxHz;
        private volatile double currentHz;
        private volatile long nextRunAtNanos = 0L;

        private final long softBudgetNanos;
        private final long hardBudgetNanos;

        private final AtomicInteger skippedRunning = new AtomicInteger();
        private final AtomicInteger skippedRateLimited = new AtomicInteger();
        private final AtomicInteger skippedBackpressure = new AtomicInteger();
        private final AtomicInteger hardBudgetBreaches = new AtomicInteger();
        private final AtomicInteger exceptions = new AtomicInteger();

        private final AtomicLong lastSubmitNanos = new AtomicLong();
        private final AtomicLong lastStartNanos = new AtomicLong();
        private final AtomicLong lastEndNanos = new AtomicLong();

        private final AtomicLong lastTickNanos = new AtomicLong();
        private final AtomicLong maxTickNanos = new AtomicLong();
        private final AtomicLong emaTickNanos = new AtomicLong();

        private final AtomicLong lastQueueLagNanos = new AtomicLong();
        private final AtomicLong emaQueueLagNanos = new AtomicLong();

        private final AtomicInteger started = new AtomicInteger(0);
        private final AtomicInteger startFailureLogged = new AtomicInteger(0);

        private volatile String threadName = "unknown";
        private volatile Future<?> inFlight;

        Slot(KSystem system, SystemContext ctx) {
            this.system = Objects.requireNonNull(system, "system");
            this.ctx = Objects.requireNonNull(ctx, "ctx");

            ThreadMode mode;
            try {
                mode = system.threadMode();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                mode = ThreadMode.WORKER_STRIPED;
            }

            this.sharedLane = (mode == ThreadMode.WORKER_STRIPED);
            this.laneIndex = sharedLane ? laneFor(system) : -1;

            this.priority = safeInt(system::priority, 50);

            this.desiredHz = safeHz(system::desiredHz, 60.0);
            this.minHz = safeHz(system::minHz, this.desiredHz);
            this.maxHz = safeHz(system::maxHz, this.desiredHz);
            this.currentHz = clampHz(this.desiredHz, this.minHz, this.maxHz);

            this.softBudgetNanos = Math.max(0L, safeLong(system::softBudgetNanos, 0L));
            this.hardBudgetNanos = Math.max(0L, safeLong(system::hardBudgetNanos, 0L));

            String requested = safeProfile(system);
            if (requested == null || requested.isBlank()) {
                requested = "sys." + safeSysName(system).toLowerCase();
            }
            if (sharedLane) requested = requested + ".lane" + laneIndex;

            // Enforce policy (non-null in fixed SystemContext).
            // Default stance: deny UNSAFE, allow others (see SystemContext).
            try {
                ctx.runtimePolicy().assertAllowed(requested, safeSysName(system), SystemContext.RuntimePolicy.Capability.WORLD_ACCESS);
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                throw new SecurityException("RuntimePolicy denied system=" + safeSysName(system) + " profile=" + requested, t);
            }

            this.profile = requested;

            if (sharedLane) {
                Lane lane = lanes[laneIndex];
                this.exec = lane.exec;
                this.threadName = lane.threadName;
            } else {
                String baseName = "sys-" + safeSysName(system);
                this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(baseName, n -> this.threadName = n));
            }

            ScriptRuntime rt = ctx.runtime(profile);
            if (rt == null) rt = ctx.runtime();
            if (rt == null) {
                throw new IllegalStateException("SystemScheduler requires ScriptRuntime in SystemContext");
            }
            this.runtime = rt;
        }

        void startIfNeeded() {
            if (!started.compareAndSet(0, 1)) return;

            ThreadMode mode;
            try {
                mode = system.threadMode();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                mode = ThreadMode.WORKER_STRIPED;
            }

            if (mode == ThreadMode.WORKER_DEDICATED || mode == ThreadMode.WORKER_STRIPED) {
                submitInternal(0f, true);
            } else {
                // Safety fallback: execute on caller thread.
                try {
                    system.onStart(ctx);
                } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                    exceptions.incrementAndGet();
                    log.error("[scheduler] onStart failed (system={}, profile={}, thread=caller)", safeSysName(system), profile, t);
                }
            }
        }

        void requestTick(float tpf, long nowNanos) {
            lastSubmitNanos.set(nowNanos);

            Future<?> f = inFlight;
            if (f != null && !f.isDone()) {
                skippedRunning.incrementAndGet();
                return;
            }

            if (!isDue(nowNanos)) {
                skippedRateLimited.incrementAndGet();
                return;
            }

            MainThreadBudgetQueue q = ctx.mainQueue();
            if (q != null && priority < BACKPRESSURE_PRIORITY_THRESHOLD) {
                if (q.isOverloaded()) {
                    skippedBackpressure.incrementAndGet();
                    return;
                }
            }

            submitInternal(tpf, false);
        }

        private void submitInternal(float tpf, boolean isStart) {
            nextRunAtNanos = computeNextRun(System.nanoTime(), currentHz);

            inFlight = exec.submit(() -> {
                threadName = Thread.currentThread().getName();

                long start = System.nanoTime();
                lastStartNanos.set(start);

                long submit = lastSubmitNanos.get();
                long lag = (submit > 0L) ? Math.max(0L, start - submit) : 0L;
                lastQueueLagNanos.set(lag);
                emaQueueLagNanos.set(ema(emaQueueLagNanos.get(), lag));

                try {
                    if (isStart) {
                        system.onStart(ctx);
                    } else {
                        system.onUpdate(ctx, tpf);
                    }
                } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                    exceptions.incrementAndGet();

                    if (isStart && startFailureLogged.compareAndSet(0, 1)) {
                        log.error("[scheduler] onStart failed (system={}, profile={}, thread={})",
                                safeSysName(system), profile, threadName, t);
                    } else {
                        log.error("[scheduler] onUpdate failed (system={}, profile={}, thread={})",
                                safeSysName(system), profile, threadName, t);
                    }
                } finally {
                    long end = System.nanoTime();
                    lastEndNanos.set(end);

                    long dt = Math.max(0L, end - start);
                    lastTickNanos.set(dt);
                    maxTickNanos.accumulateAndGet(dt, Math::max);
                    emaTickNanos.set(ema(emaTickNanos.get(), dt));

                    if (hardBudgetNanos > 0 && dt > hardBudgetNanos) {
                        hardBudgetBreaches.incrementAndGet();
                        currentHz = clampHz(currentHz * HZ_HARD_DECAY, minHz, maxHz);
                    } else if (softBudgetNanos > 0 && dt > softBudgetNanos) {
                        currentHz = clampHz(currentHz * HZ_SOFT_DECAY, minHz, maxHz);
                    } else {
                        currentHz = clampHz(currentHz + HZ_RECOVERY_STEP, minHz, maxHz);
                    }
                }
            });
        }

        void await(long budgetNanos) {
            Future<?> f = inFlight;
            if (f == null || f.isDone()) return;

            try {
                f.get(Math.max(1L, budgetNanos), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ignored) {
                // Budget exhausted; continue.
            } catch (ExecutionException e) {
                // Should be already logged in worker path; keep observable for diagnostics.
                log.debug("[scheduler] await saw ExecutionException (system={}, profile={})", safeSysName(system), profile, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.debug("[scheduler] await failed (system={}, profile={})", safeSysName(system), profile, t);
            }
        }

        void shutdown() {
            Future<?> f = inFlight;
            if (f != null) {
                try {
                    f.cancel(false);
                } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                    log.debug("[scheduler] cancel failed (system={}, profile={})", safeSysName(system), profile, t);
                }
            }

            try {
                system.onStop(ctx);
            } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                log.error("[scheduler] onStop failed (system={}, profile={})", safeSysName(system), profile, t);
            }

            if (!sharedLane) {
                try {
                    exec.shutdownNow();
                } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
                    log.warn("[scheduler] dedicated executor shutdown failed (system={}, profile={})", safeSysName(system), profile, t);
                }
            }
        }

        WorkerSystemStats statsSnapshot() {
            Future<?> f = inFlight;
            boolean runningNow = (f != null && !f.isDone());

            return new WorkerSystemStats(
                    safeSysName(system),
                    profile,
                    threadName,
                    runningNow,
                    laneIndex,
                    priority,
                    desiredHz,
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

        private boolean isDue(long now) {
            return now >= nextRunAtNanos;
        }

        private String safeId() {
            return safeSysName(system) + "@" + Integer.toHexString(System.identityHashCode(system));
        }
    }
}