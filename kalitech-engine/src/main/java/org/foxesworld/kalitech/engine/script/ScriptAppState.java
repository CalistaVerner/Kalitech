package org.foxesworld.kalitech.engine.script;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.hotreload.HotReloadWatcher;

import java.nio.file.Path;

public final class ScriptAppState extends BaseAppState {

    private static final Logger log = LogManager.getLogger(ScriptAppState.class);

    private final AssetManager assetManager;
    private final String scriptAssetPath; // пример: "Scripts/main.js"

    private GraalScriptRuntime runtime;
    private ScriptModule module;

    private ScriptEventBus eventBus;
    private KalitechAPI api;

    // DEV: hot reload из ./assets (см. KalitechApplication.registerLocator("assets", FileLocator))
    private HotReloadWatcher watcher;
    private float reloadCooldown = 0f;

    public ScriptAppState(AssetManager assetManager, String scriptAssetPath) {
        this.assetManager = assetManager;
        this.scriptAssetPath = scriptAssetPath;
    }

    @Override
    protected void initialize(Application app) {
        if (!(app instanceof SimpleApplication sa)) {
            throw new IllegalStateException("ScriptAppState requires SimpleApplication");
        }

        this.eventBus = new ScriptEventBus();
        this.api = new KalitechAPI(sa, eventBus);
        this.runtime = new GraalScriptRuntime();

        // DEV watcher: следит за папкой ./assets (где лежит Scripts/main.js)
        // Если ты не используешь внешний assets/ — можно закомментировать.
        this.watcher = new HotReloadWatcher(Path.of("assets"));

        reloadScript();
    }

    @Override
    public void update(float tpf) {
        // 1) JS update
        if (module != null) {
            module.update(api, tpf);
        }

        // 2) pump событий JS<->Java
        if (eventBus != null) {
            eventBus.pump();
        }

        // 3) hot reload (с небольшим cooldown, чтобы не перезагружать 10 раз за сохранение файла)
        reloadCooldown -= tpf;
        if (watcher != null && reloadCooldown <= 0f && watcher.pollDirty()) {
            reloadCooldown = 0.35f;
            log.info("Hot-reload triggered");
            reloadScript();
        }
    }

    private void reloadScript() {
        try {
            // чистим кэш ассетов, иначе AssetManager может вернуть старый текст
            assetManager.deleteFromCache(new AssetKey<>(scriptAssetPath));

            String code = (String) assetManager.loadAsset(new AssetKey<>(scriptAssetPath));
            log.info("Loaded script asset: {} ({} chars)", scriptAssetPath, code.length());

            // destroy старого модуля
            if (module != null) {
                try {
                    module.destroy(api);
                } catch (Exception e) {
                    log.error("Error in old module destroy()", e);
                }
            }

            // reload
            module = runtime.loadModule(scriptAssetPath, code);
            module.init(api);

            // пример: событие “движок готов”
            eventBus.emit("engine.ready", "ok");

        } catch (Exception e) {
            log.error("Script reload failed: {}", scriptAssetPath, e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        try {
            if (module != null) module.destroy(api);
        } catch (Exception e) {
            log.error("Error in module destroy()", e);
        }

        if (watcher != null) {
            watcher.close();
            watcher = null;
        }

        if (eventBus != null) {
            eventBus.clearAll();
            eventBus = null;
        }

        if (runtime != null) {
            runtime.close();
            runtime = null;
        }

        module = null;
        api = null;

        log.info("ScriptAppState cleaned up");
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}