package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.EntityScriptAPI;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ScriptSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(ScriptSystem.class);

    private final EcsWorld ecs;
    private final boolean hotReload;
    private final float cooldownSec;
    private final Path watchRoot;

    private SimpleApplication app;
    private ScriptEventBus bus;   // optional
    private ScriptRuntime runtime;

    private HotReloadWatcher watcher;
    private float cooldown = 0f;

    public ScriptSystem(EcsWorld ecs, boolean hotReload, float cooldownSec, Path watchRoot) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.hotReload = hotReload;
        this.cooldownSec = (cooldownSec <= 0f) ? 0.25f : cooldownSec;
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
    }

    private static void callOptional(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember(member)) return;

        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) {
            throw new IllegalStateException("Member exists but not executable: " + member);
        }
        fn.execute(args);
    }

    private static void callRequired(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) throw new IllegalStateException("JS instance is null");
        if (!obj.hasMember(member)) throw new IllegalStateException("JS instance missing required method: " + member);

        Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) {
            throw new IllegalStateException("Required method not executable: " + member);
        }
        fn.execute(args);
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    @Override
    public void onStart(SystemContext ctx) {
        this.app = Objects.requireNonNull(ctx.app(), "ctx.app");
        this.bus = ctx.events();
        this.runtime = Objects.requireNonNull(ctx.runtime(), "ScriptSystem requires ScriptRuntime (ctx.runtime() is null)");

        if (hotReload) {
            this.watcher = new HotReloadWatcher(watchRoot);
            log.info("ScriptSystem hotReload enabled (root={}, cooldown={}s)", watchRoot.toAbsolutePath(), cooldownSec);
        } else {
            this.watcher = null;
            log.info("ScriptSystem hotReload disabled");
        }

        this.cooldown = 0f;
        log.info("ScriptSystem started");
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        final ScriptRuntime rt = runtime;
        if (rt == null) return;

        if (hotReload && watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f) {
                final Set<String> changed = watcher.pollChanged();
                if (changed != null && !changed.isEmpty()) {
                    cooldown = cooldownSec;

                    final int removed = rt.invalidateManyWithReason(changed, "hotReload");
                    log.debug("HotReload: changed={}, removedFromCache={}", changed.size(), removed);

                    if (bus != null) bus.emit("hotreload:changed", changed);
                } else {
                    cooldown = 0f;
                }
            }
        }

        final Map<Integer, ScriptComponent> scripts = ecs.components().view(ScriptComponent.class);
        if (scripts.isEmpty()) return;

        for (var e : scripts.entrySet()) {
            final int entityId = e.getKey();
            final ScriptComponent sc = e.getValue();
            if (sc == null || sc.assetPath == null) continue;

            ensureStarted(entityId, sc);
            callRequired(sc.instance, "update", tpf);
        }
    }

    @Override
    public void onStop(SystemContext ctx) {
        try {
            final var scripts = ecs.components().view(ScriptComponent.class);
            for (var e : scripts.entrySet()) {
                final ScriptComponent sc = e.getValue();
                if (sc == null || sc.instance == null) continue;

                try {
                    callOptional(sc.instance, "destroy");
                } finally {
                    sc.instance = null;
                    sc.moduleVersion = 0L;
                }
            }
        } finally {
            if (watcher != null) {
                watcher.close();
                watcher = null;
            }
            app = null;
            bus = null;
            runtime = null;
        }

        log.info("ScriptSystem stopped");
    }

    private void ensureStarted(int entityId, ScriptComponent sc) {
        final String moduleId = (sc.moduleId != null && !sc.moduleId.isBlank())
                ? sc.moduleId
                : normalize(sc.assetPath);

        final long v = runtime.moduleVersion(moduleId);
        final boolean needsStart = (sc.instance == null) || (sc.moduleVersion != v);
        if (!needsStart) return;

        if (sc.instance != null) {
            callOptional(sc.instance, "destroy");
            sc.instance = null;
        }

        final Value exports = Objects.requireNonNull(runtime.require(moduleId), "require returned null: " + moduleId);
        if (!exports.hasMember("create")) {
            throw new IllegalStateException("Entity script must export create(api): " + moduleId);
        }

        final Value create = exports.getMember("create");
        if (create == null || !create.canExecute()) {
            throw new IllegalStateException("Entity script export 'create' is not executable: " + moduleId);
        }

        final EntityScriptAPI api = new EntityScriptAPI(entityId, ecs, app, bus);
        final Value instance = create.execute(api);

        if (instance == null || instance.isNull()) {
            throw new IllegalStateException("Entity script create(api) returned null instance: " + moduleId);
        }

        sc.instance = instance;
        sc.moduleVersion = v;
    }
}