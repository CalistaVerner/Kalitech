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
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.WorldBuilder;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;
import java.util.Objects;

public final class RuntimeAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(RuntimeAppState.class);

    private final String mainAssetPath;
    private final Path watchRoot;
    private final float reloadCooldownSec;

    private final EcsWorld ecs;
    private final ScriptEventBus bus;

    private GraalScriptRuntime runtime;
    private HotReloadWatcher watcher;

    private SystemRegistry registry;
    private WorldAppState worldState;
    private WorldBuilder worldBuilder;
    private BulletAppState bullet;

    private float cooldown = 0f;
    private boolean dirty = true;
    private String lastHash = null;

    private EngineApiImpl engineApi;

    public RuntimeAppState(String mainAssetPath, Path watchRoot, float reloadCooldownSec, EcsWorld ecs, ScriptEventBus bus) {
        this.mainAssetPath = Objects.requireNonNull(mainAssetPath, "mainAssetPath");
        this.watchRoot = Objects.requireNonNull(watchRoot, "watchRoot");
        this.reloadCooldownSec = reloadCooldownSec <= 0 ? 0.25f : reloadCooldownSec;

        this.ecs = Objects.requireNonNull(ecs, "ecs");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication sa = (SimpleApplication) app;

        // --- PHYSICS: one per RuntimeAppState (stable across world rebuilds) ---
        bullet = new BulletAppState();
        bullet.setDebugEnabled(Boolean.parseBoolean(System.getProperty("log.level", "false")));
        app.getStateManager().attach(bullet);

        PhysicsSpace space = bullet.getPhysicsSpace();
        space.setGravity(new Vector3f(0, -9.81f, 0));

        // 1) shared runtime
        runtime = new GraalScriptRuntime();
        runtime.setModuleStreamProvider(moduleId -> {
            String id = moduleId;
            if (!id.endsWith(".js")) id += ".js";

            AssetManager am = app.getAssetManager();
            try {
                InputStream in = am.locateAsset(new AssetKey<>(id)).openStream();
                return in;
            } catch (Exception e) {
                return null;
            }
        });

        // 4) stable API for JS
        engineApi = new EngineApiImpl(sa, sa.getAssetManager(), bus, ecs);
        // give PhysicsSpace to EngineApiImpl (so engine.physics() uses this space)
        engineApi.__setPhysicsSpace(space);
        runtime.initBuiltIns(engineApi);
        // 2) ЯВНО инициализируем built-ins


        runtime.setModuleSourceProvider(path -> sa.getAssetManager().loadAsset(new AssetKey<>(path)));

        // 2) dev hot reload watcher
        watcher = new HotReloadWatcher(watchRoot);

        // 3) providers registry (ServiceLoader)
        registry = new SystemRegistry();



        // 5) world runner (keeps SystemContext)
        worldState = new WorldAppState(bus, ecs, runtime, engineApi);
        getStateManager().attach(worldState);

        // 6) builder uses registry
        worldBuilder = new WorldBuilder(sa, registry);

        log.info("RuntimeAppState started: main='{}', watchRoot={}", mainAssetPath, watchRoot.toAbsolutePath());
        dirty = true;
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        engineApi.__updateTime(tpf);
        //engineApi.ui().tick();
        // пока WorldAppState не готов — просто ждём
        if (worldState == null || worldState.getContextForJs() == null) return;

        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null) {
            Set<String> changed = watcher.pollChanged();
            if (!changed.isEmpty()) {
                cooldown = reloadCooldownSec;

                // 1) Invalidate changed modules so require() reloads them.
                //    Systems (ScriptSystem / JsWorldSystem) will pick changes by moduleVersion().
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

            Value worldDesc = extractWorldDescriptor(main);
            if (worldDesc == null || worldDesc.isNull()) {
                log.error("main.js has no world descriptor. Expect module.exports.world = {...}");
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

            // 3) declarative entities spawn BEFORE bootstrap
            applyEntitiesFromWorldDesc(worldDesc);

            // 4) optional bootstrap AFTER world created + entities spawned
            callIfExists(main, "bootstrap", ctx);

            log.info("World rebuilt from {}", mainAssetPath);

        } catch (Exception e) {
            log.error("Failed to rebuild world from {}", mainAssetPath, e);
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

    private static void callIfExists(Value module, String fn, Object... args) {
        if (module == null || module.isNull()) return;
        if (!module.hasMember(fn)) return;
        Value f = module.getMember(fn);
        if (f == null || !f.canExecute()) return;
        f.execute(args);
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
            } catch (Exception ignored) {}
            if (bullet != null) {try { getStateManager().detach(bullet); } catch (Exception ignored) {}bullet = null;}
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

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}