package org.foxesworld.kalitech.engine.app;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.impl.EngineApiImpl;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.WorldBuilder;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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

    private float cooldown = 0f;
    private boolean dirty = true;
    private String lastHash = null;

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

        // 1) shared runtime
        runtime = new GraalScriptRuntime();
        runtime.setModuleSourceProvider(path -> sa.getAssetManager().loadAsset(new AssetKey<>(path)));

        // 2) dev hot reload watcher
        watcher = new HotReloadWatcher(watchRoot);

        // 3) providers registry (ServiceLoader)
        registry = new SystemRegistry();

        // 4) stable API for JS
        var api = new EngineApiImpl(sa, sa.getAssetManager(), bus, ecs);

        // 5) world runner (keeps SystemContext)
        worldState = new WorldAppState(bus, ecs, runtime, api);
        getStateManager().attach(worldState);

        // 6) builder uses registry
        worldBuilder = new WorldBuilder(sa, registry);

        log.info("RuntimeAppState started: main='{}', watchRoot={}", mainAssetPath, watchRoot.toAbsolutePath());
        dirty = true;
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // пока WorldAppState не готов — просто ждём
        if (worldState == null || worldState.getContextForJs() == null) return;

        cooldown -= tpf;
        if (cooldown <= 0f && watcher != null && watcher.pollDirty()) {
            cooldown = reloadCooldownSec;
            dirty = true;
        }

        if (dirty) {
            dirty = false;
            reloadMainAndRebuildWorld();
        }
    }

    private void reloadMainAndRebuildWorld() {
        // ВАЖНО: WorldAppState может быть ещё не initialized -> ctx == null
        SystemContext ctx = (worldState != null) ? worldState.getContextForJs() : null;
        if (ctx == null) {
            // Это НОРМАЛЬНО на старте: просто отложим на следующий тик
            dirty = true;
            return;
        }

        SimpleApplication app = (SimpleApplication) getApplication();
        try {
            String code = app.getAssetManager().loadAsset(new AssetKey<>(mainAssetPath));

            String hash = sha1(code);
            if (hash.equals(lastHash)) return;
            lastHash = hash;

            Value main = runtime.loadModuleValue(mainAssetPath, code);

            Value worldDesc = extractWorldDescriptor(main);
            if (worldDesc == null || worldDesc.isNull()) {
                log.error("main.js has no world descriptor. Expect module.exports.world = {...}");
                return;
            }

            // 1) build world
            KWorld newWorld = worldBuilder.buildFromWorldDesc(ctx, worldDesc);
            worldState.setWorld(newWorld);

            // 2) optional bootstrap AFTER world created
            callIfExists(main, "bootstrap", ctx);

            log.info("World rebuilt from {}", mainAssetPath);

        } catch (Exception e) {
            log.error("Failed to rebuild world from {}", mainAssetPath, e);
        }
    }

    private static Value extractWorldDescriptor(Value module) {
        if (module == null || module.isNull()) return null;

        if (module.hasMember("exports")) {
            Value ex = module.getMember("exports");
            if (ex != null && !ex.isNull() && ex.hasMember("world")) return ex.getMember("world");
        }
        if (module.hasMember("world")) return module.getMember("world");
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
        if (worldState != null) {
            try { getStateManager().detach(worldState); } catch (Exception ignored) {}
            worldState = null;
        }
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
        registry = null;
        worldBuilder = null;
        lastHash = null;
        log.info("RuntimeAppState stopped");
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}