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
                log.debug("[api] replace id='{}' prev={} new={}", id, fmt(prev), fmt(next));
            }
        }

        // Detach previous module after successful replace.
        if (prev != null && prev.module != null) {
            detachEntry(prev, "prev");
        }

        return module;
    }

    /**
     * Detaches a specific module instance if it is still the currently registered module for its id.
     *
     * <p>Semantics:
     * <ul>
     *   <li>Never throws (shutdown-safe).</li>
     *   <li>CAS removal: will only remove if the map still points to the same module instance.</li>
     *   <li>If the module was already replaced/unregistered, it becomes a no-op.</li>
     * </ul>
     */
    public void detach(ApiModule module) {
        if (module == null) return;

        final String id;
        try {
            id = requireId(module.id());
        } catch (Throwable t) {
            // Invalid module id - cannot be in registry reliably. Best effort detach only.
            try {
                module.detach();
            } catch (Throwable ignored) {
                // never throw
            }
            return;
        }

        Entry current = map.get(id);
        if (current == null) {
            return;
        }

        // Only detach if this exact instance is still registered.
        if (current.module != module) {
            return;
        }

        if (map.remove(id, current)) {
            detachEntry(current, "explicit");
        }
    }

    /**
     * Returns the registered API object (module instance for modular entries).
     */
    public Object api(String id) {
        Entry e = get(id);
        return (e != null) ? e.api : null;
    }

    /**
     * Returns the registered API object cast to the requested type, or null if missing or of the wrong type.
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
        List<Entry> ordered = entries(); // sorted by id -> deterministic
        if (log.isDebugEnabled()) {
            log.debug("[api] detachAll begin count={}", ordered.size());
        }

        for (Entry e : ordered) {
            detachEntry(e, "all");
        }

        map.clear();

        if (log.isDebugEnabled()) {
            log.debug("[api] detachAll done");
        }
    }

    private void detachEntry(Entry e, String reason) {
        try {
            e.module.detach();
            if (log.isDebugEnabled()) {
                log.debug("[api] detached({}) id='{}' name='{}' ver='{}' impl={}",
                        reason, e.id, e.name, e.version, e.module.getClass().getName());
            }
        } catch (Throwable t) {
            // Detach must never break startup/shutdown path; log and continue.
            log.warn("[api] detach({}) failed id='{}' impl={} err={}",
                    reason,
                    e.id,
                    e.module.getClass().getName(),
                    t.toString());
        }
    }

    public static final class Entry {
        public final String id;
        public final Object api;
        public final ApiModule module;
        public final String name;
        public final String version;

        Entry(String id, Object api, ApiModule module, String name, String version) {
            this.id = id;
            this.api = api;
            this.module = module;
            this.name = name;
            this.version = version;
        }

    }
}