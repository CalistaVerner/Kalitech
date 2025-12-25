// FILE: ScriptSubsystem.java
package org.foxesworld.kalitech.engine.script;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.script.lifecycle.ScriptLifecycle;
import org.foxesworld.kalitech.engine.script.profiler.ScriptProfiler;

import java.io.Closeable;
import java.util.Set;

@Deprecated
public final class ScriptSubsystem implements Closeable {

    private int maxJobsPerFrame = 4096;
    private long jobsBudgetNanos = 2_000_000L; // 2ms

    private int maxEventsPerFrame = ScriptEventBus.DEFAULT_MAX_EVENTS_PER_FRAME;
    private long eventsBudgetNanos = ScriptEventBus.DEFAULT_TIME_BUDGET_NANOS;

    private final GraalScriptRuntime runtime;
    private final ScriptEventBus events;
    private final ScriptProfiler profiler;
    private final ScriptLifecycle lifecycle;
    private final HotReloadWatcher watcher;

    public ScriptSubsystem(EcsWorld ecs,
                           SimpleApplication app,
                           GraalScriptRuntime.ModuleSourceProvider sourceProvider,
                           HotReloadWatcher watcher) {

        this.runtime = new GraalScriptRuntime();
        this.runtime.setModuleSourceProvider(sourceProvider);

        this.events = new ScriptEventBus();

        this.profiler = new ScriptProfiler()
                .setEnabled(true)
                .setReportEveryNanos(2_000_000_000L) // 2s
                .setTopN(8);

        this.lifecycle = new ScriptLifecycle(ecs, app, events, runtime, profiler);
        this.watcher = watcher;
    }

    public GraalScriptRuntime runtime() { return runtime; }
    public ScriptEventBus events() { return events; }
    public ScriptLifecycle lifecycle() { return lifecycle; }
    public ScriptProfiler profiler() { return profiler; }

    public ScriptSubsystem setJobsBudget(int maxJobsPerFrame, long budgetNanos) {
        this.maxJobsPerFrame = Math.max(0, maxJobsPerFrame);
        this.jobsBudgetNanos = Math.max(0L, budgetNanos);
        return this;
    }

    public ScriptSubsystem setEventsBudget(int maxEventsPerFrame, long budgetNanos) {
        this.maxEventsPerFrame = Math.max(0, maxEventsPerFrame);
        this.eventsBudgetNanos = Math.max(0L, budgetNanos);
        return this;
    }

    public void update(float tpf) {
        // 1) jobs (budgeted)
        long t0 = profiler.begin();
        int drained = runtime.jobs().drainBudgeted(maxJobsPerFrame, jobsBudgetNanos);
        long dt = System.nanoTime() - t0;
        profiler.recordJobs(drained, dt);

        // 2) hot reload
        Set<String> changed = (watcher != null) ? watcher.pollChanged() : Set.of();
        if (!changed.isEmpty()) lifecycle.onHotReloadChanged(changed);

        // 3) events (budgeted)
        long e0 = profiler.begin();
        int pumped = events.pump(maxEventsPerFrame, eventsBudgetNanos);
        long edt = System.nanoTime() - e0;
        profiler.recordEvents(pumped, edt);

        // 4) entity scripts
        lifecycle.update(tpf);

        // report window (once per N seconds)
        profiler.tick();
    }

    public void reset() {
        lifecycle.reset();
        events.clearAll();
        runtime.jobs().clear();
        runtime.invalidatePrefix("Scripts/");
    }

    @Override
    public void close() {
        try { if (watcher != null) watcher.close(); } catch (Exception ignored) {}
        try { lifecycle.reset(); } catch (Exception ignored) {}
        try { events.clearAll(); } catch (Exception ignored) {}
        try { runtime.jobs().clear(); } catch (Exception ignored) {}
        runtime.close();
    }
}
