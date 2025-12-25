package org.foxesworld.kalitech.engine.script.cache;

// Author: Calista Verner

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * Centralized caching for the script runtime.
 *
 * Design goals:
 *  - Fast hot-reload cycles: avoid repeated IO/parsing when a file is saved multiple times.
 *  - Hard bounds: every cache has a max size to prevent memory creep.
 *  - Explicit invalidation: HotReloadWatcher / runtime invalidation should invalidate relevant keys.
 *
 * IMPORTANT: This module does NOT replace the runtime module/exports cache.
 * It only accelerates auxiliary steps: loading module text and building wrapped Source.
 */
public final class ScriptCaches {

    /**
     * Raw module text by moduleId ("Scripts/..../file.js").
     * Very hot during editor work.
     */
    private final Cache<String, String> moduleText;

    /**
     * Wrapped Source cache by (moduleId + hash(wrappedText)).
     * Avoids rebuilding/parsing during reload bursts.
     */
    private final Cache<SourceKey, org.graalvm.polyglot.Source> wrappedSources;

    /**
     * Wrapped JS code string cache (reduces String building churn).
     * Kept small because wrapped code can be large.
     */
    private final Cache<SourceKey, String> wrappedCode;

    private ScriptCaches(Cache<String, String> moduleText,
                         Cache<SourceKey, org.graalvm.polyglot.Source> wrappedSources,
                         Cache<SourceKey, String> wrappedCode) {
        this.moduleText = Objects.requireNonNull(moduleText, "moduleText");
        this.wrappedSources = Objects.requireNonNull(wrappedSources, "wrappedSources");
        this.wrappedCode = Objects.requireNonNull(wrappedCode, "wrappedCode");
    }

    public Cache<String, String> moduleText() { return moduleText; }

    public Cache<SourceKey, org.graalvm.polyglot.Source> wrappedSources() { return wrappedSources; }

    public Cache<SourceKey, String> wrappedCode() { return wrappedCode; }

    /**
     * Invalidate caches for a particular module id.
     * Note: wrapped caches are keyed by SourceKey, so this method invalidates by scanning keys.
     * The caches are bounded; scanning is acceptable (editor/dev workflow).
     */
    public void invalidateModule(String moduleId) {
        if (moduleId == null) return;
        moduleText.invalidate(moduleId);

        invalidateByModuleId(wrappedSources.asMap(), moduleId);
        invalidateByModuleId(wrappedCode.asMap(), moduleId);
    }

    /** Invalidate everything. Useful for full reset / shutdown. */
    public void invalidateAll() {
        moduleText.invalidateAll();
        wrappedSources.invalidateAll();
        wrappedCode.invalidateAll();
    }

    private static <V> void invalidateByModuleId(ConcurrentMap<SourceKey, V> map, String moduleId) {
        for (SourceKey k : map.keySet()) {
            if (moduleId.equals(k.moduleId)) {
                map.remove(k);
            }
        }
    }

    /**
     * Default cache setup for Kalitech (editor-first workflow).
     *
     * - moduleText: small but very hot, short expiry to reflect filesystem changes
     * - wrappedCode/source: bounded and expire to avoid stale buildup
     */
    public static ScriptCaches defaults() {
        Cache<String, String> moduleText = Caffeine.newBuilder()
                .maximumSize(2_048)
                .expireAfterAccess(Duration.ofSeconds(10))
                .build();

        Cache<SourceKey, String> wrappedCode = Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(Duration.ofSeconds(20))
                .build();

        Cache<SourceKey, org.graalvm.polyglot.Source> wrappedSources = Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(Duration.ofSeconds(20))
                .build();

        return new ScriptCaches(moduleText, wrappedSources, wrappedCode);
    }

    /**
     * Cache key that includes moduleId and a stable hash of the content.
     * We store the hash to avoid holding large strings as part of the key.
     */
    public static final class SourceKey {
        public final String moduleId;
        public final long contentHash;

        private SourceKey(String moduleId, long contentHash) {
            this.moduleId = moduleId;
            this.contentHash = contentHash;
        }

        public static SourceKey of(String moduleId, String content) {
            return new SourceKey(Objects.requireNonNull(moduleId, "moduleId"), fnv1a64(content));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceKey)) return false;
            SourceKey that = (SourceKey) o;
            return contentHash == that.contentHash && moduleId.equals(that.moduleId);
        }

        @Override
        public int hashCode() {
            int h = moduleId.hashCode();
            h = 31 * h + (int) (contentHash ^ (contentHash >>> 32));
            return h;
        }

        @Override
        public String toString() {
            return "SourceKey{" + moduleId + ", hash=" + Long.toHexString(contentHash) + '}';
        }
    }

    private static long fnv1a64(String s) {
        if (s == null) return 0L;
        long h = 0xcbf29ce484222325L; // offset basis
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L; // prime
        }
        return h;
    }
}