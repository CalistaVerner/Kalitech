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
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime host: scripts, hot-reload, engine API, ECS, bus, optional physics.
 * Does NOT manage WorldAppState or any world lifecycle.
 */
public final class RuntimeAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(RuntimeAppState.class);

    private final String entry;
    private final Path watchRoot;

    private SimpleApplication app;
    private ScriptRuntime runtime;
    private HotReloadWatcher watcher;

    private final ScriptEventBus bus;
    private final EcsWorld ecs;

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
        this.runtime = new ScriptRuntime();
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

    private static SimpleApplication coerceSimpleApp(Application app) {
        return (app instanceof SimpleApplication sa) ? sa : null;
    }

    @Override
    protected void initialize(Application app) {
        this.app = coerceSimpleApp(app);
        if (this.app == null) {
            throw new IllegalStateException("RuntimeAppState requires SimpleApplication");
        }

        // --- Physics (optional) ---
        bullet = new BulletAppState();
        bullet.setDebugEnabled(Boolean.parseBoolean(System.getProperty("physicsDebug", "false").toLowerCase()));

        try {
            this.app.getStateManager().attach(bullet);
        } catch (Throwable t) {
            log.warn("[Runtime] BulletAppState not attached (optional): {}", t.toString());
            bullet = null;
        }

        physicsSpace = (bullet != null) ? bullet.getPhysicsSpace() : null;
        if (physicsSpace != null) {
            physicsSpace.setMaxSubSteps(8);
            physicsSpace.setAccuracy(1f / 60f);
            physicsSpace.setGravity(new Vector3f(0f, -9.81f, 0f));
        }

        // --- Engine API MUST exist before builtins init ---
        engineApi = new EngineApiImpl(this);
        engineApi.__setPhysicsSpace(physicsSpace);

        // --- Script runtime (shared base for app + potential worlds) ---
        //runtime = new ScriptRuntime();

        // CRITICAL: providers MUST be set BEFORE any require() or builtins init
        runtime.setModuleStreamProvider(this::openJsModuleStream);

        // Builtins must be initialized AFTER engineApi exists
        runtime.initBuiltIns(engineApi);

        // --- Hot reload watcher ---
        watcher = new HotReloadWatcher(watchRoot);

        // --- App-only context ---
        // WorldTime is world-only => null here.
        appCtx = new SystemContext(
                this.app,
                engineApi,
                ecs,
                bus,
                physicsSpace,
                null,      // WorldTime (world-only)
                runtime,
                null,      // runtimeProvider
                null,      // runtimePolicy (default inside SystemContext)
                null,      // scheduler
                null,      // mainQueue
                null,      // perfProvider
                engineApi.getLog()
        );

        dirty = true;
        cooldown = 0f;

        log.info("[Runtime] started entry={} watchRoot={}", entry, watchRoot.toAbsolutePath());
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Keep time/fps updated even without world
        try {
            if (engineApi != null) engineApi.__updateTime(tpf);
        } catch (Throwable ignored) {
        }

        // Hot reload
        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null && runtime != null) {
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
            if (engineApi != null) engineApi.input().endFrame();
        } catch (Throwable ignored) {
        }
    }

    private void restartApp() {
        final ScriptRuntime rt = this.runtime;
        final SystemContext ctx = this.appCtx;

        if (rt == null || ctx == null) return;

        try {
            // Optional: hard-reset physics on reload to avoid ghost bodies
            try {
                if (engineApi != null) engineApi.physics().__clearAll();
            } catch (Throwable ignored) {
            }

            try {
                rt.invalidate(entry);
            } catch (Throwable ignored) {
            }

            Value main = rt.require(entry);
            Value appObj = resolveApp(main);

            if (appObj != null && !appObj.isNull() && appObj.hasMember("start")) {
                Value start = appObj.getMember("start");
                if (start != null && start.canExecute()) {
                    start.execute(ctx);
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
            if (id.isEmpty()) return null;

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
            if (this.app != null && bullet != null) this.app.getStateManager().detach(bullet);
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
        this.app = null;

        log.info("[Runtime] stopped");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    // ---------------------------------------------------------------------
    // Getters used by EngineApiImpl / WorldApiImpl (lazy attach WorldAppState)
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