package org.foxesworld.kalitech.engine.script.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * Bounded text and compiled-chunk caches for the Lua runtime.
 */
public final class ScriptCaches {

    private final Cache<String, String> moduleText;
    private final Cache<SourceKey, String> compiledLua;

    private ScriptCaches(Cache<String, String> moduleText, Cache<SourceKey, String> compiledLua) {
        this.moduleText = Objects.requireNonNull(moduleText, "moduleText");
        this.compiledLua = Objects.requireNonNull(compiledLua, "compiledLua");
    }

    public static ScriptCaches defaults() {
        Cache<String, String> moduleText = Caffeine.newBuilder()
                .maximumSize(2_048)
                .expireAfterAccess(Duration.ofSeconds(10))
                .build();
        Cache<SourceKey, String> compiledLua = Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(Duration.ofSeconds(20))
                .build();
        return new ScriptCaches(moduleText, compiledLua);
    }

    public Cache<String, String> moduleText() {
        return moduleText;
    }

    public Cache<SourceKey, String> compiledLua() {
        return compiledLua;
    }

    public void invalidateModule(String moduleId) {
        if (moduleId == null) return;
        moduleText.invalidate(moduleId);
        invalidateByModuleId(compiledLua.asMap(), moduleId);
    }

    public void invalidateAll() {
        moduleText.invalidateAll();
        compiledLua.invalidateAll();
    }

    private static <V> void invalidateByModuleId(ConcurrentMap<SourceKey, V> map, String moduleId) {
        for (SourceKey key : map.keySet()) {
            if (moduleId.equals(key.moduleId)) map.remove(key);
        }
    }

    private static long fnv1a64(String text) {
        if (text == null) return 0L;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

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
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SourceKey that)) return false;
            return contentHash == that.contentHash && moduleId.equals(that.moduleId);
        }

        @Override
        public int hashCode() {
            int result = moduleId.hashCode();
            return 31 * result + (int) (contentHash ^ (contentHash >>> 32));
        }
    }
}
