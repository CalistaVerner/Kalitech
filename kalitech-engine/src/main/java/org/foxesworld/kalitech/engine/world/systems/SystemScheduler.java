// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SystemScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SystemScheduler.class);

    private static final double HZ_RECOVERY_STEP = 1.0;
    private final Lane[] lanes;
    private final Map<KSystem, Slot> slots = new IdentityHashMap<>();

    private volatile int defaultAwaitBudgetMs = 1;
    private static final double HZ_SOFT_DECAY = 0.85;
    private static final double HZ_HARD_DECAY = 0.60;
    private static final int BACKPRESSURE_PRIORITY_THRESHOLD = 60;
    private final Object owner; // keep opaque (WorldAppState or any host)

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

    public void awaitDefaultBudget() {
        awaitBudgetNanos(TimeUnit.MILLISECONDS.toNanos(defaultAwaitBudgetMs));
    }

    private static int defaultLaneCount() {
        int p = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(8, Math.max(2, p / 2)));
    }

    public void stopSystem(KSystem system) {
        if (system == null) return;
        Slot slot;
        synchronized (slots) {
            slot = slots.remove(system);
        }
        if (slot != null) slot.shutdown();
    }

    private static int safeInt(IntSupplier s, int def) {
        try {
            return s.getAsInt();
        } catch (Throwable t) {
            return def;
        }
    }

    private static long safeLong(LongSupplier s, long def) {
        try {
            return s.getAsLong();
        } catch (Throwable t) {
            return def;
        }
    }

    // ======================================================================

    private static double safeHz(DoubleSupplier s, double def) {
        try {
            double v = s.getAsDouble();
            return (v > 0 && Double.isFinite(v)) ? v : def;
        } catch (Throwable t) {
            return def;
        }
    }

    // ======================================================================

    private static String safeProfile(KSystem sys) {
        try {
            return sys.runtimeProfile();
        } catch (Throwable t) {
            return null;
        }
    }

    private static double clampHz(double v, double a, double b) {
        if (!(v > 0) || !Double.isFinite(v)) v = a;
        return Math.max(a, Math.min(b, v));
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
            } catch (Throwable ignored) {
            }
        }

        for (Lane lane : lanes) {
            try {
                lane.shutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * IMPORTANT: must use actual lanes.length, not defaultLaneCount().
     */
    private int laneFor(KSystem sys) {
        int n = lanes.length;
        if (n <= 1) return 0;
        return (System.identityHashCode(sys) & 0x7fffffff) % n;
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
            try {
                exec.shutdownNow();
            } catch (Throwable ignored) {
            }
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
            }
            return t;
        }
    }

    private final class Slot {

        private final KSystem system;
        private final SystemContext ctx;
        private final String profile;

        private final boolean sharedLane;
        private final int laneIndex;

        private final ExecutorService exec;

        @SuppressWarnings("unused")
        private final ScriptRuntime runtime; // can be used by systems if they pull it from ctx.runtime(profile)

        private volatile boolean started = false;
        private volatile Future<?> inFlight;

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

        private volatile String threadName = "unknown";

        Slot(KSystem system, SystemContext ctx) {
            this.system = Objects.requireNonNull(system, "system");
            this.ctx = Objects.requireNonNull(ctx, "ctx");

            ThreadMode mode = system.threadMode();
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
                requested = "sys." + system.getClass().getSimpleName().toLowerCase();
            }
            if (sharedLane) requested = requested + ".lane" + laneIndex;

            // policy optional
            SystemContext.RuntimePolicy pol = ctx.runtimePolicy();
            this.profile = (pol != null)
                    ? pol.resolveProfile(requested, SystemContext.RuntimePolicy.Origin.JAVA_PROVIDER)
                    : requested;

            if (sharedLane) {
                Lane lane = lanes[laneIndex];
                this.exec = lane.exec;
                this.threadName = lane.threadName;
            } else {
                String baseName = "sys-" + system.getClass().getSimpleName();
                this.exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(baseName, n -> this.threadName = n));
            }

            // runtime per profile (provider optional; fallback to ctx.runtime())
            ScriptRuntime rt = ctx.runtime(profile);
            if (rt == null) rt = ctx.runtime();
            if (rt == null) throw new IllegalStateException("SystemScheduler requires ScriptRuntime in SystemContext");
            this.runtime = rt;
        }

        void startIfNeeded() {
            if (started) return;
            started = true;

            if (system.threadMode() == ThreadMode.WORKER_DEDICATED || system.threadMode() == ThreadMode.WORKER_STRIPED) {
                // start on worker
                submitInternal(0f, true);
            } else {
                // fallback safety
                try {
                    system.onStart(ctx);
                } catch (Throwable t) {
                    exceptions.incrementAndGet();
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

            // backpressure hook (optional)
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
                long lag = (submit > 0L) ? (start - submit) : 0L;
                lastQueueLagNanos.set(lag);
                emaQueueLagNanos.set(ema(emaQueueLagNanos.get(), lag));

                try {
                    if (isStart) system.onStart(ctx);
                    else system.onUpdate(ctx, tpf);
                } catch (Throwable t) {
                    exceptions.incrementAndGet();
                    log.error("[scheduler] {} failed (profile={}, thread={})", system, profile, threadName, t);
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
            } catch (Throwable ignored) {
            }
        }

        void shutdown() {
            Future<?> f = inFlight;
            if (f != null) {
                try {
                    f.cancel(false);
                } catch (Throwable ignored) {
                }
            }
            try {
                if (!sharedLane) exec.shutdownNow();
            } catch (Throwable ignored) {
            }

            try {
                system.onStop(ctx);
            } catch (Throwable ignored) {
            }
        }

        WorkerSystemStats statsSnapshot() {
            Future<?> f = inFlight;
            boolean runningNow = (f != null && !f.isDone());

            return new WorkerSystemStats(
                    system.getClass().getSimpleName(),
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
    }
}