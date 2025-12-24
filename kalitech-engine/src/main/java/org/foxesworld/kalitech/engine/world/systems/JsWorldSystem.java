package org.foxesworld.kalitech.engine.world.systems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.script.GraalScriptRuntime;

import java.util.Objects;

/**
 * JsWorldSystem
 *
 * Runs a JS "world system" module.
 *
 * Shutdown-safe:
 * - If Graal context is closing/cancelled, stop() becomes no-op (no WARN spam).
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

        restartIfNeeded(true);

        log.info("JsWorldSystem started: {} (hotReload={})", moduleId, hotReload);
    }

    @Override
    public void onUpdate(SystemContext systemContext, float tpf) {
        if (runtime == null) return;

        if (hotReload) {
            restartIfNeeded(false);
        }

        // update(ctx,tpf)
        try {
            if (safeHasFn(instance, "update")) {
                instance.getMember("update").execute(ctx, tpf);
            }
        } catch (PolyglotException pe) {
            // During shutdown/cancel, treat as normal.
            if (!isContextCancelled(pe)) {
                log.warn("JsWorldSystem update failed: {}", moduleId, pe);
            }
        } catch (Throwable t) {
            log.warn("JsWorldSystem update failed: {}", moduleId, t);
        }
    }

    @Override
    public void onStop(SystemContext systemContext) {
        // IMPORTANT: During shutdown Graal context may already be cancelled/closing.
        // Any Value.* call can throw PolyglotException. Treat it as normal and do not warn.
        try {
            safeStopInstance();
        } finally {
            instance = null;
            appliedVersion = null;
            runtime = null;
            ctx = null;
            log.info("JsWorldSystem stopped: {}", moduleId);
        }
    }

    // ---------------- internals ----------------

    private void safeStopInstance() {
        if (instance == null) return;

        try {
            // stop(ctx) preferred
            if (safeHasFn(instance, "stop")) {
                instance.getMember("stop").execute(ctx);
                return;
            }
            // legacy alias
            if (safeHasFn(instance, "destroy")) {
                instance.getMember("destroy").execute(ctx);
            }
        } catch (PolyglotException pe) {
            if (!isContextCancelled(pe)) {
                log.warn("JsWorldSystem stop failed: {}", moduleId, pe);
            }
            // cancelled -> swallow
        } catch (Throwable t) {
            log.warn("JsWorldSystem stop failed: {}", moduleId, t);
        }
    }

    private void restartIfNeeded(boolean force) {
        String id = normalize(moduleId);
        String vStr = "0";
        try {
            vStr = Long.toString(runtime.moduleVersion(id));
        } catch (PolyglotException pe) {
            // runtime closing -> just keep current instance
            if (isContextCancelled(pe)) return;
            throw pe;
        }

        if (!force && appliedVersion != null && appliedVersion.equals(vStr) && instance != null) {
            return;
        }

        // destroy old (safe)
        if (instance != null) {
            safeStopInstance();
            instance = null;
        }

        // load new
        try {
            Value exports = runtime.require(id);
            instance = createInstance(exports);
            appliedVersion = vStr;

            // start/init
            if (safeHasFn(instance, "start")) instance.getMember("start").execute(ctx);
            else if (safeHasFn(instance, "init")) instance.getMember("init").execute(ctx);

        } catch (PolyglotException pe) {
            if (!isContextCancelled(pe)) {
                log.error("JsWorldSystem start failed: {}", moduleId, pe);
            }
            // cancelled -> swallow; engine is shutting down
        } catch (Throwable t) {
            log.error("JsWorldSystem start failed: {}", moduleId, t);
        }
    }

    private static Value createInstance(Value exports) {
        if (exports == null) return null;

        try {
            if (exports.isNull()) return null;

            if (exports.canExecute()) return exports.execute();

            if (exports.hasMember("create")) {
                Value c = exports.getMember("create");
                if (c != null && !c.isNull() && c.canExecute()) return c.execute();
            }

            return exports;
        } catch (PolyglotException pe) {
            if (isContextCancelled(pe)) return null;
            throw pe;
        }
    }

    /**
     * Absolutely must be shutdown-safe: ANY Value.* may throw when context is cancelled.
     */
    private static boolean safeHasFn(Value obj, String name) {
        if (obj == null) return false;
        try {
            if (obj.isNull()) return false;
            if (!obj.hasMember(name)) return false;
            Value m = obj.getMember(name);
            return m != null && !m.isNull() && m.canExecute();
        } catch (PolyglotException pe) {
            return !isContextCancelled(pe) ? false : false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isContextCancelled(PolyglotException pe) {
        // Graal uses "Context execution was cancelled." and similar on shutdown.
        // Also treat "closed" as normal shutdown.
        String msg = pe.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("cancel") || m.contains("closed");
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }
}