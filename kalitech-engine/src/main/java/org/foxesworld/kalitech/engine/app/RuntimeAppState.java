// FILE: RuntimeAppState.java
package org.foxesworld.kalitech.engine.app;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.WorldBuilder;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class RuntimeAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(RuntimeAppState.class);

    private final String mainAssetPath;
    private final Path watchRoot;
    private final float reloadCooldownSec;

    private final EcsWorld ecs;
    private final ScriptEventBus bus;

    private ScriptRuntime runtime;
    private HotReloadWatcher watcher;
    private SimpleApplication sa;

    private SystemRegistry registry;
    private WorldAppState worldState;
    private WorldBuilder worldBuilder;
    private BulletAppState bullet;

    private float cooldown = 0f;
    private boolean dirty = true;

    private EngineApiImpl engineApi;
    private PhysicsSpace space;

    public RuntimeAppState(String mainAssetPath, Path watchRoot, float reloadCooldownSec, EcsWorld ecs, ScriptEventBus bus) {
        this.mainAssetPath = Objects.requireNonNull(mainAssetPath, "mainAssetPath");
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
        this.reloadCooldownSec = reloadCooldownSec <= 0 ? 0.25f : reloadCooldownSec;

        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /**
     * New contract: entry module may expose an app instance or a factory.
     *
     * Supported:
     * - module.create() -> app
     * - module.app -> app
     * - fallback: module itself is the app
     */
    private static Value instantiateApp(Value mainModule) {
        if (mainModule == null || mainModule.isNull()) return mainModule;

        // module.create() -> app
        try {
            if (mainModule.hasMember("create")) {
                Value c = mainModule.getMember("create");
                if (c != null && c.canExecute()) {
                    Value app = c.execute();
                    if (app != null && !app.isNull()) return app;
                }
            }
        } catch (Throwable ignored) {
        }

        // module.app -> app
        try {
            if (mainModule.hasMember("app")) {
                Value app = mainModule.getMember("app");
                if (app != null && !app.isNull()) return app;
            }
        } catch (Throwable ignored) {
        }

        return mainModule;
    }

    /**
     * Resolves a world descriptor from app/module.
     *
     * Preferred:
     * - app.getWorld(ctx) -> worldDesc
     * - app.world -> worldDesc
     * Backwards compatible:
     * - module.world / module.exports.world
     */
    private static Value resolveWorldDescriptor(Value mainModule, Value appObj, SystemContext ctx) {
        // app.getWorld(ctx)
        try {
            if (appObj != null && !appObj.isNull() && appObj.hasMember("getWorld")) {
                Value f = appObj.getMember("getWorld");
                if (f != null && f.canExecute()) {
                    Value wd = f.execute(ctx);
                    if (wd != null && !wd.isNull()) return wd;
                }
            }
        } catch (Throwable ignored) {
        }

        // app.world
        try {
            if (appObj != null && !appObj.isNull() && appObj.hasMember("world")) {
                Value wd = appObj.getMember("world");
                if (wd != null && !wd.isNull()) return wd;
            }
        } catch (Throwable ignored) {
        }

        // legacy module.exports.world
        return extractWorldDescriptor(mainModule);
    }

    private static void callIfExists(Value module, String fn, Object... args) {
        if (module == null || module.isNull()) return;
        if (!module.hasMember(fn)) return;
        Value f = module.getMember(fn);
        if (f == null || !f.canExecute()) return;
        try {
            f.execute(args);
        } catch (Throwable t) {
            log.error("JS hook '{}' failed", fn, t);
            throw t;
        }
    }

    private static Value extractWorldDescriptor(Value moduleOrExports) {
        if (moduleOrExports == null || moduleOrExports.isNull()) return null;

        if (moduleOrExports.hasMember("world")) return moduleOrExports.getMember("world");

        if (moduleOrExports.hasMember("exports")) {
            Value ex = moduleOrExports.getMember("exports");
            if (ex != null && !ex.isNull() && ex.hasMember("world")) return ex.getMember("world");
        }
        return null;
    }

    @Override
    protected void initialize(Application app) {
        this.sa = (SimpleApplication) app;

        // --- PHYSICS: one per RuntimeAppState (stable across world rebuilds) ---
        bullet = new BulletAppState();
        bullet.setDebugEnabled(Boolean.parseBoolean(System.getProperty("log.level", "false")));
        app.getStateManager().attach(bullet);

        space = bullet.getPhysicsSpace();
        space.setGravity(new Vector3f(0, -9.81f, 0));

        // --- Shared runtime ---
        runtime = new ScriptRuntime();
        runtime.setModuleStreamProvider(moduleId -> {
            String id = moduleId;
            if (!id.endsWith(".js")) id += ".js";

            AssetManager am = app.getAssetManager();
            try {
                return am.locateAsset(new AssetKey<>(id)).openStream();
            } catch (Exception e) {
                return null;
            }
        });

        // stable API for JS
        engineApi = new EngineApiImpl(this);
        engineApi.__setPhysicsSpace(space);

        runtime.initBuiltIns(engineApi);
        runtime.setModuleSourceProvider(path -> sa.getAssetManager().loadAsset(new AssetKey<>(path)));

        // dev hot reload watcher
        watcher = new HotReloadWatcher(watchRoot);

        // providers registry (ServiceLoader)
        registry = new SystemRegistry();

        // world runner (keeps SystemContext)
        worldState = new WorldAppState(this);
        getStateManager().attach(worldState);

        // builder uses registry
        worldBuilder = new WorldBuilder(sa, registry);

        log.info("RuntimeAppState started: main='{}', watchRoot={}", mainAssetPath, watchRoot.toAbsolutePath());
        dirty = true;
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        engineApi.__updateTime(tpf);

        // пока WorldAppState не готов — просто ждём
        if (worldState == null || worldState.getContextForJs() == null) return;

        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null) {
            Set<String> changed = watcher.pollChanged();
            if (!changed.isEmpty()) {
                cooldown = reloadCooldownSec;

                // 1) Invalidate changed modules so require() reloads them.
                if (runtime != null) {
                    runtime.invalidateMany(changed);
                }

                // 2) Notify scripts (optional)
                bus.emit("hotreload:changed", changed);

                // 3) Script-driven rule: ANY module change can affect the app/world.
                dirty = true;
            }
        }

        if (dirty) {
            dirty = false;
            reloadMainAndRebuildWorld();
        }

        engineApi.__endFrameInput();
    }

    private void reloadMainAndRebuildWorld() {
        SystemContext ctx = (worldState != null) ? worldState.getContextForJs() : null;
        if (ctx == null) {
            dirty = true;
            return;
        }

        try {
            runtime.invalidate(mainAssetPath);
            Value main = runtime.require(mainAssetPath);

            Value appObj = instantiateApp(main);
            Value worldDesc = resolveWorldDescriptor(main, appObj, ctx);
            if (worldDesc == null || worldDesc.isNull()) {
                log.error(
                        "Entry '{}' provides no world descriptor. Implement one of: create()->{getWorld(ctx)}, app.world, or legacy exports.world",
                        mainAssetPath
                );
                return;
            }

            try { engineApi.__physicsClearWorld(); } catch (Throwable ignored) {}

            ecs.reset();

            KWorld newWorld = worldBuilder.buildFromWorldDesc(ctx, worldDesc);
            worldState.setWorld(newWorld);

            // Scripts decide what to spawn/configure
            callIfExists(appObj, "start", ctx);
            callIfExists(main, "bootstrap", ctx); // legacy

            // signal
            try {
                bus.emit("world:ready", worldDesc);
            } catch (Throwable ignored) {
            }

            log.info("World rebuilt from {}", mainAssetPath);

        } catch (Exception e) {
            log.error("Failed to rebuild world from {}", mainAssetPath, e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        if (worldState != null) {
            try {
                worldState.setEnabled(false);
            } catch (Exception ignored) {
            }
            if (bullet != null) {
                try {
                    getStateManager().detach(bullet);
                } catch (Exception ignored) {
                }
                bullet = null;
            }
            engineApi.__setPhysicsSpace(null);
            try { getStateManager().detach(worldState); } catch (Exception ignored) {}
            worldState = null;
        }

        if (watcher != null) {
            try { watcher.close(); } catch (Exception ignored) {}
            watcher = null;
        }

        if (runtime != null) {
            try { runtime.close(); } catch (Exception ignored) {}
            runtime = null;
        }

        registry = null;
        worldBuilder = null;

        log.info("RuntimeAppState stopped");
    }

    public EngineApiImpl getEngineApi() {
        return engineApi;
    }

    public PhysicsSpace getSpace() {
        return space;
    }

    public ScriptEventBus getBus() {
        return bus;
    }

    public EcsWorld getEcs() {
        return ecs;
    }

    public SimpleApplication getSa() {
        return sa;
    }

    public ScriptRuntime getRuntime() {
        return runtime;
    }

    public BulletAppState getBullet() {
        return bullet;
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}