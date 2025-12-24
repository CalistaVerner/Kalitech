package org.foxesworld.kalitech.engine.script;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.script.lifecycle.ScriptLifecycle;

import java.io.Closeable;
import java.util.Set;

/**
 * ScriptSubsystem v2 — единая точка входа. Только orchestration.
 *
 * Per-frame:
 * 1) drain jobs (from other threads)
 * 2) poll file changes -> invalidate + restart affected
 * 3) pump events
 * 4) update lifecycle
 */
public final class ScriptSubsystem implements Closeable {

    private final GraalScriptRuntime runtime;
    private final ScriptJobQueue jobs;
    private final ScriptEventBus events;
    private final ScriptLifecycle lifecycle;
    private final HotReloadWatcher watcher;

    public ScriptSubsystem(EcsWorld ecs,
                           SimpleApplication app,
                           GraalScriptRuntime.ModuleSourceProvider sourceProvider,
                           HotReloadWatcher watcher) {

        this.runtime = new GraalScriptRuntime();
        this.runtime.setModuleSourceProvider(sourceProvider);

        this.jobs = new ScriptJobQueue();
        this.events = new ScriptEventBus();
        this.lifecycle = new ScriptLifecycle(ecs, app, events, runtime);
        this.watcher = watcher;
    }

    public GraalScriptRuntime runtime() { return runtime; }
    public ScriptJobQueue jobs() { return jobs; }
    public ScriptEventBus events() { return events; }
    public ScriptLifecycle lifecycle() { return lifecycle; }

    public void update(float tpf) {
        // 1) run jobs posted from other threads (limit to avoid stalls)
        jobs.drain(10_000);

        // 2) hot reload
        Set<String> changed = (watcher != null) ? watcher.pollChanged() : Set.of();
        if (!changed.isEmpty()) lifecycle.onHotReloadChanged(changed);

        // 3) events then 4) scripts
        events.pump();
        lifecycle.update(tpf);
    }

    public void reset() {
        lifecycle.reset();
        jobs.clear();
        runtime.invalidatePrefix("Scripts/");
    }

    @Override
    public void close() {
        try { if (watcher != null) watcher.close(); } catch (Exception ignored) {}
        try { lifecycle.reset(); } catch (Exception ignored) {}
        try { events.clearAll(); } catch (Exception ignored) {}
        jobs.clear();
        runtime.close();
    }
}