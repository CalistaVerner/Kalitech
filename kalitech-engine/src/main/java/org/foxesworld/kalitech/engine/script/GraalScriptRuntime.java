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
import java.nio.file.Paths;
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

    /**
     * The first thread that interacts with this runtime becomes the owner.
     * GraalVM contexts are thread confined; using them from multiple threads
     * concurrently is undefined. To avoid subtle bugs, we enforce that all
     * externally visible methods check this invariant via
     * {@link #assertOwnerThread()}.
     */
    private volatile Thread ownerThread;

    /**
     * The module source provider used to load text for modules. Can be swapped
     * at runtime (for example, to hot reload from a new asset manager) but may
     * not be {@code null} when invoking {@link #require(String)}. Reads and
     * writes to this field are volatile to avoid races when the provider is
     * swapped on the owner thread and subsequently read on that same thread.
     */
    private volatile ModuleSourceProvider loader;

    /**
     * Stores loaded modules keyed by their normalized identifier. Each record
     * holds the module object and its exports. The map is a
     * {@link ConcurrentHashMap} to support concurrent reads in future if
     * requireFrom is ever accessed from multiple threads. Writes occur on the
     * owner thread only.
     */
    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();

    /**
     * Tracks incremental hot reload versions for each module. When a module is
     * invalidated, its version counter is incremented. Callers can use
     * {@link #moduleVersion(String)} to determine if a module has been
     * reloaded since it was last observed.
     */
    private final Map<String, AtomicLong> moduleVersions = new ConcurrentHashMap<>();

    /**
     * State of a module during loading. While LOADING, require() will return
     * the exports object immediately, enabling cycle safety. Once evaluation
     * completes successfully, the state transitions to LOADED.
     */
    private enum State { LOADING, LOADED }

    /**
     * ModuleRecord holds the module and exports objects for a module. These
     * {@link Value} instances originate from the polyglot context and must not
     * be accessed from multiple threads.
     */
    private static final class ModuleRecord {
        final Value moduleObj;
        final Value exportsObj;
        volatile State state = State.LOADING;

        ModuleRecord(Value moduleObj, Value exportsObj) {
            this.moduleObj = moduleObj;
            this.exportsObj = exportsObj;
        }
    }

    // ---------------------------------------------------------------------
    // API accessor caching
    //
    // For APIs exposed to JavaScript we reflectively invoke certain no-arg
    // methods (render, world, entity). To avoid repeated reflective lookups
    // per invocation we cache MethodHandles per API class. The caching
    // structures are thread safe to support concurrent API uses in future.
    // ---------------------------------------------------------------------

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

    /**
     * Caches accessors for API classes. Keyed by the API class itself. A
     * {@link ConcurrentHashMap} ensures thread safety when multiple threads
     * reflect on the same API concurrently.
     */
    private static final Map<Class<?>, ApiAccessors> accessorCache = new ConcurrentHashMap<>();

    /**
     * Returns cached accessors for the given API class, computing them on
     * demand if absent. Uses {@link MethodHandles#publicLookup()} to reflect
     * public no-arg methods named render, world and entity. Nonexistent or
     * inaccessible methods will result in {@code null} handles.
     */
    private static ApiAccessors accessorsFor(Class<?> apiClass) {
        return accessorCache.computeIfAbsent(apiClass, GraalScriptRuntime::resolveAccessors);
    }

    private static ApiAccessors resolveAccessors(Class<?> apiClass) {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle render = unreflectNoArgIfExists(lookup, apiClass, "render");
        MethodHandle world  = unreflectNoArgIfExists(lookup, apiClass, "world");
        MethodHandle entity = unreflectNoArgIfExists(lookup, apiClass, "entity");
        return new ApiAccessors(render, world, entity);
    }

    private static MethodHandle unreflectNoArgIfExists(MethodHandles.Lookup lookup, Class<?> cls, String name) {
        try {
            var m = cls.getMethod(name);
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

    /**
     * Invokes a MethodHandle on a given target and returns the result. Any
     * exception thrown during invocation is logged and {@code null} is
     * returned. This ensures that binding globals does not fail entirely if
     * optional API methods throw.
     */
    private static Object safeInvoke(MethodHandle mh, Object target, String nameForLog) {
        if (mh == null || target == null) return null;
        try {
            return mh.invoke(target);
        } catch (Throwable t) {
            log.warn("bindGlobals: {}() invoke failed on {}", nameForLog, target.getClass().getName(), t);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Dependency tracking
    //
    // To support transitive invalidation on hot reload, we track the directed
    // graph of module dependencies. When a module A requires module B, we
    // record that A depends on B. Invalidating B will also invalidate A and
    // any other modules that (directly or indirectly) depend on B. The graph
    // maps each module to the set of its immediate dependencies (deps) and
    // each module to the set of modules that depend on it (reverseDeps).
    // ---------------------------------------------------------------------

    /**
     * Maps a module identifier to the modules it depends on. The sets are
     * thread safe to allow concurrent reads when querying dependencies. Only
     * the owner thread mutates these structures.
     */
    private final Map<String, Set<String>> deps = new ConcurrentHashMap<>();

    /**
     * Maps a module identifier to the modules that depend on it. Used to
     * propagate invalidations when a module changes.
     */
    private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();

    /**
     * Records that {@code parent} depends on {@code child}. Self dependencies
     * are ignored. Both module identifiers should be normalized prior to
     * calling this method.
     */
    private void recordDependency(String parent, String child) {
        if (parent == null || child == null) return;
        if (parent.isEmpty() || child.isEmpty()) return;
        if (parent.equals(child)) return;
        deps.computeIfAbsent(parent, k -> ConcurrentHashMap.newKeySet()).add(child);
        reverseDeps.computeIfAbsent(child, k -> ConcurrentHashMap.newKeySet()).add(parent);
    }

    // ---------------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------------

    /**
     * Creates a new GraalScriptRuntime with default configuration. The
     * constructed context enables experimental options (to support modern
     * ECMAScript features), disables host class lookup, denies full host
     * access and only allows access to members annotated with
     * {@link HostAccess.Export}. The runtime also installs a global
     * {@code require} function on the JavaScript side which dispatches to
     * {@link #requireFrom(String, String)}. Callers must set a
     * {@link ModuleSourceProvider} via {@link #setModuleSourceProvider(ModuleSourceProvider)}
     * before requiring modules.
     */
    public GraalScriptRuntime() {
        this.ctx = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .allowHostAccess(HostAccess.newBuilder(HostAccess.NONE)
                        .allowAccessAnnotatedBy(HostAccess.Export.class)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowAllAccess(false)
                .build();

        // Provide global require() for scripts. It resolves module identifiers
        // relative to the current module by delegating to requireFrom("", id).
        ctx.getBindings("js").putMember("require", (ProxyExecutable) args -> {
            String id = args.length > 0 ? args[0].asString() : "";
            return requireFrom("", normalizeId(id));
        });
    }

    /**
     * Assigns a new module source provider. The provider may be swapped at
     * runtime, but callers should ensure that there are no concurrent module
     * loads happening on the owner thread when this is called. Passing
     * {@code null} is permitted; however, requiring a module when no provider
     * is set will result in an {@link IllegalStateException}.
     */
    public void setModuleSourceProvider(ModuleSourceProvider loader) {
        this.loader = loader;
    }

    // ---------------------------------------------------------------------
    // Ownership
    // ---------------------------------------------------------------------

    /**
     * Asserts that the current thread is the owner of this runtime. The first
     * thread to interact with this runtime becomes the owner. Subsequent
     * invocations on other threads will throw an IllegalStateException. This
     * method should be called at the start of all externally visible
     * operations to enforce the single-thread confinement of the underlying
     * context.
     */
    private void assertOwnerThread() {
        Thread current = Thread.currentThread();
        Thread o = ownerThread;
        if (o == null) {
            ownerThread = current;
            return;
        }
        if (o != current) {
            throw new IllegalStateException(
                    "GraalScriptRuntime used from different thread. owner=" + o.getName() + ", current=" + current.getName()
            );
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
     * Requires a module relative to a parent module. The parent module’s
     * identifier is used only for resolution of relative identifiers and does
     * not participate in dependency tracking; dependency edges are recorded
     * via {@link #recordDependency(String, String)} when localRequire is
     * invoked from within a module evaluation. See {@link #evalCommonJsInto(String, String, ModuleRecord)}.
     *
     * <p>When invoked directly from Java (for example, from {@link #require(String)})
     * the parent should be the empty string to indicate an absolute require.</p>
     *
     * <p>During module loading this method does not assert the owner thread to
     * support the nested calls made by the global JavaScript require() function.
     * Callers invoking this method directly must enforce the single-thread
     * constraint by calling {@link #assertOwnerThread()} beforehand.</p>
     *
     * @param parent    the parent module identifier (empty for absolute requires)
     * @param moduleId  the normalized module identifier to load
     * @return the exports object of the loaded module
     */
    private Value requireFrom(String parent, String moduleId) {
        // Fast path: return cached module if present. If a module is in
        // LOADING state we still return its exports to allow circular
        // dependencies. We intentionally do not assert ownership here; see
        // method javadoc.
        ModuleRecord existing = moduleCache.get(moduleId);
        if (existing != null) {
            return existing.moduleObj.getMember("exports");
        }

        // Create new module record with empty exports. Place it into the cache
        // before evaluation to support cyclic dependencies.
        Value moduleObj = ctx.eval("js", "({ exports: {} })");
        Value exportsObj = moduleObj.getMember("exports");
        ModuleRecord rec = new ModuleRecord(moduleObj, exportsObj);
        moduleCache.put(moduleId, rec);

        // Load module source from the provider. Use a local reference to
        // prevent races when the provider is swapped. If no provider is set
        // throw immediately.
        String code;
        try {
            ModuleSourceProvider l = this.loader;
            if (l == null) {
                throw new IllegalStateException(
                        "require() called but no ModuleSourceProvider is set on GraalScriptRuntime. " +
                                "Call setModuleSourceProvider() before requiring modules."
                );
            }
            code = l.loadText(moduleId);
            if (code == null) {
                throw new IllegalStateException("ModuleSourceProvider returned null for " + moduleId);
            }
        } catch (Exception e) {
            // Remove the half-created module record and propagate the error.
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

    /**
     * Wraps and evaluates CommonJS module code into a function and executes it.
     * This method is responsible for binding the CommonJS locals (module,
     * exports, require, __filename and __dirname) for the module under load.
     *
     * @param moduleId identifier of the module being loaded (normalized)
     * @param code     raw JavaScript source code for the module
     * @param rec      the ModuleRecord corresponding to the module being loaded
     * @throws Exception if evaluation of the module fails
     */
    private void evalCommonJsInto(String moduleId, String code, ModuleRecord rec) throws Exception {
        String wrapped =
                "(function(module, exports, require, __filename, __dirname) {\n" +
                        "  'use strict';\n" +
                        code + "\n" +
                        "})";

        Source src = Source.newBuilder("js", wrapped, moduleId).build();
        Value fn = ctx.eval(src);

        Value moduleObj = rec.moduleObj;
        Value exportsObj = rec.exportsObj;

        // Local require resolves ids relative to the current module and records
        // dependency edges. It delegates to requireFrom() which will return
        // cached exports or load the module if necessary.
        ProxyExecutable localReq = args -> {
            String id = args.length > 0 ? args[0].asString() : "";
            String normalized = normalizeId(id);
            String resolved = resolveRelative(moduleId, normalized);
            // Record dependency from current module to resolved module
            recordDependency(moduleId, resolved);
            return requireFrom(moduleId, resolved);
        };
        Value localRequire = ctx.asValue(localReq);

        String __filename = moduleId;
        String __dirname = dirnameOf(moduleId);

        fn.execute(moduleObj, exportsObj, localRequire, __filename, __dirname);

        // In case the module reassigns module.exports, keep it. The exports
        // object on moduleObj already holds the final value.
    }

    // ---------------------------------------------------------------------
    // Hot reload / cache invalidation
    // ---------------------------------------------------------------------

    /**
     * Invalidate a single module and bump its version. Any modules that
     * directly or transitively depend on the invalidated module are also
     * invalidated to ensure consistency. Returns {@code true} if the module
     * was cached and removed.
     *
     * @param moduleId the module identifier to invalidate
     * @return {@code true} if the module was present and removed; {@code false} otherwise
     */
    public boolean invalidate(String moduleId) {
        assertOwnerThread();
        String id = normalizeId(moduleId);
        bumpVersion(id);
        // Remove the module and its dependents. Use a visited set to avoid
        // infinite recursion in dependency cycles.
        Set<String> visited = new HashSet<>();
        int removed = removeModuleAndDependents(id, visited);
        return removed > 0;
    }

    /**
     * Invalidate multiple modules. Each module identifier in the provided
     * collection is normalized and invalidated as if {@link #invalidate(String)}
     * were called for it. Returns the number of modules removed (including
     * dependents).
     *
     * @param moduleIds collection of module identifiers
     * @return count of removed module records
     */
    public int invalidateMany(Collection<String> moduleIds) {
        assertOwnerThread();
        if (moduleIds == null || moduleIds.isEmpty()) return 0;
        int removed = 0;
        for (String id0 : moduleIds) {
            if (id0 == null) continue;
            String id = normalizeId(id0);
            bumpVersion(id);
            Set<String> visited = new HashSet<>();
            removed += removeModuleAndDependents(id, visited);
        }
        return removed;
    }

    /**
     * Invalidate all cached modules whose identifier starts with the given
     * prefix. This operation also invalidates any modules that depend on
     * matching modules. Returns the number of modules removed. The prefix
     * should be a normalized identifier; empty prefixes do nothing.
     *
     * @param prefix prefix of module identifiers to invalidate
     * @return count of removed module records
     */
    public int invalidatePrefix(String prefix) {
        assertOwnerThread();
        String p = normalizeId(prefix);
        if (p.isEmpty()) return 0;
        int removed = 0;
        // Snapshot keys to avoid concurrent modification
        List<String> keys = new ArrayList<>(moduleCache.keySet());
        for (String k : keys) {
            if (k != null && k.startsWith(p)) {
                bumpVersion(k);
                Set<String> visited = new HashSet<>();
                removed += removeModuleAndDependents(k, visited);
            }
        }
        return removed;
    }

    /**
     * Recursively removes a module and any modules that depend on it. Also
     * cleans up dependency mappings and bumps version counters for each
     * removed module.
     *
     * @param id      module identifier to remove
     * @param visited set of already visited modules to avoid cycles
     * @return the total number of modules removed
     */
    private int removeModuleAndDependents(String id, Set<String> visited) {
        if (!visited.add(id)) return 0;
        int removed = 0;
        if (moduleCache.remove(id) != null) {
            removed++;
            // Bump version for dependents as well
            bumpVersion(id);
        }
        // Recursively remove modules that depend on this one
        Set<String> dependents = reverseDeps.remove(id);
        if (dependents != null) {
            for (String dep : dependents) {
                removed += removeModuleAndDependents(dep, visited);
            }
        }
        // Clean up forward dependencies: remove this id from any deps sets
        Set<String> children = deps.remove(id);
        if (children != null) {
            for (String child : children) {
                Set<String> rev = reverseDeps.get(child);
                if (rev != null) {
                    rev.remove(id);
                }
            }
        }
        return removed;
    }

    /**
     * Increments the version counter for a module. Used when invalidating or
     * reloading a module. Versions start at 1 on the first invalidation.
     *
     * @param moduleId normalized module identifier
     */
    private void bumpVersion(String moduleId) {
        moduleVersions.computeIfAbsent(moduleId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    // ---------------------------------------------------------------------
    // Globals binding
    // ---------------------------------------------------------------------

    /**
     * Binds stable globals for scripts. Scripts can access these globals
     * directly without having to import them. The following bindings are
     * created:
     *
     * <ul>
     *   <li>{@code ctx} – the system context provided by the embedding
     *   application.</li>
     *   <li>{@code engine} – the engine API provided by the application.</li>
     *   <li>{@code render} – shortcut for {@code engine.render()} if present.</li>
     *   <li>{@code world} – shortcut for {@code engine.world()} if present.</li>
     *   <li>{@code entity} – shortcut for {@code engine.entity()} if present.</li>
     * </ul>
     *
     * Callers can safely invoke this method multiple times; existing bindings
     * will be overwritten. Only the owner thread may call this method.
     *
     * @param systemContext the system context object to expose to scripts
     * @param engineApi     the engine API object to expose to scripts
     */
    public void bindGlobals(Object systemContext, Object engineApi) {
        assertOwnerThread();
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

    // ---------------------------------------------------------------------
    // Utility functions
    // ---------------------------------------------------------------------

    /**
     * Normalizes a module identifier. This method trims whitespace,
     * replaces backslashes with forward slashes, collapses consecutive
     * slashes, removes leading "./" segments and trailing slashes. A
     * {@code null} or empty input yields an empty string.
     *
     * @param moduleId identifier to normalize
     * @return normalized identifier
     */
    private static String normalizeId(String moduleId) {
        if (moduleId == null) return "";
        String s = moduleId.trim().replace('\\', '/');
        // Collapse multiple slashes into one
        while (s.contains("//")) {
            s = s.replace("//", "/");
        }
        // Remove leading ./ segments
        while (s.startsWith("./")) s = s.substring(2);
        // Remove trailing slash
        if (s.endsWith("/") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Resolves a requested module identifier relative to a parent module
     * identifier. Absolute identifiers (those not starting with '.' or '/')
     * are returned as-is (normalized). Requests starting with '/' are treated
     * as rooted paths with the leading slash removed. Relative identifiers
     * containing '..' or '.' segments are resolved by joining them with the
     * parent directory and normalizing. If the parent module has no
     * directory (i.e. it is at the top level) then the relative request is
     * resolved against the empty path.
     *
     * @param parentModuleId normalized parent module identifier
     * @param requestedId    normalized requested identifier
     * @return the normalized resolved identifier
     */
    private static String resolveRelative(String parentModuleId, String requestedId) {
        if (requestedId == null) return "";
        // Absolute imports (no relative markers) are returned unchanged
        if (!requestedId.startsWith(".") && !requestedId.startsWith("/")) {
            return requestedId;
        }
        String parentDir = dirnameOf(parentModuleId);
        if (requestedId.startsWith("/")) {
            // treat leading slash as root indicator
            return normalizeId(requestedId.substring(1));
        }
        // Join and normalize ../ and ./ segments using a deque
        Deque<String> parts = new ArrayDeque<>();
        if (!parentDir.isEmpty()) {
            for (String p : parentDir.split("/")) {
                if (!p.isEmpty()) parts.addLast(p);
            }
        }
        for (String p : requestedId.split("/")) {
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

    /**
     * Returns the directory portion of a module identifier. For example,
     * {@code dirnameOf("foo/bar/baz.js")} returns {@code "foo/bar"}.
     * If the identifier does not contain a slash, returns an empty string.
     *
     * @param moduleId module identifier
     * @return directory portion of the identifier
     */
    private static String dirnameOf(String moduleId) {
        if (moduleId == null) return "";
        String s = moduleId.replace('\\', '/');
        int idx = s.lastIndexOf('/');
        if (idx < 0) return "";
        return s.substring(0, idx);
    }

    // ---------------------------------------------------------------------
    // Close
    // ---------------------------------------------------------------------

    /**
     * Closes the underlying polyglot context and clears all module caches,
     * dependency graphs and version counters. Only the owner thread may
     * close the runtime. After calling close() the runtime is no longer
     * usable and any subsequent calls will throw.
     */
    @Override
    public void close() {
        assertOwnerThread();
        log.info("Closing GraalScriptRuntime");
        moduleCache.clear();
        moduleVersions.clear();
        deps.clear();
        reverseDeps.clear();
        ctx.close(true);
    }

    // =====================================================================
    // ======================= LAST UPDATE (ADD-ONLY) =======================
    // =====================================================================
    //
    // Требование: "не менять исходный код и не укорачивать его".
    // Ниже — только добавления (без правок существующих методов/полей/импортов).
    //
    // 1) ScriptJobQueue: команды из фоновых потоков -> owner/main thread.
    // 2) Модульный контракт exports.meta/system/prefabs + валидатор.
    // 3) Hot-reload изоляция: dispose(reason) перед удалением из cache.
    // 4) Rebind-флаг: можно снаружи понять, что после invalidate лучше rebindGlobals().
    //
    // Важно: чтобы не править imports, используем fully-qualified имена.
    // =====================================================================

    /**
     * ScriptJobQueue — минимальная очередь задач, которую можно дергать из любых потоков,
     * а выполнять (drain) — строго на owner thread (обычно в начале кадра).
     */
    public static final class ScriptJobQueue {
        private final java.util.Queue<java.lang.Runnable> q = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final java.util.concurrent.atomic.AtomicLong enqueued = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong executed = new java.util.concurrent.atomic.AtomicLong();

        /** From any thread. */
        public void post(java.lang.Runnable job) {
            if (job == null) return;
            q.add(job);
            enqueued.incrementAndGet();
        }

        /** From any thread. */
        public <T> java.util.concurrent.CompletableFuture<T> call(java.util.function.Supplier<T> job) {
            java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
            post(() -> {
                try {
                    f.complete(job.get());
                } catch (java.lang.Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            return f;
        }

        /**
         * Drain jobs on owner thread, bounded.
         * @return executed jobs count
         */
        public int drain(int maxJobs) {
            int n = 0;
            while (n < maxJobs) {
                java.lang.Runnable r = q.poll();
                if (r == null) break;
                try {
                    r.run();
                } finally {
                    executed.incrementAndGet();
                }
                n++;
            }
            return n;
        }

        public long enqueuedCount() { return enqueued.get(); }
        public long executedCount() { return executed.get(); }
        public int pending() { return java.lang.Math.max(0, (int) (enqueued.get() - executed.get())); }
    }

    private final ScriptJobQueue jobQueue = new ScriptJobQueue();

    /** If true, embedding layer may want to call bindGlobals(ctx, api) again after invalidation. */
    private volatile boolean rebindRequested;

    /** Access to job queue (post/call from any thread, drain from owner thread). */
    public ScriptJobQueue jobs() {
        return jobQueue;
    }

    /** Convenience: post a job from any thread. */
    public void post(java.lang.Runnable job) {
        jobQueue.post(job);
    }

    /** Convenience: call supplier on owner thread via queue. */
    public <T> java.util.concurrent.CompletableFuture<T> call(java.util.function.Supplier<T> job) {
        return jobQueue.call(job);
    }

    /**
     * Drain queued jobs. Must be called on owner thread (usually at start of frame).
     * @return executed jobs count
     */
    public int drainJobs(int maxJobs) {
        assertOwnerThread();
        return jobQueue.drain(maxJobs);
    }

    /**
     * Returns and clears rebind flag (one-shot).
     * Call this in your main loop to decide whether to call bindGlobals() again.
     */
    public boolean consumeRebindRequested() {
        boolean v = rebindRequested;
        rebindRequested = false;
        return v;
    }

    // ------------------ Module contract (exports.meta/system/prefabs/dispose) ------------------

    /** Contract meta (exports.meta). */
    public record Meta(String id, String version) {}

    /** Validated module descriptor. */
    public record ModuleDescriptor(
            String moduleId,
            Meta meta,
            Value exports,
            Value system,
            Value prefabs,
            Value dispose
    ) {}

    /**
     * Require module and validate contract:
     * - exports.meta.id (non-empty string) required
     * - exports.meta.version optional (default 0.0.0)
     * - exports.system optional, must be function or object
     * - exports.prefabs optional
     * - exports.dispose optional, must be function if present
     */
    public ModuleDescriptor requireDescriptor(String moduleId) {
        assertOwnerThread();
        String id = normalizeId(moduleId);
        Value exports = requireFrom("", id);
        return validateModule(id, exports);
    }

    private ModuleDescriptor validateModule(String moduleId, Value exports) {
        if (exports == null || exports.isNull()) {
            throw new IllegalArgumentException("Module contract violation: " + moduleId + " :: exports is null");
        }

        Value metaV = member(exports, "meta");
        if (metaV == null || metaV.isNull() || !metaV.hasMembers()) {
            throw new IllegalArgumentException("Module contract violation: " + moduleId + " :: exports.meta must be an object");
        }

        String id = asString(member(metaV, "id"), null);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Module contract violation: " + moduleId + " :: exports.meta.id must be a non-empty string");
        }

        String ver = asString(member(metaV, "version"), "0.0.0");

        Value system  = member(exports, "system");
        Value prefabs = member(exports, "prefabs");
        Value dispose = member(exports, "dispose");

        if (dispose != null && !dispose.isNull() && !dispose.canExecute()) {
            throw new IllegalArgumentException("Module contract violation: " + moduleId + " :: exports.dispose must be a function");
        }

        if (system != null && !system.isNull()) {
            boolean ok = system.canExecute() || system.hasMembers();
            if (!ok) {
                throw new IllegalArgumentException("Module contract violation: " + moduleId + " :: exports.system must be a function or an object");
            }
        }

        return new ModuleDescriptor(moduleId, new Meta(id, ver), exports, system, prefabs, dispose);
    }

    private static Value member(Value obj, String name) {
        if (obj == null || obj.isNull()) return null;
        if (!obj.hasMember(name)) return null;
        Value v = obj.getMember(name);
        return (v == null || v.isNull()) ? null : v;
    }

    private static String asString(Value v, String def) {
        if (v == null || v.isNull()) return def;
        try {
            return v.asString();
        } catch (Exception ignored) {
            return def;
        }
    }

    // ------------------ Hot-reload isolation (dispose(reason) + transitive invalidation) ------------------

    /**
     * NEW API (add-only): invalidate with dispose(reason) before removal, transitive.
     * Does NOT change existing invalidate() methods. Use this in the new pipeline.
     */
    public boolean invalidateWithReason(String moduleId, String reason) {
        assertOwnerThread();
        String id = normalizeId(moduleId);
        java.util.Set<String> visited = new java.util.HashSet<>();
        int removed = removeModuleAndDependentsWithDispose(id, visited, reason == null ? "hotReload" : reason);
        if (removed > 0) rebindRequested = true;
        return removed > 0;
    }

    /**
     * NEW API (add-only): invalidate many with dispose(reason) before removal, transitive.
     */
    public int invalidateManyWithReason(java.util.Collection<String> moduleIds, String reason) {
        assertOwnerThread();
        if (moduleIds == null || moduleIds.isEmpty()) return 0;
        int removed = 0;
        for (String id0 : moduleIds) {
            if (id0 == null) continue;
            String id = normalizeId(id0);
            java.util.Set<String> visited = new java.util.HashSet<>();
            removed += removeModuleAndDependentsWithDispose(id, visited, reason == null ? "hotReload" : reason);
        }
        if (removed > 0) rebindRequested = true;
        return removed;
    }

    /**
     * NEW API (add-only): invalidate prefix with dispose(reason) before removal, transitive.
     */
    public int invalidatePrefixWithReason(String prefix, String reason) {
        assertOwnerThread();
        String p = normalizeId(prefix);
        if (p.isEmpty()) return 0;

        int removed = 0;
        java.util.List<String> keys = new java.util.ArrayList<>(moduleCache.keySet());
        for (String k : keys) {
            if (k != null && k.startsWith(p)) {
                java.util.Set<String> visited = new java.util.HashSet<>();
                removed += removeModuleAndDependentsWithDispose(k, visited, reason == null ? "hotReload" : reason);
            }
        }
        if (removed > 0) rebindRequested = true;
        return removed;
    }

    private int removeModuleAndDependentsWithDispose(String id, java.util.Set<String> visited, String reason) {
        if (!visited.add(id)) return 0;

        // 1) dispose before removal
        disposeModuleIfPresent(id, reason);

        int removed = 0;

        // 2) remove module itself
        if (moduleCache.remove(id) != null) {
            removed++;
            bumpVersion(id);
        }

        // 3) remove dependents transitively
        java.util.Set<String> dependents = reverseDeps.remove(id);
        if (dependents != null) {
            for (String dep : dependents) {
                removed += removeModuleAndDependentsWithDispose(dep, visited, reason);
            }
        }

        // 4) cleanup forward deps
        java.util.Set<String> children = deps.remove(id);
        if (children != null) {
            for (String child : children) {
                java.util.Set<String> rev = reverseDeps.get(child);
                if (rev != null) {
                    rev.remove(id);
                }
            }
        }

        return removed;
    }

    private void disposeModuleIfPresent(String moduleId, String reason) {
        ModuleRecord rec = moduleCache.get(moduleId);
        if (rec == null) return;
        try {
            Value exports = rec.moduleObj.getMember("exports");
            if (exports != null && !exports.isNull() && exports.hasMember("dispose")) {
                Value d = exports.getMember("dispose");
                if (d != null && !d.isNull() && d.canExecute()) {
                    d.executeVoid(reason == null ? "reload" : reason);
                }
            }
        } catch (Throwable t) {
            log.warn("dispose() failed for module {} reason={}", moduleId, reason, t);
        }
    }
}