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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GraalScriptRuntime (enterprise-hardened)
 *
 * - CommonJS-like require() with cache
 * - cycle-safe partial exports during LOADING
 * - Hot reload invalidation (exact, many, prefix) + per-module version counter
 * - bindGlobals(): no per-call reflection (cached MethodHandles per engine API class)
 *
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
    private volatile ModuleSourceProvider loader;

    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();

    // Hot-reload versions: moduleId -> incrementing version
    private final Map<String, AtomicLong> moduleVersions = new ConcurrentHashMap<>();

    private enum State { LOADING, LOADED }

    private static final class ModuleRecord {
        final Value moduleObj;   // { exports: ... }
        final Value exportsObj;  // exports object
        volatile State state = State.LOADING;

        ModuleRecord(Value moduleObj, Value exportsObj) {
            this.moduleObj = moduleObj;
            this.exportsObj = exportsObj;
        }
    }

    // -----------------------------
    // bindGlobals() fast accessors (cached per API class)
    // -----------------------------

    private static final class ApiAccessors {
        final MethodHandle render0;
        final MethodHandle world0;
        final MethodHandle entity0;

        ApiAccessors(MethodHandle render0, MethodHandle world0, MethodHandle entity0) {
            this.render0 = render0;
            this.world0 = world0;
            this.entity0 = entity0;
        }
    }

    private static final Map<Class<?>, ApiAccessors> ACCESSORS = new ConcurrentHashMap<>();

    private static ApiAccessors accessorsFor(Class<?> apiClass) {
        return ACCESSORS.computeIfAbsent(apiClass, GraalScriptRuntime::buildAccessors);
    }

    private static ApiAccessors buildAccessors(Class<?> apiClass) {
        MethodHandle render = null;
        MethodHandle world  = null;
        MethodHandle entity = null;

        // We allow ANY return type; we resolve method once via reflection,
        // then unreflect into MethodHandle for fast calls later.
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        render = unreflectNoArgIfExists(lookup, apiClass, "render");
        world  = unreflectNoArgIfExists(lookup, apiClass, "world");
        entity = unreflectNoArgIfExists(lookup, apiClass, "entity");

        return new ApiAccessors(render, world, entity);
    }

    private static MethodHandle unreflectNoArgIfExists(MethodHandles.Lookup lookup, Class<?> cls, String name) {
        try {
            var m = cls.getMethod(name); // public, 0-arg
            if (m.getParameterCount() != 0) return null;
            return lookup.unreflect(m);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException e) {
            log.debug("bindGlobals: cannot access {}.{}(): {}", cls.getName(), name, e.toString());
            return null;
        } catch (Throwable t) {
            log.debug("bindGlobals: failed to resolve {}.{}(): {}", cls.getName(), name, t.toString());
            return null;
        }
    }

    private static Object safeInvoke(MethodHandle mh, Object target, String nameForLog) {
        if (mh == null || target == null) return null;
        try {
            return mh.invoke(target);
        } catch (Throwable t) {
            log.warn("bindGlobals: {}() invoke failed on {}", nameForLog, target.getClass().getName(), t);
            return null;
        }
    }

    // -----------------------------
    // Runtime
    // -----------------------------

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
            if (args.length < 2) throw new IllegalArgumentException("__kt_require(parentId, request) expected");
            String parentId = args[0].isNull() ? "" : args[0].asString();
            String request = args[1].asString();
            return requireFrom(parentId, request);
        });

        log.info("GraalJS context created (require enabled)");
    }

    public void setModuleSourceProvider(ModuleSourceProvider provider) {
        this.loader = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Public host-side require:
     * - uses CommonJS cache
     * - moduleId is resolved as absolute id (no parent)
     */
    public Value require(String moduleId) {
        return requireFrom("", normalizeId(moduleId));
    }

    /**
     * Current hot-reload version for module id.
     * Increases each time invalidate()/invalidateMany()/invalidatePrefix() touches it.
     */
    public long moduleVersion(String moduleId) {
        AtomicLong v = moduleVersions.get(normalizeId(moduleId));
        return v == null ? 0L : v.get();
    }

    // -----------------------------
    // CommonJS core
    // -----------------------------

    private Value requireFrom(String parentId, String request) {
        final String moduleId = resolveModuleId(parentId, request);

        ModuleRecord existing = moduleCache.get(moduleId);
        if (existing != null) {
            return existing.moduleObj.getMember("exports");
        }

        // Create cycle-safe placeholders
        Value exportsObj = ctx.eval("js", "({})");
        Value moduleObj  = ctx.eval("js", "({})");
        moduleObj.putMember("exports", exportsObj);

        ModuleRecord rec = new ModuleRecord(moduleObj, exportsObj);
        moduleCache.put(moduleId, rec);

        String code;
        try {
            ModuleSourceProvider l = loader;
            if (l == null) {
                throw new IllegalStateException(
                        "require() is used but no ModuleSourceProvider is set on GraalScriptRuntime. " +
                                "Call runtime.setModuleSourceProvider(id -> assetManager.loadAsset(id))");
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

    private void evalCommonJsInto(String moduleId, String code, ModuleRecord rec) throws Exception {
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

        ProxyExecutable requireFn = args -> {
            if (args.length < 1) throw new IllegalArgumentException("require(request) expected");
            String req = args[0].asString();
            Value hostRequire = ctx.getBindings("js").getMember("__kt_require");
            return hostRequire.execute(moduleId, req);
        };

        fn.execute(rec.moduleObj, rec.exportsObj, requireFn, moduleId, dir);

        Value ex = rec.moduleObj.getMember("exports");
        if (ex == null || ex.isNull()) {
            rec.moduleObj.putMember("exports", ctx.eval("js", "({})"));
        }
    }

    // -----------------------------
    // Hot reload invalidation
    // -----------------------------

    /**
     * Hot reload support: invalidate a specific module id from CommonJS cache.
     * Next require(moduleId) will reload source.
     *
     * Also increments moduleVersion(moduleId).
     */
    public void invalidate(String moduleId) {
        String id = normalizeId(moduleId);
        moduleCache.remove(id);
        bumpVersion(id);
    }

    /**
     * Invalidate many exact ids.
     * Returns number of modules actually removed from cache (best-effort).
     */
    public int invalidateMany(Collection<String> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) return 0;
        int removed = 0;
        for (String raw : moduleIds) {
            if (raw == null) continue;
            String id = normalizeId(raw);
            if (moduleCache.remove(id) != null) removed++;
            bumpVersion(id);
        }
        return removed;
    }

    /**
     * Invalidate by prefix: removes all cached modules whose id starts with prefix.
     * Also bumps versions for removed entries.
     */
    public int invalidatePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return 0;
        String p = normalizeId(prefix);
        int removed = 0;

        // Snapshot keys to avoid concurrent modification edge-cases
        for (String k : new ArrayList<>(moduleCache.keySet())) {
            if (k.startsWith(p)) {
                if (moduleCache.remove(k) != null) removed++;
                bumpVersion(k);
            }
        }
        return removed;
    }

    private void bumpVersion(String moduleId) {
        moduleVersions.computeIfAbsent(moduleId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    public int cachedModules() {
        return moduleCache.size();
    }

    // -----------------------------
    // Globals binding (fast + predictable)
    // -----------------------------

    /**
     * Bind stable globals for scripts:
     * - ctx     : SystemContext (or any context object you pass)
     * - engine  : EngineApi
     * - render  : engine.render() if present
     * - world   : engine.world()  if present
     * - entity  : engine.entity() if present
     *
     * Safe to call on every rebuild; overwrites previous bindings.
     */
    public void bindGlobals(Object systemContext, Object engineApi) {
        var b = ctx.getBindings("js");

        if (systemContext != null) b.putMember("ctx", systemContext);

        if (engineApi != null) {
            b.putMember("engine", engineApi);

            ApiAccessors acc = accessorsFor(engineApi.getClass());

            Object render = safeInvoke(acc.render0, engineApi, "render");
            if (render != null) b.putMember("render", render);

            Object world = safeInvoke(acc.world0, engineApi, "world");
            if (world != null) b.putMember("world", world);

            Object entity = safeInvoke(acc.entity0, engineApi, "entity");
            if (entity != null) b.putMember("entity", entity);
        }
    }

    /** Optional: remove globals (not strictly required). */
    public void clearGlobals() {
        var b = ctx.getBindings("js");
        try { b.removeMember("ctx"); } catch (Exception ignored) {}
        try { b.removeMember("engine"); } catch (Exception ignored) {}
        try { b.removeMember("render"); } catch (Exception ignored) {}
        try { b.removeMember("world"); } catch (Exception ignored) {}
        try { b.removeMember("entity"); } catch (Exception ignored) {}
    }

    // -----------------------------
    // Path resolution helpers
    // -----------------------------

    private static String normalizeId(String moduleId) {
        String s = (moduleId == null) ? "" : moduleId.trim();
        s = s.replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        return s;
    }

    private static String resolveModuleId(String parentId, String request) {
        String req = normalizeId(request);
        if (req.isEmpty()) throw new IllegalArgumentException("require('') is not allowed");

        boolean isRelative = req.startsWith("./") || req.startsWith("../");
        String base = isRelative ? dirnameOf(normalizeId(parentId)) : "";
        String resolved;

        if (isRelative) {
            Path p = Path.of(base.isEmpty() ? "." : base).resolve(req).normalize();
            resolved = p.toString().replace('\\', '/');
        } else {
            resolved = req;
        }

        if (resolved.endsWith("/")) resolved = resolved + "index.js";
        if (!hasExtension(resolved)) resolved = resolved + ".js";

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
    // Close
    // -----------------------------

    @Override
    public void close() {
        log.info("Closing GraalScriptRuntime");
        moduleCache.clear();
        moduleVersions.clear();
        ctx.close(true);
    }
}