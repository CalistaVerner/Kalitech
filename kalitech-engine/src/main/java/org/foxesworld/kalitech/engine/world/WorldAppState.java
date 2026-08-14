// FILE: WorldAppState.java
package org.foxesworld.kalitech.engine.world;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
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
import org.foxesworld.kalitech.engine.world.systems.MainThreadBudgetQueue;
import org.foxesworld.kalitech.engine.world.systems.FrameStats;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.SystemScheduler;
import org.foxesworld.kalitech.engine.world.systems.WorkerSystemStats;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldAppState is an optional engine service.
 * It does nothing unless scripts explicitly create/start a world.
 */
public final class WorldAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(WorldAppState.class);

    private static final String HOT_RELOAD = "world:hotReload";

    private final RuntimeAppState host;
    private final EngineApiImpl engine;

    private final Map<String, ScriptRuntime> runtimeProfiles = new ConcurrentHashMap<>();

    private ScriptRuntime baseRuntime;
    private final ActionListener hotReloadListener = (name, pressed, tpf) -> {
        if (!pressed) return;
        if (!HOT_RELOAD.equals(name)) return;

        KWorld w = this.world;
        SystemContext ctx = this.worldCtx;
        if (w == null || ctx == null) return;

        log.warn("[World] F5 hot reload");
        try {
            w.hotReload(ctx, "F5");
        } catch (Throwable t) {
            log.error("[World] hotReload failed", t);
        }
    };

    private boolean baseRuntimeInitialized;
    private KWorld world;
    private SystemContext worldCtx;
    private SystemScheduler scheduler;
    private InputManager input;
    private volatile boolean schedulerErrorLogged;
    private volatile FrameStats lastFrameStats;
    private final WorldPerfProvider perfProvider = new WorldPerfProvider();

    public WorldAppState(RuntimeAppState runtimeAppState) {
        this.host = Objects.requireNonNull(runtimeAppState, "runtimeAppState");
        this.engine = Objects.requireNonNull(runtimeAppState.getEngineApi(), "engineApi");
    }

    private static String normalizeLuaModuleId(String moduleId) {
        String id = (moduleId == null) ? "" : moduleId.trim();
        if (id.isEmpty()) return "";
        id = id.replace('\\', '/');
        while (id.startsWith("./")) id = id.substring(2);
        while (id.startsWith("/")) id = id.substring(1);
        if (!id.endsWith(".lua") && !id.endsWith(".json")) id += ".lua";
        return id;
    }

    private static String safeWorldName(KWorld w) {
        try {
            return (w != null) ? String.valueOf(w.getName()) : "null";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static SimpleApplication coerceSimpleApp(Application app) {
        return (app instanceof SimpleApplication sa) ? sa : null;
    }

    @Override
    protected void initialize(Application app) {
        this.baseRuntime = host.getRuntime();
        ensureBaseRuntimeInitialized();

        try {
            this.scheduler = new SystemScheduler(this);
        } catch (Throwable t) {
            log.warn("[World] SystemScheduler disabled (optional): {}", t.toString(), t);
            this.scheduler = null;
        }

        this.input = (engine.getApp() != null) ? engine.getApp().getInputManager() : null;
        if (this.input != null) {
            try {
                if (!input.hasMapping(HOT_RELOAD)) {
                    input.addMapping(HOT_RELOAD, new KeyTrigger(KeyInput.KEY_F5));
                }
                input.addListener(hotReloadListener, HOT_RELOAD);
            } catch (Throwable t) {
                log.warn("[World] HotReload keybind failed: {}", t.toString(), t);
            }
        }
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    // ---------------- World control (called by engine.world()) ----------------

    @Override
    public void update(float tpf) {
        KWorld w = this.world;
        SystemContext ctx = this.worldCtx;

        if (w != null && ctx != null) {
            try {
                long start = System.nanoTime();
                w.update(ctx, tpf);
                long worldUpdateNanos = Math.max(0L, System.nanoTime() - start);
                recordFrameStats(w, worldUpdateNanos, 0L);
            } catch (Throwable t) {
                log.error("[World] update failed (world='{}')", safeWorldName(w), t);
            }
        }

        SystemScheduler s = this.scheduler;
        if (s != null) {
            try {
                long awaitStart = System.nanoTime();
                s.awaitDefaultBudget();
                long awaitNanos = Math.max(0L, System.nanoTime() - awaitStart);
                recordFrameStats(w, 0L, awaitNanos);
            } catch (Throwable t) {
                if (!schedulerErrorLogged) {
                    schedulerErrorLogged = true;
                    log.error("[World] scheduler.awaitDefaultBudget failed (will be suppressed afterwards)", t);
                }
            }
        }
    }

    @Override
    protected void cleanup(Application app) {
        destroyWorld();

        if (input != null) {
            try {
                input.removeListener(hotReloadListener);
            } catch (Throwable t) {
                log.warn("[World] removeListener failed: {}", t.toString(), t);
            }
            try {
                if (input.hasMapping(HOT_RELOAD)) input.deleteMapping(HOT_RELOAD);
            } catch (Throwable t) {
                log.warn("[World] deleteMapping failed: {}", t.toString(), t);
            }
            input = null;
        }

        if (scheduler != null) {
            try {
                scheduler.close();
            } catch (Throwable t) {
                log.warn("[World] scheduler.close failed: {}", t.toString(), t);
            }
            scheduler = null;
        }

        runtimeProfiles.forEach((k, rt) -> {
            try {
                rt.close();
            } catch (Throwable t) {
                log.warn("[World] runtime profile close failed (profile='{}'): {}", k, t.toString(), t);
            }
        });
        runtimeProfiles.clear();

        baseRuntime = null;
        baseRuntimeInitialized = false;
        schedulerErrorLogged = false;
    }

    public void createWorld(KWorld newWorld, boolean start) {
        Objects.requireNonNull(newWorld, "newWorld");
        destroyWorld();

        this.world = newWorld;

        final ScriptRuntime rt0 = getBaseRuntimeOrNull();
        if (rt0 == null) {
            log.error("[World] cannot create world: base runtime is null");
            this.world = null;
            return;
        }

        SimpleApplication sa = coerceSimpleApp(engine.getApp());
        if (sa == null) {
            log.error("[World] cannot create world: engine.getApp() is not a SimpleApplication");
            this.world = null;
            return;
        }

        final SystemContext.RuntimePolicy policy = new DefaultRuntimePolicy();

        // IMPORTANT FIX:
        // - pass WorldTime from KWorld
        // - pass MainThreadBudgetQueue instance
        this.worldCtx = new SystemContext(
                sa,
                engine,
                engine.getEcs(),
                engine.getBus(),
                engine.__getPhysicsSpaceOrNull(),
                newWorld.worldTime(),     // WorldTime
                rt0,                      // base runtime
                this::getRuntime,          // runtime provider
                policy,
                scheduler,
                new MainThreadBudgetQueue(),
                perfProvider,             // perf provider
                engine.getLog()
        );

        log.info("[World] created '{}'", safeWorldName(world));

        if (start) startWorld();
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

    public void startWorld() {
        KWorld w = this.world;
        SystemContext ctx = this.worldCtx;
        if (w == null || ctx == null) return;

        try {
            w.start(ctx);
        } catch (Throwable t) {
            log.error("[World] start failed (world='{}')", safeWorldName(w), t);
        }
    }

    private void recordFrameStats(KWorld w, long worldUpdateNanos, long awaitWorkersNanos) {
        long frameIdx = 0L;
        if (w != null && w.getTime() != null) frameIdx = w.getTime().getFrameIndex();

        long existingWorld = 0L;
        long existingAwait = 0L;
        if (lastFrameStats != null && lastFrameStats.frameIndex == frameIdx) {
            existingWorld = lastFrameStats.worldUpdateNanos;
            existingAwait = lastFrameStats.awaitWorkersNanos;
        }

        long totalWorld = existingWorld + worldUpdateNanos;
        long totalAwait = existingAwait + awaitWorkersNanos;
        long frameNanos = totalWorld + totalAwait;
        long budgetNanos = (long) (1_000_000_000.0 / 60.0);

        lastFrameStats = new FrameStats(
                frameIdx,
                budgetNanos,
                frameNanos,
                0L,
                0L,
                0L,
                totalWorld,
                totalAwait,
                0L,
                0,
                0L,
                0L
        );
    }

    private final class WorldPerfProvider implements SystemContext.PerfProvider {
        @Override
        public FrameStats getLastFrameStats() {
            return lastFrameStats;
        }

        @Override
        public WorkerSystemStats[] getWorkerStatsSnapshot() {
            SystemScheduler s = scheduler;
            return (s != null) ? s.statsSnapshot() : new WorkerSystemStats[0];
        }

        @Override
        public void dumpPerfSnapshotToLog() {
            if (lastFrameStats == null) return;
            log.info("[perf][frame] frame={} totalMs={} worldMs={} awaitMs={}",
                    lastFrameStats.frameIndex,
                    lastFrameStats.frameNanos / 1_000_000.0,
                    lastFrameStats.worldUpdateNanos / 1_000_000.0,
                    lastFrameStats.awaitWorkersNanos / 1_000_000.0);
        }

        @Override
        public void setTargetFps(int fps) {
            // no-op (fixed budget derived from fps is not yet configurable)
        }

        @Override
        public void setStatsLogEverySeconds(int sec) {
            // no-op
        }

        @Override
        public void setFrameOverBudgetLogEverySeconds(int sec) {
            // no-op
        }

        @Override
        public boolean isWorldThread() {
            return engine.isJmeThread();
        }
    }

    public void destroyWorld() {
        KWorld w = this.world;
        SystemContext ctx = this.worldCtx;

        if (w != null && ctx != null) {
            try {
                w.stop(ctx);
            } catch (Throwable t) {
                log.error("[World] stop failed (world='{}')", safeWorldName(w), t);
            }
        }

        this.world = null;
        this.worldCtx = null;
    }

    public ScriptRuntime getRuntime(String profile) {
        final String p = (profile == null || profile.isBlank()) ? "world" : profile.trim();

        if ("world".equalsIgnoreCase(p) || "main".equalsIgnoreCase(p) || "default".equalsIgnoreCase(p)) {
            ScriptRuntime rt0 = getBaseRuntimeOrNull();
            if (rt0 != null) return rt0;
        }

        return runtimeProfiles.computeIfAbsent(p, k -> {
            ScriptRuntime rt = new ScriptRuntime();
            try {
                rt.setModuleStreamProvider(this::openLuaModuleStream);
            } catch (Throwable t) {
                log.warn("[World] setModuleStreamProvider failed for profile '{}': {}", k, t.toString(), t);
            }
            try {
                rt.initBuiltIns(engine);
            } catch (Throwable t) {
                log.error("[World] initBuiltIns failed for profile '{}'", k, t);
            }
            return rt;
        });
    }

    // ---------------------------------------------------------------------
    // Asset-backed module loading
    // ---------------------------------------------------------------------

    public EngineApiImpl getEngine() {
        return engine;
    }

    private ScriptRuntime getBaseRuntimeOrNull() {
        ScriptRuntime rt0 = (baseRuntime != null) ? baseRuntime : host.getRuntime();
        if (rt0 == null) return null;

        if (!baseRuntimeInitialized) {
            baseRuntime = rt0;
            ensureBaseRuntimeInitialized();
        }
        return rt0;
    }

    private void ensureBaseRuntimeInitialized() {
        if (baseRuntimeInitialized) return;

        if (this.baseRuntime == null) {
            log.warn("[World] base runtime is null (host.getRuntime returned null)");
            return;
        }

        try {
            this.baseRuntime.setModuleStreamProvider(this::openLuaModuleStream);
        } catch (Throwable t) {
            log.warn("[World] failed to set module stream provider for base runtime: {}", t.toString(), t);
        }

        try {
            this.baseRuntime.initBuiltIns(engine);
        } catch (Throwable t) {
            log.error("[World] initBuiltIns failed for base runtime", t);
        }

        baseRuntimeInitialized = true;
    }

    private InputStream openLuaModuleStream(String moduleId) {
        try {
            String id = normalizeLuaModuleId(moduleId);
            if (id.isEmpty()) return null;

            AssetManager am = (engine.getApp() != null) ? engine.getApp().getAssetManager() : null;
            if (am == null) return null;

            var ai = am.locateAsset(new AssetKey<>(id));
            return (ai != null) ? ai.openStream() : null;
        } catch (Throwable t) {
            log.warn("[World] openLuaModuleStream failed (moduleId='{}'): {}", moduleId, t.toString(), t);
            return null;
        }
    }

    @SuppressWarnings("unused")
    private Object loadTextAssetOrNull(String path) {
        try {
            AssetManager am = (engine.getApp() != null) ? engine.getApp().getAssetManager() : null;
            if (am == null) return null;
            return am.loadAsset(new AssetKey<>(path));
        } catch (Throwable t) {
            log.warn("[World] loadTextAssetOrNull failed (path='{}'): {}", path, t.toString(), t);
            return null;
        }
    }

    /**
     * Default runtime policy for WorldAppState. Keeps the contract non-null and enforced.
     *
     * <p>Policy:</p>
     * <ul>
     *   <li>Deny {@link SystemContext.RuntimePolicy.Capability#UNSAFE}</li>
     *   <li>Allow everything else by default</li>
     * </ul>
     */
    private static final class DefaultRuntimePolicy implements SystemContext.RuntimePolicy {

        @Override
        public void assertAllowed(String profile, String systemId, Capability capability) {
            Objects.requireNonNull(capability, "capability");

            if (capability == Capability.UNSAFE) {
                String p = (profile == null) ? "" : profile.trim();
                String s = (systemId == null) ? "" : systemId.trim();
                throw new SecurityException("Denied capability=" + capability + " for system=" + s + " profile=" + p);
            }
        }
    }
}
