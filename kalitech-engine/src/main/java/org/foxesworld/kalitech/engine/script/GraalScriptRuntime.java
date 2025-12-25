// FILE: GraalScriptRuntime.java
package org.foxesworld.kalitech.engine.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.script.cache.ScriptCaches;
import org.foxesworld.kalitech.engine.script.resolve.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GraalScriptRuntime is responsible for loading and executing JavaScript modules
 * inside a GraalVM {@link Context}. It provides a CommonJS-style {@code require}
 * implementation, manages module caching, supports hot reloading and tracks
 * dependencies to allow transitive invalidation when a module changes.
 *
 * <p>Thread confined: first thread that touches this runtime becomes the owner.
 * All subsequent interactions must occur on the same thread.</p>
 *
 * <p>Security: host class lookup is disabled; host access is restricted to
 * members annotated with {@link HostAccess.Export}.</p>
 *
 * Author: Calista Verner
 */
public final class GraalScriptRuntime implements Closeable {

    private static final Logger log = LogManager.getLogger(GraalScriptRuntime.class);

    /**
     * Legacy text provider (kept for compatibility).
     * Prefer {@link ModuleStreamProvider} for builtins/resources/files.
     */
    @FunctionalInterface
    public interface ModuleSourceProvider {
        String loadText(String moduleId) throws Exception;
    }

    /**
     * Stream provider. MUST return a fresh InputStream each call.
     * Caller closes the stream.
     *
     * @return stream or null if module not found.
     */
    @FunctionalInterface
    public interface ModuleStreamProvider {
        InputStream openStream(String moduleId) throws Exception;
    }

    private final Context ctx;
    private final ScriptCaches caches;

    private volatile Thread ownerThread;

    /** External stream loader used to fetch module source. */
    private volatile ModuleStreamProvider streamLoader;

    /** CommonJS exports cache (source of truth for loaded modules & cyclic deps). */
    private final Map<String, ModuleRecord> moduleCache = new ConcurrentHashMap<>();

    /** Version counter per module id. */
    private final Map<String, AtomicLong> moduleVersions = new ConcurrentHashMap<>();

    /** Dependency graph for hot reload. */
    private final Map<String, Set<String>> forwardDeps = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();

    /** Global require in JS. */
    private final Value requireFn;
    private final MutableAliasResolver aliasResolver;

    /** Job queue. */
    private final ScriptJobQueue jobs = new ScriptJobQueue();

    /** Optional host handles registry. */
    private final Map<String, MethodHandle> hostHandles = new ConcurrentHashMap<>();

    /** Module id resolver chain. */
    private final ResolverChain resolver;

    /** Built-in bootstrap module id. */
    private final String builtinBootstrapId = "@builtin/bootstrap";

    /** Built-in prefix and resources directory. */
    private static final String BUILTIN_PREFIX = "@builtin/";
    private static final String BUILTIN_RES_DIR = "kalitech/builtin/";

    /** Guard to ensure builtins init happens once. */
    private volatile boolean builtinsInitialized = false;

    public GraalScriptRuntime() {
        this(ScriptCaches.defaults());
    }

    public GraalScriptRuntime(ScriptCaches caches) {
        this.caches = Objects.requireNonNull(caches, "caches");
        this.aliasResolver = new MutableAliasResolver();

        // ResolverChain policy:
        // - keep @builtin/* stable
        // - then relative
        // - then namespace kalitech:ui -> Mods/kalitech/ui/index.js
        // - then aliases (loaded from bootstrap)
        // - then pass-through
        this.resolver = new ResolverChain()
                .add(new BuiltinResolver(BUILTIN_PREFIX))
                .add(new RelativeResolver())
                .add(new NamespaceResolver("Mods"))
                .add(aliasResolver)
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

        // install global require() dispatcher (CommonJS)
        // IMPORTANT: do NOT normalize/resolve here; requireFrom() will do it.
        ProxyExecutable req = args -> {
            String request = args.length > 0 ? args[0].asString() : "";
            return requireFrom("", request);
        };
        this.requireFn = ctx.asValue(req);
        ctx.getBindings("js").putMember("require", requireFn);

        // Optional: expose resolver for diagnostics (used by @builtin/paths if you want)
        ProxyExecutable resolveFn = args -> {
            String parent = args.length > 0 ? args[0].asString() : "";
            String request = args.length > 1 ? args[1].asString() : "";
            return resolveToModuleId(normalizeId(parent), request);
        };
        ctx.getBindings("js").putMember("__resolveId", ctx.asValue(resolveFn));

        assertOwnerThread();
    }

    /**
     * Legacy setter: wraps text provider into stream provider (UTF-8).
     */
    public GraalScriptRuntime setModuleSourceProvider(ModuleSourceProvider loader) {
        if (loader == null) {
            this.streamLoader = null;
            return this;
        }
        this.streamLoader = moduleId -> {
            String txt = loader.loadText(moduleId);
            if (txt == null) return null;
            byte[] bytes = txt.getBytes(StandardCharsets.UTF_8);
            return new java.io.ByteArrayInputStream(bytes);
        };
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
    // Built-ins init (MUST happen before any user scripts are resolved/loaded)
    // ---------------------------------------------------------------------

    /**
     * Ensures built-ins are loaded before any non-builtin module resolution/loading.
     * This is called automatically from requireFrom() BEFORE resolveToModuleId().
     */
    private void ensureBuiltInsBeforeUserScripts(String parentModuleId, String requestRaw) {
        if (builtinsInitialized) return;

        String req = (requestRaw == null) ? "" : requestRaw.trim();
        if (req.startsWith(BUILTIN_PREFIX)) {
            // Direct builtin require doesn't need auto-init of the whole suite.
            // But we still allow initBuiltIns() to be called explicitly.
            return;
        }

        // For any user-script require, make sure builtins (bootstrap + aliases) are ready.
        //initBuiltIns();
    }

    /**
     * Loads built-in modules BEFORE user scripts:
     *  - @builtin/bootstrap (must exist)
     *  - optionally: other builtins (assert/deepMerge/events/paths/schema)
     *
     * Also loads aliases from bootstrap exports.config.aliases into MutableAliasResolver.
     *
     * Safe to call multiple times.
     */
    public void initBuiltIns(EngineApi api) {
        assertOwnerThread();

        if (builtinsInitialized) return;
        builtinsInitialized = true;

        if (this.streamLoader == null) {
            String msg = "ModuleStreamProvider not set before initBuiltIns(). " +
                    "Call setModuleStreamProvider(...) with your project/asset loader first.";
            log.error("[script] {}", msg);
            throw new IllegalStateException(msg);
        }

        log.debug("[script] builtins: loading {}", builtinBootstrapId);
        long t0 = System.nanoTime();
        Value boot = require(builtinBootstrapId);
        boot.invokeMember("attachEngine", api);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        log.debug("[script] builtins: loaded {} ({} ms)", builtinBootstrapId, ms);

        applyBootstrapAliases(boot);
    }

    // --- add this method inside GraalScriptRuntime ---

    private ModuleStreamProvider wrapWithBuiltIns(ModuleStreamProvider downstream) {
        final ClassLoader cl = GraalScriptRuntime.class.getClassLoader();

        return moduleId -> {
            String id = normalizeId(moduleId);

            if (id.startsWith(BUILTIN_PREFIX)) {
                InputStream in = openBuiltInStream(cl, id);
                if (in != null) {
                    String rel = id.substring(BUILTIN_PREFIX.length());
                    if (!rel.endsWith(".js")) rel = rel + ".js";
                    log.debug("[script] builtins: stream=open resource {}{}", BUILTIN_RES_DIR, rel);
                    return in;
                }
                log.error("[script] builtins: resource not found for {}", id);
                return null;
            }

            return downstream != null ? downstream.openStream(id) : null;
        };
    }


    private void warmupBuiltIn(String id) {
        try {
            log.debug("[script] builtins: loading {}", id);
            long t0 = System.nanoTime();
            require(id);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.debug("[script] builtins: loaded {} ({} ms)", id, ms);
        } catch (Throwable t) {
            // Optional ones: warn, but don't kill runtime
            log.warn("[script] builtins: failed to load {}: {}", id, t.toString());
        }
    }

    public GraalScriptRuntime setModuleStreamProvider(ModuleStreamProvider loader) {
        this.streamLoader = (loader == null) ? null : wrapWithBuiltIns(loader);
        return this;
    }

    /**
     * Loads aliases from bootstrap exports:
     *  module.exports = { config: { aliases: { "@core":"Scripts/core", ... } } }
     */
    private void applyBootstrapAliases(Value bootExports) {
        try {
            if (bootExports == null) return;

            Value cfg = bootExports.getMember("config");
            if (cfg == null) {
                log.warn("[script] builtins: bootstrap has no 'config' export (aliases not applied)");
                return;
            }

            Value aliases = cfg.getMember("aliases");
            if (aliases == null || !aliases.hasMembers()) {
                log.warn("[script] builtins: bootstrap config has no 'aliases' (aliases not applied)");
                return;
            }

            Map<String, String> map = new LinkedHashMap<>();
            for (String k : aliases.getMemberKeys()) {
                Value v = aliases.getMember(k);
                if (v != null && v.isString()) {
                    map.put(k, v.asString());
                }
            }

            if (map.isEmpty()) {
                log.warn("[script] builtins: bootstrap aliases are empty");
                return;
            }

            aliasResolver.setAliases(map);
            log.debug("[script] builtins: aliases applied ({}) {}", map.size(), map.keySet());

        } catch (Throwable t) {
            log.warn("[script] builtins: failed to apply bootstrap aliases", t);
        }
    }

    /**
     * Minimal built-in-only loader: serves @builtin/* from resources/kalitech/builtin/*.js.
     * Used only as a fallback if user didn't set any ModuleStreamProvider.
     */
    private ModuleStreamProvider openBuiltInStreamFallback() {
        final ClassLoader cl = GraalScriptRuntime.class.getClassLoader();
        return moduleId -> openBuiltInStream(cl, normalizeId(moduleId));
    }

    private static InputStream openBuiltInStream(ClassLoader cl, String moduleId) {
        if (moduleId == null) return null;
        if (!moduleId.startsWith(BUILTIN_PREFIX)) return null;

        String rel = moduleId.substring(BUILTIN_PREFIX.length());
        if (rel.isBlank()) return null;

        if (!rel.endsWith(".js")) rel = rel + ".js";
        String resPath = BUILTIN_RES_DIR + rel;

        return cl.getResourceAsStream(resPath);
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public Value require(String moduleId) {
        assertOwnerThread();
        return requireFrom("", moduleId);
    }

    public long moduleVersion(String moduleId) {
        AtomicLong v = moduleVersions.get(normalizeId(moduleId));
        return v == null ? 0L : v.get();
    }

    // ---------------------------------------------------------------------
    // Core module loader
    // ---------------------------------------------------------------------

    /**
     * Resolves relative "./" "../" against parent module id.
     * IMPORTANT: must see "./" and "../" BEFORE normalizeId strips "./".
     */
    private static String resolveRequest(String parentModuleId, String requestRaw) {
        if (requestRaw == null) return "";
        String req = requestRaw.trim().replace('\\', '/');

        // absolute-ish
        while (req.startsWith("/")) req = req.substring(1);

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
            return sb.toString();
        }

        return req;
    }

    private static String normalizeId(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.trim().replace('\\', '/');

        // strip leading "./"
        while (id.startsWith("./")) id = id.substring(2);

        // strip leading "/"
        while (id.startsWith("/")) id = id.substring(1);

        // collapse // without regex
        id = collapseSlashes(id);

        // remove trailing /
        while (id.endsWith("/")) id = id.substring(0, id.length() - 1);

        return id;
    }

    private static String collapseSlashes(String s) {
        if (s.indexOf("//") < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' && prev == '/') continue;
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    private static String dirnameOf(String moduleId) {
        if (moduleId == null) return "";
        String id = moduleId.replace('\\', '/');
        int idx = id.lastIndexOf('/');
        return idx < 0 ? "" : id.substring(0, idx);
    }

    private Value requireFrom(String parentModuleId, String requestRaw) {
        assertOwnerThread();

        // MUST load builtins before resolving/loading any user scripts.
        ensureBuiltInsBeforeUserScripts(parentModuleId, requestRaw);

        final String parent = normalizeId(parentModuleId);
        final String request = (requestRaw == null) ? "" : requestRaw;

        // 1) Resolve base id using your resolver chain (pure, no IO)
        final String baseId = resolveToModuleId(parent, request);

        // 2) Expand into strict candidates:
        //    - if baseId is builtin or already has extension -> [baseId]
        //    - else -> [baseId/index.js, baseId.js]
        final String[] candidates = expandRequireCandidates(baseId);

        // 3) Fast path: already loaded under any candidate id
        for (String id : candidates) {
            ModuleRecord existing = moduleCache.get(id);
            if (existing != null) {
                return existing.exportsObj;
            }
        }

        // 4) We must pick the first candidate that actually exists (stream != null / code != null)
        ModuleStreamProvider l = this.streamLoader;
        if (l == null) {
            String msg =
                    "require() called but no ModuleStreamProvider is set. " +
                            "resolvedBase='" + baseId + "', parent='" + parent + "', request='" + request + "'";
            log.error("[script] {}", msg);
            throw new IllegalStateException(msg);
        }

        String moduleId = null; // final chosen id (candidate)
        String code = null;

        try {
            for (String id : candidates) {
                // Debug-log what exactly we are loading for built-ins
                if (id.startsWith(BUILTIN_PREFIX)) {
                    log.debug("[script] builtins: require {}", id);
                }

                // Load source text (via InputStream) with caching
                // IMPORTANT: cache key is candidate id, not base id
                String maybe = caches.moduleText().get(id, key -> {
                    try (InputStream in = l.openStream(key)) {
                        if (in == null) return null;
                        return readUtf8(in);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                if (maybe != null) {
                    moduleId = id;
                    code = maybe;
                    break;
                }
            }

            if (code == null) {
                String msg =
                        "ModuleStreamProvider returned null (module not found). " +
                                "tried=" + formatCandidates(candidates) +
                                " parent='" + parent + "', request='" + request + "', resolvedBase='" + baseId + "'";
                log.error("[script] {}", msg);
                throw new IllegalStateException(msg);
            }

        } catch (RuntimeException re) {
            Throwable cause = re.getCause();

            String base =
                    "Failed to load module source. " +
                            "tried=" + formatCandidates(candidates) +
                            " parent='" + parent + "', request='" + request + "', resolvedBase='" + baseId + "'";

            if (cause != null) {
                log.error("[script] {} cause={}", base, cause.toString());
                throw new RuntimeException(base, cause);
            }

            log.error("[script] {}", base, re);
            throw re;
        }

        // 5) Now we have a deterministic final moduleId (candidate).
        //    Create record BEFORE evaluation for cyclic deps
        ModuleRecord rec = new ModuleRecord(moduleId, ctx);
        moduleCache.put(moduleId, rec);

        try {
            evalCommonJsInto(moduleId, code, rec);
            rec.loaded = true;
            return rec.exportsObj;
        } catch (Exception e) {
            moduleCache.remove(moduleId);
            String msg =
                    "Failed to evaluate module. " +
                            "resolved='" + moduleId + "', parent='" + parent + "', request='" + request + "'";
            log.error("[script] {}", msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * Strict rule:
     * - if baseId is builtin or already has extension -> [baseId]
     * - else -> [baseId + "/index.js", baseId + ".js"]
     */
    private String[] expandRequireCandidates(String baseId) {
        final String base = normalizeId(baseId);

        // builtins: never expand (keep existing semantics)
        if (base.startsWith(BUILTIN_PREFIX)) {
            return new String[] { base };
        }

        // if request already has extension (.js, .json, etc) -> exact only
        if (hasExtension(base)) {
            return new String[] { base };
        }

        // strict directory-first rule:
        // require("./dir") -> try dir/index.js first (dir module), then dir.js
        return new String[] { base + "/index.js", base + ".js" };
    }

    /** Extension exists only if dot is in the last path segment. */
    private boolean hasExtension(String id) {
        if (id == null || id.isEmpty()) return false;
        String s = id.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        int dot = s.lastIndexOf('.');
        return dot > slash;
    }

    private String formatCandidates(String[] cands) {
        if (cands == null || cands.length == 0) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < cands.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(cands[i]).append('\'');
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * Resolves a request to a final canonical module id:
     * - apply relative resolution (./ ../)
     * - run resolver chain (builtin/namespace/aliases/etc)
     * - normalize for cache keys
     */
    private String resolveToModuleId(String parentModuleId, String requestRaw) {
        String rawResolved = resolveRequest(parentModuleId, requestRaw);
        String afterChain = resolver.resolveOrThrow(parentModuleId, rawResolved);
        return normalizeId(afterChain);
    }

    private static String readUtf8(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

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

        ProxyExecutable localReq = args -> {
            String request = args.length > 0 ? args[0].asString() : "";
            String childId = resolveToModuleId(moduleId, request);
            recordDependency(moduleId, childId);
            return requireFrom(moduleId, childId);
        };
        Value localRequire = ctx.asValue(localReq);

        String __filename = moduleId;
        String __dirname = dirnameOf(moduleId);

        fn.execute(moduleObj, exportsObj, localRequire, __filename, __dirname);

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

        // protect builtins from invalidation
        if (id.startsWith(BUILTIN_PREFIX)) return false;

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
            if (id.startsWith(BUILTIN_PREFIX)) continue;
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

        // protect builtins
        if (p.startsWith(BUILTIN_PREFIX)) return 0;

        List<String> keys = new ArrayList<>(moduleCache.keySet());
        int total = 0;
        Set<String> visited = new HashSet<>();
        for (String id : keys) {
            if (id.startsWith(p) && !id.startsWith(BUILTIN_PREFIX)) {
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
        aliasResolver.setAliases(Map.of());
        builtinsInitialized = false;
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