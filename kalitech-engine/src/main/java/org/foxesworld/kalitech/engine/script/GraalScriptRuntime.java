// FILE: GraalScriptRuntime.java
package org.foxesworld.kalitech.engine.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GraalScriptRuntime (patched)
 * <p>
 * Adds a minimal CommonJS-like "require()" with:
 * - module.exports / exports
 * - cache
 * - relative path resolution (./ ../)
 * - cycle-safe partial exports during LOADING
 * <p>
 * IMPORTANT:
 * This runtime does NOT access files by itself. You must provide a ModuleSourceProvider
 * that returns JS source text by module id (asset path).
 */
public final class GraalScriptRuntime implements Closeable {

    private static final Logger log = LogManager.getLogger(GraalScriptRuntime.class);

    /**
     * Provides source text for a module id like "Scripts/systems/ai.js".
     */
    @FunctionalInterface
    public interface ModuleSourceProvider {
        String loadText(String moduleId) throws Exception;
    }

    private final Context ctx;

    // Optional loader (set by director / appstate)
    private volatile ModuleSourceProvider loader;

    // CommonJS cache + cycle support
    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();

    private enum State {LOADING, LOADED}

    private static final class ModuleRecord {
        final Value moduleObj;   // { exports: ... }
        final Value exportsObj;  // exports object
        volatile State state = State.LOADING;

        ModuleRecord(Value moduleObj, Value exportsObj) {
            this.moduleObj = moduleObj;
            this.exportsObj = exportsObj;
        }
    }

    public GraalScriptRuntime() {
        log.info("Initializing ScriptRuntime...");

        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.NONE)
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .build();

        this.ctx = Context.newBuilder("js")
                .allowAllAccess(false)
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        // Expose host require entrypoint to JS
        ctx.getBindings("js").putMember("__kt_require", (ProxyExecutable) args -> {
            // args: [parentId, request]
            if (args.length < 2) throw new IllegalArgumentException("__kt_require(parentId, request) expected");
            String parentId = args[0].isNull() ? "" : args[0].asString();
            String request = args[1].asString();
            return requireFrom(parentId, request);
        });

        log.info("GraalJS context created (require enabled)");
    }

    /**
     * Set/replace the module source provider used by require().
     * Call this once from your director/appstate (AssetManager-backed).
     */
    public void setModuleSourceProvider(ModuleSourceProvider provider) {
        this.loader = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Loads a JS module that returns an object (legacy behavior).
     * NOTE: This does NOT automatically enable CommonJS exports unless your code uses it.
     */
    public ScriptModule loadModule(String name, String code) {
        log.info("Loading JS module: {}", name);

        try {
            Source src = Source.newBuilder("js", code, name).build();
            log.debug("JS source built ({} chars)", code.length());

            Value obj = ctx.eval(src);
            log.debug("JS source evaluated");

            if (obj == null || obj.isNull()) {
                log.error("JS module returned null: {}", name);
                throw new IllegalStateException("JS module returned null: " + name);
            }

            if (!obj.hasMembers()) {
                log.error("JS module has no members: {}", name);
                throw new IllegalStateException("JS module must return an object with init/update/destroy: " + name);
            }

            log.info("JS module loaded successfully: {}", name);
            return new GraalScriptModule(obj);

        } catch (Exception e) {
            log.error("Failed to load JS module: {}", name, e);
            throw new RuntimeException("Failed to load JS module: " + name, e);
        }
    }

    /**
     * Loads a module as Value with CommonJS wrapper.
     * <p>
     * Behavior:
     * - executes code as if it's a CommonJS file
     * - returns module.exports (Value)
     * - supports require() inside
     * <p>
     * "name" is used as moduleId for relative resolution and caching.
     */
    public Value loadModuleValue(String name, String code) {
        try {
            // register/execute as CommonJS
            return evalCommonJsModule(name, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JS module: " + name, e);
        }
    }

    // -----------------------------
    // CommonJS core
    // -----------------------------

    private Value requireFrom(String parentId, String request) {
        final String moduleId = resolveModuleId(parentId, request);

        // cache hit
        ModuleRecord existing = moduleCache.get(moduleId);
        if (existing != null) {
            // cycle-safe: if LOADING return partial exports
            return existing.moduleObj.getMember("exports");
        }

        // create placeholder record for cycles
        Value exportsObj = ctx.eval("js", "({})");
        Value moduleObj = ctx.eval("js", "({})");
        moduleObj.putMember("exports", exportsObj);

        ModuleRecord rec = new ModuleRecord(moduleObj, exportsObj);
        moduleCache.put(moduleId, rec);

        // load and execute
        String code;
        try {
            ModuleSourceProvider l = loader;
            if (l == null) {
                throw new IllegalStateException(
                        "require() is used but no ModuleSourceProvider is set on GraalScriptRuntime. " +
                                "Call runtime.setModuleSourceProvider(path -> assetManager.loadAsset(path))");
            }
            code = l.loadText(moduleId);
            if (code == null) throw new IllegalStateException("ModuleSourceProvider returned null for " + moduleId);
        } catch (Exception e) {
            moduleCache.remove(moduleId);
            throw new RuntimeException("Failed to load module source: " + moduleId, e);
        }

        try {
            evalCommonJsInto(moduleId, code, rec);
            rec.state = State.LOADED;
            return rec.moduleObj.getMember("exports");
        } catch (Exception e) {
            moduleCache.remove(moduleId);
            throw new RuntimeException("Failed to eval required module: " + moduleId, e);
        }
    }

    private Value evalCommonJsModule(String moduleId, String code) throws Exception {
        // If already loaded via require-cache, return cached exports
        ModuleRecord rec = moduleCache.get(moduleId);
        if (rec != null) return rec.moduleObj.getMember("exports");

        // create record
        Value exportsObj = ctx.eval("js", "({})");
        Value moduleObj = ctx.eval("js", "({})");
        moduleObj.putMember("exports", exportsObj);

        ModuleRecord created = new ModuleRecord(moduleObj, exportsObj);
        moduleCache.put(moduleId, created);

        try {
            evalCommonJsInto(moduleId, code, created);
            created.state = State.LOADED;
            return created.moduleObj.getMember("exports");
        } catch (Exception e) {
            moduleCache.remove(moduleId);
            throw e;
        }
    }

    private void evalCommonJsInto(String moduleId, String code, ModuleRecord rec) throws Exception {
        // CommonJS wrapper: (function(module, exports, require, __filename, __dirname){ ... })
        String wrapped =
                "(function(module, exports, require, __filename, __dirname) {\n" +
                        "  'use strict';\n" +
                        code + "\n" +
                        "})";

        Source src = Source.newBuilder("js", wrapped, moduleId).build();
        Value fn = ctx.eval(src);
        if (fn == null || !fn.canExecute()) {
            throw new IllegalStateException("Wrapped module is not executable: " + moduleId);
        }

        String dir = dirnameOf(moduleId);

        // require bound to this module
        ProxyExecutable requireFn = args -> {
            if (args.length < 1) throw new IllegalArgumentException("require(request) expected");
            String request = args[0].asString();
            // call host-provided __kt_require(parentId, request) (bound in bindings)
            Value hostRequire = ctx.getBindings("js").getMember("__kt_require");
            return hostRequire.execute(moduleId, request);
        };

        // execute wrapper
        fn.execute(rec.moduleObj, rec.exportsObj, requireFn, moduleId, dir);

        // Ensure module.exports exists
        Value ex = rec.moduleObj.getMember("exports");
        if (ex == null || ex.isNull()) {
            // if module replaced exports with null, restore empty object
            rec.moduleObj.putMember("exports", ctx.eval("js", "({})"));
        }
    }

    // -----------------------------
    // Path resolution helpers
    // -----------------------------

    private static String resolveModuleId(String parentId, String request) {
        String req = request.replace('\\', '/').trim();
        if (req.isEmpty()) throw new IllegalArgumentException("require('') is not allowed");

        // If absolute "Scripts/..." style
        boolean isRelative = req.startsWith("./") || req.startsWith("../");

        String base = isRelative ? dirnameOf(parentId) : "";
        String resolved;

        if (isRelative) {
            // Resolve like paths (not file system, but same rules)
            Path p = Path.of(base.isEmpty() ? "." : base).resolve(req).normalize();
            resolved = p.toString().replace('\\', '/');
        } else {
            resolved = req;
        }

        // Add extension defaults
        if (resolved.endsWith("/")) resolved = resolved + "index.js";
        if (!hasExtension(resolved)) resolved = resolved + ".js";

        // Clean leading "./"
        while (resolved.startsWith("./")) resolved = resolved.substring(2);

        return resolved;
    }

    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash;
    }

    private static String dirnameOf(String moduleId) {
        if (moduleId == null) return "";
        String s = moduleId.replace('\\', '/');
        int idx = s.lastIndexOf('/');
        if (idx <= 0) return "";
        return s.substring(0, idx);
    }

    // -----------------------------
    // Legacy ScriptModule wrapper
    // -----------------------------

    private static final class GraalScriptModule implements ScriptModule {
        private final Value module;

        private GraalScriptModule(Value module) {
            this.module = module;
        }

        @Override
        public void init(Object api) {
            callIfExists("init", api);
        }

        @Override
        public void update(Object api, float tpf) {
            callIfExists("update", api, tpf);
        }

        @Override
        public void destroy(Object api) {
            callIfExists("destroy", api);
        }

        private void callIfExists(String fn, Object... args) {
            if (!module.hasMember(fn)) return;
            Value f = module.getMember(fn);
            if (f == null || !f.canExecute()) return;
            f.execute(args);
        }
    }

    /**
     * Hot reload support: invalidate a specific module id from CommonJS cache.
     * Next require(moduleId) will reload source.
     */
    public void invalidate(String moduleId) {
        if (moduleId == null) return;
        moduleCache.remove(moduleId.replace('\\', '/'));
    }

    /**
     * Bind stable globals for scripts:
     * - ctx     : SystemContext
     * - engine  : EngineApi
     * - render  : EngineApi.render()
     * - world   : EngineApi.entity() (temporary facade)
     *
     * Safe to call on every rebuild; overwrites previous bindings.
     */
    public void bindGlobals(Object systemContext, Object engineApi) {
        // "systemContext" is passed as Object to avoid cyclic deps from script package to world package.
        // HostAccess.Export is still required for members to be visible.
        var b = ctx.getBindings("js");

        if (systemContext != null) b.putMember("ctx", systemContext);
        if (engineApi != null) {
            b.putMember("engine", engineApi);

            // Resolve render/world via reflection so script module doesn't depend on api interfaces at compile-time
            try {
                Object render = engineApi.getClass().getMethod("render").invoke(engineApi);
                if (render != null) b.putMember("render", render);
            } catch (Exception ignored) {}

            try {
                Object world = engineApi.getClass().getMethod("world").invoke(engineApi);
                if (world != null) b.putMember("world", world);
            } catch (Exception ignored) {}

            try {
                Object entity = engineApi.getClass().getMethod("entity").invoke(engineApi);
                if (entity != null) b.putMember("entity", entity);
            } catch (Exception ignored) {}

        }
    }

    /** Optional: remove globals (not strictly required). */
    public void clearGlobals() {
        var b = ctx.getBindings("js");
        try { b.removeMember("ctx"); } catch (Exception ignored) {}
        try { b.removeMember("engine"); } catch (Exception ignored) {}
        try { b.removeMember("render"); } catch (Exception ignored) {}
        try { b.removeMember("world"); } catch (Exception ignored) {}
    }


    /**
     * Invalidate all cached modules under prefix (e.g. "Scripts/").
     */
    public void invalidatePrefix(String prefix) {
        if (prefix == null) return;
        String p = prefix.replace('\\', '/');
        moduleCache.keySet().removeIf(k -> k.startsWith(p));
    }

    /**
     * Optional diagnostics.
     */
    public int cachedModules() {
        return moduleCache.size();
    }


    @Override
    public void close() {
        log.info("Closing GraalScriptRuntime");
        moduleCache.clear();
        ctx.close(true);
    }
}