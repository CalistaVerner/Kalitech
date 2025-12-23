package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.asset.AssetKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;
import org.foxesworld.kalitech.engine.script.KalitechAPI;
import org.foxesworld.kalitech.engine.script.ScriptModule;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;

import java.nio.file.Path;

public final class ScriptSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(ScriptSystem.class);

    private final String scriptAssetPath; // "Scripts/main.js"
    private final boolean devHotReload;

    private GraalScriptRuntime runtime;
    private ScriptModule module;
    private KalitechAPI api;

    private HotReloadWatcher watcher;
    private float cooldown = 0f;

    public ScriptSystem(String scriptAssetPath, boolean devHotReload) {
        this.scriptAssetPath = scriptAssetPath;
        this.devHotReload = devHotReload;
    }

    @Override
    public void onStart(SystemContext ctx) {
        runtime = new GraalScriptRuntime();
        api = new KalitechAPI(ctx.app(), ctx.events());

        if (devHotReload) {
            watcher = new HotReloadWatcher(Path.of("assets"));
        }

        reload(ctx);
        log.info("ScriptSystem started: {}", scriptAssetPath);
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        if (module != null) {
            module.update(api, tpf);
        }

        if (watcher != null) {
            cooldown -= tpf;
            if (cooldown <= 0f && watcher.pollDirty()) {
                cooldown = 0.35f;
                log.info("Hot-reload triggered for {}", scriptAssetPath);
                reload(ctx);
            }
        }
    }

    @Override
    public void onStop(SystemContext ctx) {
        try {
            if (module != null) module.destroy(api);
        } catch (Exception e) {
            log.error("Error in JS destroy()", e);
        }

        if (watcher != null) {
            watcher.close();
            watcher = null;
        }

        if (runtime != null) {
            runtime.close();
            runtime = null;
        }

        module = null;
        api = null;

        log.info("ScriptSystem stopped: {}", scriptAssetPath);
    }

    private void reload(SystemContext ctx) {
        try {
            ctx.assets().deleteFromCache(new AssetKey<>(scriptAssetPath));
            String code = (String) ctx.assets().loadAsset(new AssetKey<>(scriptAssetPath));
            log.info("Loaded script asset: {} ({} chars)", scriptAssetPath, code.length());

            if (module != null) {
                try { module.destroy(api); } catch (Exception e) { log.error("Old destroy() failed", e); }
            }

            module = runtime.loadModule(scriptAssetPath, code);
            module.init(api);

            ctx.events().emit("engine.ready", "ok");
        } catch (Exception e) {
            log.error("Script reload failed: {}", scriptAssetPath, e);
        }
    }
}