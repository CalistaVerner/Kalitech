// FILE: ScriptSubsystem.java  (NEW)
package org.foxesworld.kalitech.engine.script;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.script.lifecycle.ScriptLifecycle;

import java.io.Closeable;
import java.util.Set;

/**
 * ScriptSubsystem — a single, centralized entrypoint for the scripting pipeline.
 *
 * <p>Order per-frame:
 * <ol>
 *   <li>Poll file changes</li>
 *   <li>Invalidate runtime cache + selective entity restart</li>
 *   <li>Pump queued events</li>
 *   <li>Update script instances</li>
 * </ol>
 *
 * <p>This class is intentionally small: orchestration only.
 */
public final class ScriptSubsystem implements Closeable {

    private final GraalScriptRuntime runtime;
    private final ScriptEventBus events;
    private final ScriptLifecycle lifecycle;
    private final HotReloadWatcher watcher;

    public ScriptSubsystem(EcsWorld ecs,
                           SimpleApplication app,
                           GraalScriptRuntime.ModuleSourceProvider sourceProvider,
                           HotReloadWatcher watcher) {
        this.runtime = new GraalScriptRuntime();
        this.runtime.setModuleSourceProvider(sourceProvider);

        this.events = new ScriptEventBus();
        this.lifecycle = new ScriptLifecycle(ecs, app, events, runtime);
        this.watcher = watcher;
    }

    public GraalScriptRuntime runtime() { return runtime; }
    public ScriptEventBus events() { return events; }
    public ScriptLifecycle lifecycle() { return lifecycle; }

    /** Central update call (call once per frame in the main thread). */
    public void update(float tpf) {
        Set<String> changed = (watcher != null) ? watcher.pollChanged() : Set.of();
        if (!changed.isEmpty()) {
            lifecycle.onHotReloadChanged(changed);
        }
        events.pump();
        lifecycle.update(tpf);
    }

    /** For world rebuilds. */
    public void reset() {
        lifecycle.reset();
        events.clearAll();
        // optional convenience: clear all cached scripts under Scripts/
        runtime.invalidatePrefix("Scripts/");
    }

    @Override
    public void close() {
        try { if (watcher != null) watcher.close(); } catch (Exception ignored) {}
        try { lifecycle.reset(); } catch (Exception ignored) {}
        try { events.clearAll(); } catch (Exception ignored) {}
        runtime.close();
    }
}