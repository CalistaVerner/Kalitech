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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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
    private String lastHash = null;

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
     * <p>
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
            // ignore and fall back
        }

        // module.app -> app
        try {
            if (mainModule.hasMember("app")) {
                Value app = mainModule.getMember("app");
                if (app != null && !app.isNull()) return app;
            }
        } catch (Throwable ignored) {
            // ignore and fall back
        }

        return mainModule;
    }

    /**
     * Resolves a world descriptor from app/module.
     * <p>
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
            // ignore and fall back
        }

        // app.world
        try {
            if (appObj != null && !appObj.isNull() && appObj.hasMember("world")) {
                Value wd = appObj.getMember("world");
                if (wd != null && !wd.isNull()) return wd;
            }
        } catch (Throwable ignored) {
            // ignore and fall back
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
            // keep reload resilient: do not crash Java world loop due to JS lifecycle bug
            log.error("JS hook '{}' failed", fn, t);
            throw t;
        }
    }

    private void applyMode(Value worldDesc) {
        try {
            if (!worldDesc.hasMember("mode")) return;
            Value m = worldDesc.getMember("mode");
            if (m == null || m.isNull() || !m.isString()) return;

            String mode = m.asString();
            boolean editor = "editor".equalsIgnoreCase(mode);

            // EngineApiImpl supports __setEditorMode
            engineApi.__setEditorMode(editor);
        } catch (Throwable t) {
            log.warn("applyMode skipped: {}", t.toString());
        }
    }

    private void applyEntitiesFromWorldDesc(Value worldDesc) {
        if (worldDesc == null || worldDesc.isNull()) return;
        if (!worldDesc.hasMember("entities")) return;

        Value arr = worldDesc.getMember("entities");
        if (arr == null || arr.isNull() || !arr.hasArrayElements()) return;

        long n = arr.getArraySize();
        for (long i = 0; i < n; i++) {
            Value e = arr.getArrayElement(i);
            if (e == null || e.isNull()) continue;

            try {
                engineApi.world().spawn(e); // e already has {name,prefab}
            } catch (Exception ex) {
                log.error("Failed to spawn entity from descriptor index={}", i, ex);
            }
        }
    }

    /** Small payload class for events (JS will see fields). */
    public static final class EntitySpawned {
        public final int id;
        public final String name;
        public final String prefab;

        public EntitySpawned(int id, String name, String prefab) {
            this.id = id;
            this.name = name;
            this.prefab = prefab;
        }
    }

    private static Value extractWorldDescriptor(Value moduleOrExports) {
        if (moduleOrExports == null || moduleOrExports.isNull()) return null;

        // CASE 1: exports object directly
        if (moduleOrExports.hasMember("world")) return moduleOrExports.getMember("world");

        // CASE 2: legacy: object with exports field
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
        // give PhysicsSpace to EngineApiImpl (so engine.physics() uses this space)
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

                // 2) Notify scripts (optional): allow JS to react.
                bus.emit("hotreload:changed", changed);

                // 3) Rebuild world ONLY if main descriptor changed.
                if (changed.contains(mainAssetPath.replace('\\', '/'))) {
                    dirty = true;
                }
            }
        }

        if (dirty) {
            dirty = false;
            reloadMainAndRebuildWorld();
        }

        engineApi.__endFrameInput();
    }

    private void reloadMainAndRebuildWorld() {
        // ВАЖНО: WorldAppState может быть ещё не initialized -> ctx == null
        SystemContext ctx = (worldState != null) ? worldState.getContextForJs() : null;
        if (ctx == null) {
            dirty = true;
            return;
        }

        SimpleApplication app = (SimpleApplication) getApplication();
        try {
            // main.js source (invalidate asset cache on dirty rebuild)
            app.getAssetManager().deleteFromCache(new AssetKey<>(mainAssetPath));
            String code = app.getAssetManager().loadAsset(new AssetKey<>(mainAssetPath));

            String hash = sha1(code);
            if (hash.equals(lastHash)) {
                // main descriptor not changed; keep current world.
                return;
            }
            lastHash = hash;

            // Ensure main module is re-evaluated on rebuild (even if it was required before)
            runtime.invalidate(mainAssetPath);
            Value main = runtime.require(mainAssetPath);

            // --- NEW CONTRACT (no magic keys): entry provides an "app" which yields a world descriptor ---
            // Preferred:
            //   module.create() -> app
            //   app.getWorld(ctx) -> worldDesc
            //   app.world -> worldDesc
            // Backwards compatible:
            //   module.world / module.exports.world
            Value appObj = instantiateApp(main);
            Value worldDesc = resolveWorldDescriptor(main, appObj, ctx);
            if (worldDesc == null || worldDesc.isNull()) {
                log.error(
                        "Entry '{}' provides no world descriptor. Implement one of: create()->{getWorld(ctx)}, app.world, or legacy exports.world",
                        mainAssetPath
                );
                return;
            }

            // 0) editor-mode by descriptor (optional, soft)
            applyMode(worldDesc);

            // Clear physics objects before hard ECS reset / world rebuild
            try { engineApi.__physicsClearWorld(); } catch (Throwable ignored) {}

            // 1) HARD reset ECS so rebuild does not accumulate entities/components
            ecs.reset();

            // 2) build world systems
            KWorld newWorld = worldBuilder.buildFromWorldDesc(ctx, worldDesc);
            worldState.setWorld(newWorld);

            // 3) declarative entities spawn BEFORE lifecycle hooks
            applyEntitiesFromWorldDesc(worldDesc);

            // 4) optional lifecycle AFTER world created + entities spawned
            // New lifecycle first
            callIfExists(appObj, "start", ctx);
            // Legacy hook (kept for compatibility)
            callIfExists(main, "bootstrap", ctx);

            log.info("World rebuilt from {}", mainAssetPath);

        } catch (Exception e) {
            log.error("Failed to rebuild world from {}", mainAssetPath, e);
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    @Override
    protected void cleanup(Application app) {
        // 1) STOP WORLD FIRST (before closing runtime)
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

        // 2) stop watcher
        if (watcher != null) {
            try { watcher.close(); } catch (Exception ignored) {}
            watcher = null;
        }

        // 3) close runtime LAST
        if (runtime != null) {
            try { runtime.close(); } catch (Exception ignored) {}
            runtime = null;
        }

        registry = null;
        worldBuilder = null;
        lastHash = null;

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
        return bullet; }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}