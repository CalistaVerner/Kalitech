package org.foxesworld.kalitech.engine.api.module;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of API modules.
 *
 * <p>Design goals:
 * <ul>
 *   <li>No engine hardcode: modules are discovered/loaded externally and registered here.</li>
 *   <li>Deterministic lifecycle: {@link #register(ApiModule)} always attaches, and any replaced module is detached.</li>
 *   <li>Low overhead: stable O(1) lookups and startup-only allocations.</li>
 * </ul>
 */
public final class ApiRegistry {

    private static final Logger log = LogManager.getLogger(ApiRegistry.class);

    private final ApiContext ctx;
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public ApiRegistry(ApiContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        if (log.isDebugEnabled()) {
            log.debug("[api] registry created ctx={}", ctx.getClass().getSimpleName());
        }
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("id is blank");
        return normalized;
    }

    private static String fmt(Entry e) {
        if (e == null) return "null";
        String impl = (e.api != null) ? e.api.getClass().getSimpleName() : "null";
        return "{id=" + e.id + ",name=" + e.name + ",ver=" + e.version + ",impl=" + impl + "}";
    }

    /**
     * Legacy entry registration (non-module APIs). Prefer {@link #register(ApiModule)}.
     */
    @Deprecated
    public <T> T registerLegacy(String id, T api) {
        String normalized = requireId(id);
        Objects.requireNonNull(api, "api");

        Entry prev = map.put(normalized, new Entry(normalized, api, null, normalized, "legacy"));

        if (log.isDebugEnabled()) {
            if (prev == null) {
                log.debug("[api] register legacy id='{}' impl={}", normalized, api.getClass().getName());
            } else {
                log.debug("[api] replace legacy id='{}' prevImpl={} newImpl={}",
                        normalized,
                        prev.api != null ? prev.api.getClass().getName() : "null",
                        api.getClass().getName());
            }
        }

        return api;
    }

    /**
     * Registers a module and attaches it immediately. If another entry with the same id exists,
     * the previous module (if any) is detached deterministically after a successful replace.
     */
    public <T extends ApiModule> T register(T module) {
        Objects.requireNonNull(module, "module");

        // Attach first: keep contract "usable immediately after register".
        module.attach(ctx);

        final String id = requireId(module.id());
        final Entry next = new Entry(id, module, module, module.name(), module.version());

        Entry prev = map.put(id, next);

        if (log.isDebugEnabled()) {
            if (prev == null) {
                log.debug("[api] register id='{}' name='{}' ver='{}' impl={}",
                        id, next.name, next.version, module.getClass().getName());
            } else {
                log.debug("[api] replace id='{}' prev={} new={} (prevLegacy={})",
                        id, fmt(prev), fmt(next), prev.isLegacy());
            }
        }

        // Detach previous module after successful replace.
        if (prev != null && prev.module != null) {
            try {
                prev.module.detach();
                if (log.isDebugEnabled()) {
                    log.debug("[api] detached(prev) id='{}' name='{}' ver='{}' impl={}",
                            prev.id, prev.name, prev.version, prev.module.getClass().getName());
                }
            } catch (Throwable t) {
                // Detach must never break startup; log and continue.
                log.warn("[api] detach(prev) failed id='{}' impl={} err={}",
                        prev.id,
                        prev.module.getClass().getName(),
                        t.toString());
            }
        }

        return module;
    }

    /**
     * Returns the registered API object (module instance for modular entries).
     */
    public Object api(String id) {
        Entry e = get(id);
        return (e != null) ? e.api : null;
    }

    /**
     * Returns the registered API object cast to the requested type, or null if missing/incompatible.
     */
    public <T> T api(String id, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Entry e = get(id);
        if (e == null) return null;
        Object a = e.api;
        return type.isInstance(a) ? type.cast(a) : null;
    }

    public Entry get(String id) {
        if (id == null) return null;
        return map.get(id.trim());
    }

    public String[] keys() {
        ArrayList<String> out = new ArrayList<>(map.keySet());
        out.sort(String::compareTo);
        return out.toArray(new String[0]);
    }

    public List<Entry> entries() {
        ArrayList<Entry> out = new ArrayList<>(map.values());
        out.sort(Comparator.comparing(e -> e.id));
        return out;
    }

    public ApiModuleInfo info(String id) {
        Entry entry = get(id);
        return ApiModuleInfo.from(entry);
    }

    public List<ApiModuleInfo> infos() {
        ArrayList<ApiModuleInfo> out = new ArrayList<>();
        for (Entry e : entries()) {
            out.add(ApiModuleInfo.from(e));
        }
        return out;
    }

    /**
     * Detaches all registered modules and clears the registry.
     * Safe for shutdown: must not throw.
     */
    public void detachAll() {
        if (log.isDebugEnabled()) {
            log.debug("[api] detachAll begin count={}", map.size());
        }

        for (Entry e : map.values()) {
            if (e.module != null) {
                try {
                    e.module.detach();
                    if (log.isDebugEnabled()) {
                        log.debug("[api] detached id='{}' name='{}' ver='{}' impl={}",
                                e.id, e.name, e.version, e.module.getClass().getName());
                    }
                } catch (Throwable t) {
                    // detach must never fail shutdown path
                    log.warn("[api] detach failed id='{}' impl={} err={}",
                            e.id,
                            e.module.getClass().getName(),
                            t.toString());
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("[api] skip detach legacy id='{}' impl={}",
                            e.id,
                            e.api != null ? e.api.getClass().getName() : "null");
                }
            }
        }

        map.clear();

        if (log.isDebugEnabled()) {
            log.debug("[api] detachAll done");
        }
    }

    public static final class Entry {
        public final String id;
        public final Object api;
        public final ApiModule module; // null if legacy
        public final String name;
        public final String version;

        Entry(String id, Object api, ApiModule module, String name, String version) {
            this.id = id;
            this.api = api;
            this.module = module;
            this.name = name;
            this.version = version;
        }

        boolean isLegacy() {
            return module == null;
        }
    }
}