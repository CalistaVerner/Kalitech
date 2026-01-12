// FILE: WorldAppState.java
package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.SystemScheduler;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldAppState is OPTIONAL engine service.
 * It does nothing unless scripts explicitly create/start a world.
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private static final String HOT_RELOAD = "world:hotReload";

    private final RuntimeAppState host;     // keep host to access shared runtime/assets/bus/ecs/etc
    private final EngineApiImpl engine;
    private final Map<String, ScriptRuntime> runtimeProfiles = new ConcurrentHashMap<>();
    private ScriptRuntime baseRuntime;
    private SystemScheduler scheduler; // optional (can be null)
    private KWorld world;
    private SystemContext worldCtx;
    private final ActionListener hotReloadListener = (name, pressed, tpf) -> {
        if (!pressed) return;
        if (!HOT_RELOAD.equals(name)) return;

        if (world != null && worldCtx != null) {
            log.warn("[World] F5 hot reload");
            try {
                world.hotReload(worldCtx, "F5");
            } catch (Throwable t) {
                log.error("[World] hotReload failed", t);
            }
        }
    };
    private InputManager input;

    public WorldAppState(RuntimeAppState runtimeAppState) {
        this.host = Objects.requireNonNull(runtimeAppState, "runtimeAppState");
        this.engine = Objects.requireNonNull(runtimeAppState.getEngineApi(), "engineApi");
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
        this.baseRuntime = host.getRuntime();

        try {
            scheduler = new SystemScheduler(this);
        } catch (Throwable t) {
            log.warn("[World] SystemScheduler disabled (optional): {}", t.toString());
            scheduler = null;
        }

        input = (engine.getApp() != null) ? engine.getApp().getInputManager() : null;
        if (input != null) {
            try {
                if (!input.hasMapping(HOT_RELOAD)) input.addMapping(HOT_RELOAD, new KeyTrigger(KeyInput.KEY_F5));
                input.addListener(hotReloadListener, HOT_RELOAD);
            } catch (Throwable t) {
                log.warn("[World] HotReload keybind failed: {}", t.toString());
            }
        }
    }

    @Override
    public void update(float tpf) {
        if (world != null && worldCtx != null) {
            world.update(worldCtx, tpf);
        }
        if (scheduler != null) {
            try {
                scheduler.awaitDefaultBudget();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void cleanup(Application app) {
        destroyWorld();

        if (input != null) {
            try {
                input.removeListener(hotReloadListener);
            } catch (Throwable ignored) {
            }
            try {
                if (input.hasMapping(HOT_RELOAD)) input.deleteMapping(HOT_RELOAD);
            } catch (Throwable ignored) {
            }
            input = null;
        }

        if (scheduler != null) {
            try {
                scheduler.close();
            } catch (Throwable ignored) {
            }
            scheduler = null;
        }

        runtimeProfiles.values().forEach(rt -> {
            try {
                rt.close();
            } catch (Throwable ignored) {
            }
        });
        runtimeProfiles.clear();
        baseRuntime = null;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    // ---------------- World control (called by engine.world()) ----------------

    public void createWorld(KWorld newWorld, boolean start) {
        Objects.requireNonNull(newWorld, "newWorld");
        destroyWorld();

        this.world = newWorld;

        final ScriptRuntime rt0 = (baseRuntime != null) ? baseRuntime : host.getRuntime();

        this.worldCtx = new SystemContext(
                engine.getApp(),
                engine,
                engine.getEcs(),
                engine.getBus(),
                engine.__getPhysicsSpaceOrNull(),
                rt0,
                this::getRuntime,
                null,
                scheduler,
                null,
                null,
                engine.getLog()
        );

        log.info("[World] created '{}'", world.getName());

        if (start) startWorld();
    }

    public void startWorld() {
        if (world == null || worldCtx == null) return;
        world.start(worldCtx);
    }

    public void destroyWorld() {
        if (world != null && worldCtx != null) {
            try {
                world.stop(worldCtx);
            } catch (Throwable ignored) {
            }
        }
        world = null;
        worldCtx = null;
    }

    public KWorld getWorldOrNull() {
        return world;
    }

    public SystemContext getWorldContextOrNull() {
        return worldCtx;
    }

    // ---------------- Runtime profiles ----------------

    public SystemScheduler getSchedulerOrNull() {
        return scheduler;
    }

    public ScriptRuntime getRuntime(String profile) {
        final String p = (profile == null || profile.isBlank()) ? "world" : profile.trim();

        if ("world".equalsIgnoreCase(p) || "main".equalsIgnoreCase(p) || "default".equalsIgnoreCase(p)) {
            ScriptRuntime rt0 = (baseRuntime != null) ? baseRuntime : host.getRuntime();
            if (rt0 != null) return rt0;
        }

        return runtimeProfiles.computeIfAbsent(p, k -> {
            ScriptRuntime rt = new ScriptRuntime();
            rt.setModuleStreamProvider(this::openJsModuleStream);
            try {
                rt.initBuiltIns(engine);
            } catch (Throwable t) {
                log.warn("[World] initBuiltIns failed for profile '{}': {}", k, t.toString());
            }
            return rt;
        });
    }

    // ---------------------------------------------------------------------
    // Asset-backed module loading (same logic as RuntimeAppState)
    // ---------------------------------------------------------------------

    public EngineApiImpl getEngine() {
        return engine;
    }

    private InputStream openJsModuleStream(String moduleId) {
        try {
            String id = normalizeJsModuleId(moduleId);
            AssetManager am = (engine.getApp() != null) ? engine.getApp().getAssetManager() : null;
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
            AssetManager am = (engine.getApp() != null) ? engine.getApp().getAssetManager() : null;
            if (am == null) return null;
            return am.loadAsset(new AssetKey<>(path));
        } catch (Throwable ignored) {
            return null;
        }
    }
}