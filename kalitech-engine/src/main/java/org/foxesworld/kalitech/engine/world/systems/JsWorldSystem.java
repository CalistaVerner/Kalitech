package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;

import java.util.Objects;

/**
 * JsWorldSystem
 *
 * Runs a JS "world system" module.
 *
 * Supported module shapes:
 * 1) module.exports = { start(ctx), update(ctx,tpf), stop(ctx) }
 * 2) module.exports = { init(ctx), update(ctx,tpf), destroy(ctx) }  (legacy names)
 * 3) module.exports = function() { return { ... } }
 * 4) module.exports = { create: () => ({ ... }) }
 */
public final class JsWorldSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(JsWorldSystem.class);

    private final String moduleId;
    private final boolean hotReload;

    private SystemContext ctx;
    private GraalScriptRuntime runtime;

    private Value instance;
    private String appliedVersion;

    public JsWorldSystem(String moduleId, boolean hotReload) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.hotReload = hotReload;
    }

    public JsWorldSystem(String moduleId) {
        this(moduleId, true);
    }

    @Override
    public void onStart(SystemContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.runtime = Objects.requireNonNull(ctx.runtime(), "ctx.runtime");

        // Create initial instance
        restartIfNeeded(true);

        log.info("JsWorldSystem started: {} (hotReload={})", moduleId, hotReload);
    }

    @Override
    public void onUpdate(SystemContext context, float tpf) {
        if (runtime == null) return;

        if (hotReload) {
            restartIfNeeded(false);
        }

        if (instance != null && hasFn(instance, "update")) {
            instance.getMember("update").execute(ctx, tpf);
        }
    }

    @Override
    public void onStop(SystemContext context) {
        try {
            if (instance != null) {
                // stop(ctx) preferred
                if (hasFn(instance, "stop")) instance.getMember("stop").execute(ctx);
                else if (hasFn(instance, "destroy")) instance.getMember("destroy").execute(ctx);
            }
        } catch (Throwable t) {
            log.warn("JsWorldSystem stop failed: {}", moduleId, t);
        } finally {
            instance = null;
            appliedVersion = null;
            runtime = null;
            ctx = null;
        }

        log.info("JsWorldSystem stopped: {}", moduleId);
    }

    // ---------------- internals ----------------

    private void restartIfNeeded(boolean force) {
        String id = normalize(moduleId);
        String vStr = Long.toString(runtime.moduleVersion(id));

        if (!force && appliedVersion != null && appliedVersion.equals(vStr) && instance != null) {
            return;
        }

        // destroy old
        if (instance != null) {
            try {
                if (hasFn(instance, "stop")) instance.getMember("stop").execute(ctx);
                else if (hasFn(instance, "destroy")) instance.getMember("destroy").execute(ctx);
            } catch (Throwable t) {
                log.warn("JsWorldSystem destroy failed: {}", moduleId, t);
            }
            instance = null;
        }

        // load new
        Value exports = runtime.require(id);
        instance = createInstance(exports);
        appliedVersion = vStr;

        // start/init
        try {
            if (hasFn(instance, "start")) instance.getMember("start").execute(ctx);
            else if (hasFn(instance, "init")) instance.getMember("init").execute(ctx);
        } catch (Throwable t) {
            log.error("JsWorldSystem start failed: {}", moduleId, t);
        }
    }

    private static Value createInstance(Value exports) {
        if (exports == null || exports.isNull()) {
            throw new IllegalStateException("JS module exports is null");
        }

        if (exports.canExecute()) {
            return exports.execute();
        }

        if (exports.hasMember("create")) {
            Value c = exports.getMember("create");
            if (c != null && c.canExecute()) return c.execute();
        }

        return exports;
    }

    private static boolean hasFn(Value obj, String name) {
        return obj != null
                && !obj.isNull()
                && obj.hasMember(name)
                && obj.getMember(name) != null
                && obj.getMember(name).canExecute();
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }
}