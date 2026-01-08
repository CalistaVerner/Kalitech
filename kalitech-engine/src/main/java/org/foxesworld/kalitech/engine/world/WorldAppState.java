// FILE: WorldAppState.java
package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
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

    private final RuntimeAppState host;     // ✅ keep host to access shared runtime/assets/bus/ecs/etc
    private final EngineApiImpl engine;
    private final Map<String, ScriptRuntime> runtimeProfiles = new ConcurrentHashMap<>();
    // Base runtime configured in RuntimeAppState (providers + builtins)
    private ScriptRuntime baseRuntime;
    private SystemScheduler scheduler; // optional (can be null)
    private KWorld world;
    private SystemContext worldCtx;

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
        // snapshot base runtime (must be configured in RuntimeAppState)
        this.baseRuntime = host.getRuntime();

        // Scheduler is optional — can be enabled later if you want.
        try {
            scheduler = new SystemScheduler(this);
        } catch (Throwable t) {
            log.warn("[World] SystemScheduler disabled (optional): {}", t.toString());
            scheduler = null;
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

    // ---------------- World control (called by engine.world()) ----------------

    @Override
    protected void onDisable() {
    }

    public void createWorld(KWorld newWorld, boolean start) {
        Objects.requireNonNull(newWorld, "newWorld");
        destroyWorld();

        this.world = newWorld;

        // ✅ IMPORTANT: always use RuntimeAppState's runtime as base (configured providers)
        final ScriptRuntime rt0 = (baseRuntime != null) ? baseRuntime : host.getRuntime();

        this.worldCtx = new SystemContext(
                engine.getApp(),
                engine,
                engine.getEcs(),
                engine.getBus(),
                engine.__getPhysicsSpaceOrNull(),
                rt0,                         // base runtime (shared)
                this::getRuntime,            // runtimeProvider (profiles)
                null,                        // runtimePolicy (optional)
                scheduler,                   // scheduler (optional)
                null,                        // mainQueue (optional)
                null,                        // perfProvider (optional)
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

    /**
     * Runtime profile pool.
     * CRITICAL: profile runtimes MUST be configured with the same module providers as base runtime,
     * otherwise require() will fail (no ModuleStreamProvider).
     */
    public ScriptRuntime getRuntime(String profile) {
        final String p = (profile == null || profile.isBlank()) ? "world" : profile.trim();

        // Shortcut: if someone asks for default profile, we can return base runtime directly.
        // (Optional but safe; avoids extra runtimes until you really need isolation.)
        if ("world".equalsIgnoreCase(p) || "main".equalsIgnoreCase(p) || "default".equalsIgnoreCase(p)) {
            ScriptRuntime rt0 = (baseRuntime != null) ? baseRuntime : host.getRuntime();
            if (rt0 != null) return rt0;
        }

        return runtimeProfiles.computeIfAbsent(p, k -> {
            ScriptRuntime rt = new ScriptRuntime();

            // ✅ providers first
            rt.setModuleStreamProvider(this::openJsModuleStream);
            // rt.setModuleSourceProvider(this::loadTextAssetOrNull); // if you use it

            // ✅ then builtins
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