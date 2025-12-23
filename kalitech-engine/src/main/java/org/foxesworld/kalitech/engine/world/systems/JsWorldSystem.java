package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

public final class JsWorldSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String moduleAssetPath;
    private Value module;

    public JsWorldSystem(String moduleAssetPath) {
        this.moduleAssetPath = moduleAssetPath;
    }

    @Override
    public void onStart(SystemContext ctx) {
        try {
            String code = (String) ctx.assets().loadAsset(moduleAssetPath);
            module = ctx.runtime().loadModuleValue(moduleAssetPath, code);
            callIfExists(module, "init", ctx);
            log.info("JsWorldSystem loaded: {}", moduleAssetPath);
        } catch (Exception e) {
            log.error("JsWorldSystem start failed: {}", moduleAssetPath, e);
        }
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        callIfExists(module, "update", ctx, tpf);
    }

    @Override
    public void onStop(SystemContext ctx) {
        try {
            callIfExists(module, "destroy", ctx);
        } catch (Exception e) {
            log.error("JsWorldSystem destroy failed: {}", moduleAssetPath, e);
        } finally {
            module = null;
        }
    }

    private static void callIfExists(Value module, String fn, Object... args) {
        try {
            if (module == null || module.isNull()) return;
            if (!module.hasMember(fn)) return;
            Value f = module.getMember(fn);
            if (f == null || !f.canExecute()) return;
            f.execute(args);
        } catch (org.graalvm.polyglot.PolyglotException e) {
            if (e.isCancelled()) return; // норм при shutdown/reload
            throw e;
        }
    }
}