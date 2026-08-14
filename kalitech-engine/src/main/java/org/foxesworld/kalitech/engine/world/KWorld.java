// FILE: org/foxesworld/kalitech/engine/world/KWorld.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.world.systems.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * World orchestrator.
 * Script is expected to provide an explicit time configuration during create().
 */
public final class KWorld {

    private final String name;
    private final WorldTime time;

    private final List<Entry> systems = new ArrayList<>();

    private KSystem[] mainSystems = new KSystem[0];
    private KSystem[] workerSystems = new KSystem[0];

    private boolean started = false;

    public KWorld(String name) {
        this(name, WorldTimeParams.defaults());
    }

    public KWorld(String name, WorldTimeParams timeParams) {
        this.name = (name == null || name.isBlank()) ? "world" : name.trim();
        this.time = new WorldTime(timeParams);
    }

    public String getName() {
        return name;
    }

    public boolean isStarted() {
        return started;
    }

    public WorldTime worldTime() {
        return time;
    }

    public void addSystem(KSystem system, int order) {
        if (started) throw new IllegalStateException("Cannot add system after world started");
        Objects.requireNonNull(system, "system");
        systems.add(new Entry(system, order));
    }

    public void start(SystemContext ctx) {
        if (started) return;
        Objects.requireNonNull(ctx, "ctx");

        systems.sort(Comparator.comparingInt(e -> e.order));

        final ArrayList<KSystem> mains = new ArrayList<>(systems.size());
        final ArrayList<KSystem> workers = new ArrayList<>(systems.size());

        for (Entry e : systems) {
            final KSystem sys = e.system;
            ThreadMode m;
            try {
                m = sys.threadMode();
            } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
                continue;
            }

            if (m == ThreadMode.WORKER_DEDICATED || m == ThreadMode.WORKER_STRIPED) workers.add(sys);
            else mains.add(sys);
        }

        mainSystems = mains.toArray(new KSystem[0]);
        workerSystems = workers.toArray(new KSystem[0]);

        for (KSystem sys : mainSystems) {
            try {
                sys.onStart(ctx);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] system onStart failed: {}", name, sys, t);
            }
        }

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.ensureStarted(sys, ctx);
                else sys.onStart(ctx);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] worker start failed: {}", name, sys, t);
            }
        }

        started = true;
    }

    public void update(SystemContext ctx, float realTpf) {
        if (!started) return;
        Objects.requireNonNull(ctx, "ctx");

        final double realDt = (double) realTpf;
        if (!Double.isFinite(realDt) || realDt <= 0.0) {
            time.beginFrame(0.0, 0.0);
            awaitWorkers(ctx, 0L);
            pumpEvents(ctx);
            return;
        }

        if (time.paused()) {
            time.beginFrame(realDt, 0.0);
            awaitWorkers(ctx, 0L);
            pumpEvents(ctx);
            return;
        }

        double dt = realDt * time.timeRate();

        Double maxDelta = time.maxDeltaSec();
        if (maxDelta != null && maxDelta.doubleValue() > 0.0) {
            dt = Math.min(dt, maxDelta.doubleValue());
        }

        Double fixedStep = time.fixedStepSec();
        if (fixedStep != null && fixedStep.doubleValue() > 0.0) {
            time.beginFrame(realDt, dt);
            runFixedStep(ctx, dt, fixedStep.doubleValue());
        } else {
            time.beginFrame(realDt, dt);
            time.beginStep(dt);
            time.advanceWorldTime(dt);
            updateSystems(ctx, (float) dt);
            time.endStep();
        }

        awaitWorkers(ctx, 0L);
        pumpEvents(ctx);
    }

    private void runFixedStep(SystemContext ctx, double dt, double step) {
        time.addAccumulator(dt);

        int maxSteps = 8;
        Double maxDelta = time.maxDeltaSec();
        if (maxDelta != null && maxDelta.doubleValue() > 0.0 && step > 0.0) {
            maxSteps = Math.max(1, (int) Math.ceil(maxDelta.doubleValue() / step));
            maxSteps = Math.min(maxSteps, 64);
        }

        int steps = 0;
        while (time.accumulatorSec() >= step && steps < maxSteps) {
            time.consumeAccumulator(step);
            time.beginStep(step);
            time.advanceWorldTime(step);
            updateSystems(ctx, (float) step);
            time.endStep();
            steps++;
        }
    }

    private void awaitWorkers(SystemContext ctx, long budgetNanos) {
        final SystemScheduler sch = ctx.scheduler();
        if (sch == null) return;

        try {
            if (budgetNanos > 0L) sch.awaitBudgetNanos(budgetNanos);
            else sch.awaitDefaultBudget();
        } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
            ctx.log().warn("[world:{}] scheduler await failed: {}", name, t.toString());
        }
    }

    private void updateSystems(SystemContext ctx, float tpfSim) {
        for (KSystem sys : mainSystems) {
            try {
                sys.onUpdate(ctx, tpfSim);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] system onUpdate failed: {}", name, sys, t);
            }
        }

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.submitUpdate(sys, ctx, tpfSim);
                else sys.onUpdate(ctx, tpfSim);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] worker update failed: {}", name, sys, t);
            }
        }
    }

    private void pumpEvents(SystemContext ctx) {
        try {
            if (ctx.events() != null) ctx.events().pump();
        } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
            ctx.log().warn("[world:{}] events.pump failed: {}", name, t.toString());
        }
    }

    public void stop(SystemContext ctx) {
        if (!started) return;
        Objects.requireNonNull(ctx, "ctx");

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.stopSystem(sys);
                else sys.onStop(ctx);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] worker stop failed: {}", name, sys, t);
            }
        }

        for (KSystem sys : mainSystems) {
            try {
                sys.onStop(ctx);
            } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
                ctx.log().error("[world:{}] system onStop failed: {}", name, sys, t);
            }
        }

        started = false;
    }

    public void hotReload(SystemContext ctx, String reason) {
        Objects.requireNonNull(ctx, "ctx");
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason;

        if (started) stop(ctx);

        try {
            ctx.hotReloadHub().fire(why);
        } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
            ctx.log().warn("[world:{}] hotReload hub.fire failed: {}", name, t.toString());
        }

        for (KSystem sys : mainSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, why);
                } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
                }
            }
        }
        for (KSystem sys : workerSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, why);
                } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
                }
            }
        }

        try {
            if (ctx.api() instanceof EngineApiImpl impl) {
                impl.__resetWorldState(why);
            }
        } catch (Throwable t) {
                ScriptFailureBoundary.rethrowIfFatal(t);
            ctx.log().warn("[world:{}] reset failed: {}", name, t.toString());
        }

        start(ctx);
    }

    private static final class Entry {
        final KSystem system;
        final int order;

        Entry(KSystem system, int order) {
            this.system = system;
            this.order = order;
        }
    }

    public WorldTime getTime() {
        return time;
    }
}