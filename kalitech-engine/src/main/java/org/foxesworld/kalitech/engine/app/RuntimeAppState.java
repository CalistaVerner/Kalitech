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
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.ScriptEntryPoint;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime host: scripts, hot-reload, engine API, ECS, bus, optional physics.
 * Does NOT manage WorldAppState or any world lifecycle.
 */
public final class RuntimeAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(RuntimeAppState.class);

    private final ScriptEntryPoint entryPoint;
    private final Path projectOwnedRoot;

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
    private boolean appQuarantined;
    private String appQuarantineReason;
    private float reloadCooldown = 0.25f;
    private float cooldown = 0f;

    public RuntimeAppState(
            ScriptEntryPoint entryPoint,
            Path projectOwnedRoot,
            EcsWorld ecs,
            ScriptEventBus bus
    ) {
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
        this.projectOwnedRoot = Objects.requireNonNull(projectOwnedRoot, "projectOwnedRoot");
        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.runtime = new ScriptRuntime();
    }

    private static LuaValueRef resolveApp(LuaValueRef module) {
        if (module == null || module.isNull()) return null;

        if (module.hasMember("create")) {
            LuaValueRef create = module.getMember("create");
            if (create != null && create.canExecute()) {
                LuaValueRef app = module.invokeMemberLifecycle("app.create", "create");
                if (app != null && !app.isNull()) return app;
            }
        }

        if (module.hasMember("app")) {
            LuaValueRef app = module.getMember("app");
            if (app != null && !app.isNull()) return app;
        }

        return module;
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

        // Install the project namespace before EngineApiImpl mounts module JARs.
        // RuntimeLuaBridge will chain on top of this provider instead of being
        // overwritten by a later application provider.
        runtime.setModuleStreamProvider(this::openProjectModule);

        // --- Engine API MUST exist before builtins init ---
        engineApi = new EngineApiImpl(this);
        engineApi.__setPhysicsSpace(physicsSpace);

        // Builtins must be initialized AFTER engineApi exists
        runtime.initBuiltIns(engineApi);

        // --- Hot reload watcher ---
        watcher = new HotReloadWatcher(projectOwnedRoot);

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
        appQuarantined = false;
        appQuarantineReason = null;
        cooldown = 0f;

        log.info("[Runtime] started namespace={} entry={} projectOwnedRoot={}",
                entryPoint.namespace(), entryPoint.moduleId(), projectOwnedRoot.toAbsolutePath());
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Keep time/fps updated even without world
        try {
            if (engineApi != null) engineApi.__updateTime(tpf);
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
        }

        // Hot reload
        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null && runtime != null) {
            Set<String> changed = watcher.pollChanged();
            if (changed != null && !changed.isEmpty()) {
                try {
                    HashSet<String> changedModules = new HashSet<>();
                    for (String changedProjectPath : changed) {
                        String moduleId = entryPoint.moduleIdForProjectPath(changedProjectPath);
                        if (moduleId != null) changedModules.add(moduleId);
                    }
                    runtime.invalidateManyWithReason(changedModules, "hot reload");
                } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
                }

                dirty = true;
                appQuarantined = false;
                appQuarantineReason = null;
                cooldown = reloadCooldown;

                try {
                    bus.emit("hotreload:changed", changed);
                } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
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
            ScriptFailureBoundary.rethrowIfFatal(ignored);
        }
    }

    private void restartApp() {
        final ScriptRuntime rt = this.runtime;
        final SystemContext ctx = this.appCtx;
        final String entry = this.entryPoint.moduleId();

        if (rt == null || ctx == null) return;

        try {
            // Optional: hard-reset physics on reload to avoid ghost bodies
            try {
                if (engineApi != null) engineApi.physics().__clearAll();
            } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            }

            try {
                rt.invalidate(entry);
            } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            }

            LuaValueRef main = rt.require(entry);
            LuaValueRef appObj = resolveApp(main);

            if (appObj != null && !appObj.isNull() && appObj.hasMember("start")) {
                LuaValueRef start = appObj.getMember("start");
                if (start != null && start.canExecute()) {
                    start.executeLifecycle("app.start", ctx);
                }
            }

            try {
                bus.emit("app:started", null);
            } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            }

            appQuarantined = false;
            appQuarantineReason = null;
            log.info("[Runtime] app started");
        } catch (Throwable failure) {
            ScriptFailureBoundary.rethrowIfFatal(failure);
            appQuarantined = true;
            appQuarantineReason = ScriptFailureBoundary.summary(failure);
            log.error("[Runtime] application Lua script quarantined; engine remains active; "
                    + "recovery=hot reload", failure);
        }
    }

    /**
     * Opens only modules owned by this project's virtual application namespace.
     * Physical project-owned paths are never accepted as module ids.
     */
    public InputStream openProjectModule(String moduleId) {
        try {
            String projectPath = entryPoint.projectOwnedPath(moduleId);
            if (projectPath == null) return null;

            AssetManager am = (app != null) ? app.getAssetManager() : null;
            if (am == null) return null;

            var ai = am.locateAsset(new AssetKey<>(projectPath));
            return (ai != null) ? ai.openStream() : null;
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
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
            ScriptFailureBoundary.rethrowIfFatal(ignored);
            return null;
        }
    }

    @Override
    protected void cleanup(Application app) {
        try {
            if (this.app != null && bullet != null) this.app.getStateManager().detach(bullet);
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
        }
        bullet = null;
        physicsSpace = null;

        try {
            if (watcher != null) watcher.close();
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
        }
        watcher = null;

        try {
            if (runtime != null) runtime.close();
        } catch (Throwable ignored) {
            ScriptFailureBoundary.rethrowIfFatal(ignored);
        }
        runtime = null;

        appCtx = null;
        appQuarantined = false;
        appQuarantineReason = null;
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

    public ScriptEntryPoint getEntryPoint() {
        return entryPoint;
    }

    public Path getProjectOwnedRoot() {
        return projectOwnedRoot;
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

    public boolean isAppQuarantined() {
        return appQuarantined;
    }

    public String getAppQuarantineReason() {
        return appQuarantineReason;
    }
}