package org.foxesworld.kalitech.engine.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.resolve.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import org.foxesworld.kalitech.engine.script.cache.ScriptCaches;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GraalScriptRuntime is responsible for loading and executing JavaScript modules
 * inside a GraalVM {@link Context}. It provides a CommonJS‐style {@code require}
 * implementation, manages module caching, supports hot reloading and tracks
 * dependencies to allow transitive invalidation when a module changes. Only the
 * first thread that touches this runtime becomes the owner; all subsequent
 * interactions must occur on the same thread. Violating this contract will
 * result in an {@link IllegalStateException}.
 *
 * <p>The runtime deliberately disallows arbitrary host class lookup and limits
 * host access to members annotated with {@link HostAccess.Export}. This helps
 * sandbox scripts and prevent accidental or malicious access to the JVM.</p>
 */
public final class GraalScriptRuntime implements Closeable {

    private static final Logger log = LogManager.getLogger(GraalScriptRuntime.class);

    /**
     * A simple source provider capable of returning module source code given a
     * module identifier. Implementations should return the raw contents of a
     * JavaScript file (without any wrapping) or {@code null} if the module does
     * not exist. Throwing from {@link #loadText(String)} is interpreted as a
     * terminal failure and will propagate to the caller.
     */
    @FunctionalInterface
    public interface ModuleSourceProvider {
        String loadText(String moduleId) throws Exception;
    }

    /**
     * The polyglot context used to execute JavaScript. Created once during
     * construction and closed when {@link #close()} is called. The context is
     * configured to deny host class lookup and only allow access to exported
     * members. Experimental options are enabled to support ECMAScript features
     * such as top level await and other modern syntax.
     */
    private final Context ctx;

    /** Runtime caches (module text, wrapped sources, path resolution, etc.). */
    private final ScriptCaches caches;

    /**
     * The first thread that interacts with this runtime becomes the owner.
     * GraalVM contexts are thread confined; using them across threads without
     * explicit configuration is unsafe. We enforce this contract.
     */
    private volatile Thread ownerThread;

    /** External source loader used to fetch module text. */
    private volatile ModuleSourceProvider loader;

    /**
     * Per-module exports cache (CommonJS). This is NOT replaced by Caffeine:
     * it is the "source of truth" for loaded modules and cyclic deps.
     */
    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();

    /**
     * Version counter per module id. Bumped every time a module is invalidated.
     */
    private final Map<String, AtomicLong> moduleVersions = new ConcurrentHashMap<>();

    // graph of module dependencies. When a module A requires module B, we
    // record A -> B in forwardDeps, and B -> A in reverseDeps. This allows us
    // to invalidate transitive dependents when a module changes.
    private final Map<String, Set<String>> forwardDeps = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();

    // global require function injected into the context.
    private final Value requireFn;

    // job queue (used by ScriptSubsystem etc).
    private final ScriptJobQueue jobs = new ScriptJobQueue();

    // optional: allow scripts to call host functions by MethodHandle, pre-bound.
    private final Map<String, MethodHandle> hostHandles = new ConcurrentHashMap<>();
    private final ResolverChain resolver;


    /**
     * Creates a runtime with default Caffeine caches.
     */
    public GraalScriptRuntime() {
        this(ScriptCaches.defaults());
    }

    /**
     * Creates a runtime with explicit caches (useful for tuning/testing).
     */
    public GraalScriptRuntime(ScriptCaches caches) {
        this.caches = Objects.requireNonNull(caches, "caches");
        this.resolver = new ResolverChain()
                .add(new RelativeResolver())
                .add(new NamespaceResolver("Mods"))
                .add(new AliasResolver(Map.of(
                        "@core", "Scripts/core",
                        "@lib", "Scripts/lib",
                        "@engine", "Scripts/engine"
                )))
                .add(new PassThroughResolver());

        this.ctx = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .allowHostAccess(HostAccess.newBuilder(HostAccess.NONE)
                        .allowAccessAnnotatedBy(HostAccess.Export.class)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowAllAccess(false)
                .build();

        // install global require() dispatcher (commonjs)
        ProxyExecutable req = args -> {
            String request = args.length > 0 ? args[0].asString() : "";
            return requireFrom("", normalizeId(request));
        };
        this.requireFn = ctx.asValue(req);
        ctx.getBindings("js").putMember("require", requireFn);

        // Touch owner thread now to avoid surprises later.
        assertOwnerThread();
    }

    public GraalScriptRuntime setModuleSourceProvider(ModuleSourceProvider loader) {
        this.loader = loader;
        return this;
    }

    public ScriptJobQueue jobs() {
        return jobs;
    }

    public Context ctx() {
        return ctx;
    }

    // ---------------------------------------------------------------------
    // Thread ownership
    // ---------------------------------------------------------------------

    private void assertOwnerThread() {
        Thread t = Thread.currentThread();
        Thread owner = ownerThread;
        if (owner == null) {
            ownerThread = t;
            return;
        }
        if (owner != t) {
            throw new IllegalStateException("GraalScriptRuntime is thread confined. Owner=" + owner.getName()
                    + ", current=" + t.getName());
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Requires a module by its absolute (normalized) identifier. See
     * {@link #requireFrom(String, String)} for details. Throws if no
     * {@link ModuleSourceProvider} is set or the module fails to load.
     *
     * @param moduleId the absolute module identifier
     * @return the exports object of the required module
     */
    public Value require(String moduleId) {
        assertOwnerThread();
        return requireFrom("", normalizeId(moduleId));
    }

    /**
     * Returns the current hot reload version for a module. When a module is
     * invalidated via {@link #invalidate(String)}, {@link #invalidateMany(Collection)}
     * or {@link #invalidatePrefix(String)}, its version counter is incremented.
     *
     * @param moduleId the module identifier
     * @return the version counter, or 0 if the module has never been loaded
     */
    public long moduleVersion(String moduleId) {
        AtomicLong v = moduleVersions.get(normalizeId(moduleId));
        return v == null ? 0L : v.get();
    }

    // ---------------------------------------------------------------------
    // Core module loader
    // ---------------------------------------------------------------------

    /**
     * Resolves a CommonJS require() request relative to the parent module id.
     * IMPORTANT: must see "./" and "../" BEFORE normalizeId() strips "./".
     *
     * Examples:
     *  parent="Scripts/main.js", request="./world/main.world.js" -> "Scripts/world/main.world.js"
     *  parent="Scripts/systems/scene.system.js", request="../lib/math.js" -> "Scripts/lib/math.js"
     *  request="Scripts/world/main.world.js" -> "Scripts/world/main.world.js"
     *  request="/Scripts/world/main.world.js" -> "Scripts/world/main.world.js"
     */
    private static String resolveRequest(String parentModuleId, String requestRaw) {
        if (requestRaw == null) return "";
        String req = requestRaw.trim().replace('\\', '/');

        // absolute-ish
        if (req.startsWith("/")) req = req.substring(1);

        // relative
        if (req.startsWith("./") || req.startsWith("../")) {
            String parentDir = dirnameOf(parentModuleId);
            Deque<String> parts = new ArrayDeque<>();
            if (!parentDir.isEmpty()) {
                for (String p : parentDir.split("/")) {
                    if (!p.isEmpty()) parts.addLast(p);
                }
            }

            for (String p : req.split("/")) {
                if (p.isEmpty() || ".".equals(p)) continue;
                if ("..".equals(p)) {
                    if (!parts.isEmpty()) parts.removeLast();
                } else {
                    parts.addLast(p);
                }
            }

            StringBuilder sb = new StringBuilder();
            Iterator<String> it = parts.iterator();
            while (it.hasNext()) {
                sb.append(it.next());
                if (it.hasNext()) sb.append('/');
            }
            return normalizeId(sb.toString());
        }

        // Non-relative: "Scripts/..." or "world/..." (package-style) -> just normalize
        // NOTE: if you want "world/..." to resolve against "Scripts/", that's a separate policy
        return normalizeId(req);
    }

    private static String normalizeId(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.trim().replace('\\', '/');

        // strip leading "./"
        while (id.startsWith("./")) id = id.substring(2);

        // strip leading "/"
        while (id.startsWith("/")) id = id.substring(1);

        // collapse // and remove trailing /
        id = id.replaceAll("/{2,}", "/");
        if (id.endsWith("/")) id = id.substring(0, id.length() - 1);

        return id;
    }

    private static String dirnameOf(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.replace('\\', '/');
        int idx = id.lastIndexOf('/');
        return idx < 0 ? "" : id.substring(0, idx);
    }

    /**
     * CommonJS require() implementation that resolves relative requests,
     * returns cached exports where possible, supports cyclic deps and records
     * dependency edges for hot reload invalidation.
     *
     * @param parentModuleId module that calls require (can be empty for root)
     * @return exports object for the required module
     */
    private Value requireFrom(String parentModuleId, String requestModuleIdOrRaw) {
        assertOwnerThread();

        // Determine "resolved" module id for cache/load & for diagnostics
        final String parent = normalizeId(parentModuleId);
        final String requestRaw = (requestModuleIdOrRaw == null) ? "" : requestModuleIdOrRaw;

        // If caller already passed a normalized absolute id, resolveToModuleId will keep it stable.
        final String moduleId = resolveToModuleId(parent, requestRaw);

        // Fast path: already loaded
        ModuleRecord existing = moduleCache.get(moduleId);
        if (existing != null) {
            return existing.exportsObj;
        }

        // Create record BEFORE evaluation to support cyclic dependencies.
        ModuleRecord rec = new ModuleRecord(moduleId, ctx);
        moduleCache.put(moduleId, rec);

        // Load source
        String code;
        try {
            ModuleSourceProvider l = this.loader;
            if (l == null) {
                moduleCache.remove(moduleId);
                String msg =
                        "require() called but no ModuleSourceProvider is set. " +
                                "resolved='" + moduleId + "', parent='" + parent + "', request='" + requestRaw + "'";
                log.error("[script] {}", msg);
                throw new IllegalStateException(msg);
            }

            code = caches.moduleText().get(moduleId, id -> {
                try {
                    return l.loadText(id);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            if (code == null) {
                moduleCache.remove(moduleId);
                String msg =
                        "ModuleSourceProvider returned null. " +
                                "resolved='" + moduleId + "', parent='" + parent + "', request='" + requestRaw + "'";
                log.error("[script] {}", msg);
                throw new IllegalStateException(msg);
            }

        } catch (RuntimeException re) {
            // Unwrap loader exceptions wrapped for Caffeine.
            Throwable cause = re.getCause();
            moduleCache.remove(moduleId);

            String base =
                    "Failed to load module source. " +
                            "resolved='" + moduleId + "', parent='" + parent + "', request='" + requestRaw + "'";

            if (cause != null) {
                log.error("[script] {} cause={}", base, cause.toString());
                throw new RuntimeException(base, cause);
            }

            log.error("[script] {}", base, re);
            throw re;

        } catch (Exception e) {
            moduleCache.remove(moduleId);
            String msg =
                    "Failed to load module source. " +
                            "resolved='" + moduleId + "', parent='" + parent + "', request='" + requestRaw + "'";
            log.error("[script] {}", msg, e);
            throw new RuntimeException(msg, e);
        }

        try {
            evalCommonJsInto(moduleId, code, rec);
            rec.loaded = true;
            return rec.exportsObj;
        } catch (Exception e) {
            moduleCache.remove(moduleId);
            String msg =
                    "Failed to evaluate module. " +
                            "resolved='" + moduleId + "', parent='" + parent + "', request='" + requestRaw + "'";
            log.error("[script] {}", msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * Resolves a require request to a final module id used for loading/caching.
     * - applies relative resolution (./ ../) against parent
     * - then runs ResolverChain (aliases/namespaces/etc)
     * - finally normalizes id for cache keys
     *
     * IMPORTANT: this method is used for error reporting too.
     */
    private String resolveToModuleId(String parentModuleId, String requestRaw) {
        String rawResolved = resolveRequest(parentModuleId, requestRaw); // handles ./../ and trims
        String afterChain;
        try {
            // If your ResolverChain API differs, adapt here.
            // The intention: translate package-style (@core/..) / namespace (kalitech:ui) / etc.
            afterChain = resolver.resolveOrThrow(parentModuleId, rawResolved);
        } catch (Throwable t) {
            // If resolver fails, still keep a usable id for diagnostics and fallback.
            afterChain = rawResolved;
        }
        return normalizeId(afterChain);
    }


    /**
     * Evaluates a CommonJS module: wraps the module code in a function, executes it
     * with (module, exports, require, __filename, __dirname), and stores results.
     *
     * @param moduleId module id
     * @param code     raw JavaScript source code for the module
     * @param rec      the ModuleRecord corresponding to the module being loaded
     * @throws Exception if evaluation of the module fails
     */
    private void evalCommonJsInto(String moduleId, String code, ModuleRecord rec) {
        String wrapped = caches.wrappedCode().get(
                ScriptCaches.SourceKey.of(moduleId, code),
                k -> "(function(module, exports, require, __filename, __dirname) {\n" +
                        "  'use strict';\n" +
                        code + "\n" +
                        "})"
        );

        ScriptCaches.SourceKey sk = ScriptCaches.SourceKey.of(moduleId, wrapped);
        Source src = caches.wrappedSources().get(sk, k -> {
            try {
                return Source.newBuilder("js", wrapped, moduleId).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Value fn = ctx.eval(src);

        Value moduleObj = rec.moduleObj;
        Value exportsObj = rec.exportsObj;

        // Local require resolves ids relative to the current module and records
        // dependency edges. It delegates to requireFrom() which will return
        // cached exports or load the module if necessary.
        ProxyExecutable localReq = args -> {
            String request = args.length > 0 ? args[0].asString() : "";
            String resolved = resolveRequest(moduleId, request); // raw request
            recordDependency(moduleId, resolved);
            return requireFrom(moduleId, resolved);
        };
        Value localRequire = ctx.asValue(localReq);

        String __filename = moduleId;
        String __dirname = dirnameOf(moduleId);

        fn.execute(moduleObj, exportsObj, localRequire, __filename, __dirname);

        // CommonJS: module.exports can be overwritten
        Value moduleExports = moduleObj.getMember("exports");
        if (moduleExports != null) {
            rec.exportsObj = moduleExports;
        }
    }

    // ---------------------------------------------------------------------
    // Dependency tracking
    // ---------------------------------------------------------------------

    private void recordDependency(String parent, String child) {
        if (parent == null || parent.isEmpty()) return;
        if (child == null || child.isEmpty()) return;

        String p = normalizeId(parent);
        String c = normalizeId(child);
        if (p.equals(c)) return;

        forwardDeps.computeIfAbsent(p, k -> ConcurrentHashMap.newKeySet()).add(c);
        reverseDeps.computeIfAbsent(c, k -> ConcurrentHashMap.newKeySet()).add(p);
    }

    // ---------------------------------------------------------------------
    // Invalidation / Hot Reload
    // ---------------------------------------------------------------------

    public boolean invalidate(String moduleId) {
        return invalidateWithReason(moduleId, "invalidate");
    }

    public int invalidateMany(Collection<String> moduleIds) {
        return invalidateManyWithReason(moduleIds, "invalidateMany");
    }

    public int invalidatePrefix(String prefix) {
        return invalidatePrefixWithReason(prefix, "invalidatePrefix");
    }

    public boolean invalidateWithReason(String moduleId, String reason) {
        assertOwnerThread();
        String id = normalizeId(moduleId);
        if (id.isEmpty()) return false;
        int removed = removeModuleAndDependents(id, new HashSet<>());
        if (removed > 0) {
            log.debug("[script] invalidate '{}' removed={} reason={}", id, removed, reason);
            return true;
        }
        return false;
    }

    public int invalidateManyWithReason(Collection<String> moduleIds, String reason) {
        assertOwnerThread();
        if (moduleIds == null || moduleIds.isEmpty()) return 0;
        int total = 0;
        Set<String> visited = new HashSet<>();
        for (String id0 : moduleIds) {
            String id = normalizeId(id0);
            if (id.isEmpty()) continue;
            total += removeModuleAndDependents(id, visited);
        }
        if (total > 0) {
            log.debug("[script] invalidateMany removed={} reason={}", total, reason);
        }
        return total;
    }

    public int invalidatePrefixWithReason(String prefix, String reason) {
        assertOwnerThread();
        String p = normalizeId(prefix);
        if (p.isEmpty()) return 0;

        List<String> keys = new ArrayList<>(moduleCache.keySet());
        int total = 0;
        Set<String> visited = new HashSet<>();
        for (String id : keys) {
            if (id.startsWith(p)) {
                total += removeModuleAndDependents(id, visited);
            }
        }

        if (total > 0) {
            log.debug("[script] invalidatePrefix '{}' removed={} reason={}", p, total, reason);
        }
        return total;
    }

    private int removeModuleAndDependents(String id, Set<String> visited) {
        if (!visited.add(id)) return 0;
        int removed = 0;

        if (moduleCache.remove(id) != null) {
            removed++;
            caches.invalidateModule(id);
            bumpVersion(id);
        }

        Set<String> dependents = reverseDeps.remove(id);
        if (dependents != null) {
            for (String dep : dependents) {
                removed += removeModuleAndDependents(dep, visited);
            }
        }

        // Clean up forward dependencies: remove this id from any deps sets
        Set<String> deps = forwardDeps.remove(id);
        if (deps != null) {
            for (String d : deps) {
                Set<String> rev = reverseDeps.get(d);
                if (rev != null) {
                    rev.remove(id);
                    if (rev.isEmpty()) {
                        reverseDeps.remove(d);
                    }
                }
            }
        }

        return removed;
    }

    private void bumpVersion(String moduleId) {
        moduleVersions.computeIfAbsent(moduleId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    // ---------------------------------------------------------------------
    // Host handles (optional)
    // ---------------------------------------------------------------------

    public GraalScriptRuntime registerHostHandle(String name, MethodHandle handle) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handle, "handle");
        hostHandles.put(name, handle);
        return this;
    }

    public MethodHandle hostHandle(String name) {
        return hostHandles.get(name);
    }

    // ---------------------------------------------------------------------
    // Reset / Close
    // ---------------------------------------------------------------------

    public void reset() {
        assertOwnerThread();
        moduleCache.clear();
        forwardDeps.clear();
        reverseDeps.clear();
        jobs.clear();
        caches.invalidateAll();
    }

    @Override
    public void close() {
        log.info("Closing GraalScriptRuntime");
        try {
            reset();
        } catch (Exception e) {
            log.warn("Error during GraalScriptRuntime reset", e);
        }
        try {
            ctx.close(true);
        } catch (Exception e) {
            log.warn("Error closing Graal context", e);
        }
    }

    // ---------------------------------------------------------------------
    // Internal module record
    // ---------------------------------------------------------------------

    private static final class ModuleRecord {
        final String id;
        boolean loaded;

        Value moduleObj;
        Value exportsObj;

        ModuleRecord(String id, Context ctx) {
            this.id = id;
            this.loaded = false;
            this.moduleObj = ctx.eval("js", "({ exports: {} })");
            this.exportsObj = moduleObj.getMember("exports");
        }
    }
}