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
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class RuntimeAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(RuntimeAppState.class);

    private final String entry;
    private final Path watchRoot;

    private SimpleApplication app;
    private ScriptRuntime runtime;
    private HotReloadWatcher watcher;

    private ScriptEventBus bus;
    private EcsWorld ecs;

    private EngineApiImpl engineApi;

    // Optional physics subsystem (safe even if world not used)
    private BulletAppState bullet;
    private PhysicsSpace physicsSpace;

    // App-only script context (world optional)
    private SystemContext appCtx;

    private boolean dirty = true;
    private float reloadCooldown = 0.25f;
    private float cooldown = 0f;

    public RuntimeAppState(String entry, Path watchRoot, EcsWorld ecs, ScriptEventBus bus) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    private static Value resolveApp(Value module) {
        if (module == null || module.isNull()) return null;

        try {
            if (module.hasMember("create")) {
                Value c = module.getMember("create");
                if (c != null && c.canExecute()) {
                    Value app = c.execute();
                    if (app != null && !app.isNull()) return app;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (module.hasMember("app")) {
                Value app = module.getMember("app");
                if (app != null && !app.isNull()) return app;
            }
        } catch (Throwable ignored) {
        }

        return module;
    }

    private static String normalizeJsModuleId(String moduleId) {
        String id = (moduleId == null) ? "" : moduleId.trim();
        if (id.isEmpty()) return "";
        id = id.replace('\\', '/');
        while (id.startsWith("./")) id = id.substring(2);
        while (id.startsWith("/")) id = id.substring(1);
        if (!id.endsWith(".js")) id += ".js";
        return id;
    }

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;

        // --- Physics (optional) ---
        bullet = new BulletAppState();
        app.getStateManager().attach(bullet);
        physicsSpace = bullet.getPhysicsSpace();
        if (physicsSpace != null) {
            physicsSpace.setGravity(new Vector3f(0f, -9.81f, 0f));
        }

        // --- Engine API MUST exist before builtins init ---
        engineApi = new EngineApiImpl(this);
        engineApi.__setPhysicsSpace(physicsSpace);

        // --- Script runtime (shared base for app + world) ---
        runtime = new ScriptRuntime();

        // CRITICAL: providers for require()/assets MUST be set BEFORE any require() or builtins init
        runtime.setModuleStreamProvider(this::openJsModuleStream);
        // runtime.setModuleSourceProvider(this::loadTextAssetOrNull); // optional if you use it

        // Builtins must be initialized AFTER engineApi exists (bootstrap needs engine to attach)
        runtime.initBuiltIns(engineApi);

        // --- Hot reload watcher ---
        watcher = new HotReloadWatcher(watchRoot);

        // --- Optional world subsystem (SERVICE) ---
        // IMPORTANT: WorldAppState must see THIS RuntimeAppState, to reuse runtime with providers.
        try {
            app.getStateManager().attach(new WorldAppState(this));
        } catch (Throwable t) {
            log.warn("[Runtime] WorldAppState not attached (optional): {}", t.toString());
        }

        // --- App-only context ---
        appCtx = new SystemContext(
                this.app,
                engineApi,
                ecs,
                bus,
                physicsSpace,
                runtime,
                null,
                null,
                null,
                null,
                null,
                engineApi.getLog()
        );

        dirty = true;
        log.info("[Runtime] started entry={} watchRoot={}", entry, watchRoot.toAbsolutePath());
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // keep time/fps updated even without world
        try {
            engineApi.__updateTime(tpf);
        } catch (Throwable ignored) {
        }

        // hot reload
        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null) {
            Set<String> changed = watcher.pollChanged();
            if (changed != null && !changed.isEmpty()) {
                try {
                    runtime.invalidateMany(changed);
                } catch (Throwable ignored) {
                }
                dirty = true;
                cooldown = reloadCooldown;

                try {
                    bus.emit("hotreload:changed", changed);
                } catch (Throwable ignored) {
                }
            }
        }

        if (dirty) {
            dirty = false;
            restartApp();
        }

        try {
            engineApi.input().endFrame();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------------
    // Asset-backed module loading
    // ---------------------------------------------------------------------

    private void restartApp() {
        try {
            try {
                engineApi.physics().__clearAll();
            } catch (Throwable ignored) {
            }

            runtime.invalidate(entry);
            Value main = runtime.require(entry);
            Value appObj = resolveApp(main);

            if (appObj != null && !appObj.isNull() && appObj.hasMember("start")) {
                Value start = appObj.getMember("start");
                if (start != null && start.canExecute()) {
                    start.execute(appCtx);
                }
            }

            try {
                bus.emit("app:started", null);
            } catch (Throwable ignored) {
            }
            log.info("[Runtime] app started");
        } catch (Throwable t) {
            log.error("[Runtime] app start failed", t);
        }
    }

    private InputStream openJsModuleStream(String moduleId) {
        try {
            String id = normalizeJsModuleId(moduleId);
            AssetManager am = (app != null) ? app.getAssetManager() : null;
            if (am == null) return null;
            var ai = am.locateAsset(new AssetKey<>(id));
            return (ai != null) ? ai.openStream() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private Object loadTextAssetOrNull(String path) {
        try {
            AssetManager am = (app != null) ? app.getAssetManager() : null;
            if (am == null) return null;
            return am.loadAsset(new AssetKey<>(path));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected void cleanup(Application app) {
        try {
            if (bullet != null) app.getStateManager().detach(bullet);
        } catch (Throwable ignored) {
        }
        bullet = null;
        physicsSpace = null;

        try {
            if (watcher != null) watcher.close();
        } catch (Throwable ignored) {
        }
        watcher = null;

        try {
            if (runtime != null) runtime.close();
        } catch (Throwable ignored) {
        }
        runtime = null;

        appCtx = null;
        engineApi = null;

        log.info("[Runtime] stopped");
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}

    // ---------------------------------------------------------------------
    // Getters used by EngineApiImpl / WorldAppState
    // ---------------------------------------------------------------------

    public SimpleApplication getSa() {
        return app;
    }

    public AssetManager getAssets() {
        return (app != null) ? app.getAssetManager() : null;
    }

    public ScriptEventBus getBus() {
        return bus;
    }

    public EcsWorld getEcs() {
        return ecs;
    }

    public ScriptRuntime getRuntime() {
        return runtime;
    }

    public BulletAppState getBullet() {
        return bullet;
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public EngineApiImpl getEngineApi() {
        return engineApi;
    }

    public SystemContext getAppContext() {
        return appCtx;
    }
}