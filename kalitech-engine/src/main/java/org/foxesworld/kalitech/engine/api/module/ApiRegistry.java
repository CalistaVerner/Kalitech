package org.foxesworld.kalitech.engine.api.module;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

    private static String fmt(Entry e) {
        if (e == null) return "null";
        String impl = (e.api != null) ? e.api.getClass().getSimpleName() : "null";
        return "{id=" + e.id + ",name=" + e.name + ",ver=" + e.version + ",impl=" + impl + "}";
    }

    @Deprecated
    public <T> T registerLegacy(String id, T api) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(api, "api");

        Entry prev = map.put(id, new Entry(id, api, null, id, "legacy"));

        if (log.isDebugEnabled()) {
            if (prev == null) {
                log.debug("[api] register legacy id='{}' impl={}", id, api.getClass().getName());
            } else {
                log.debug("[api] replace legacy id='{}' prevImpl={} newImpl={}",
                        id,
                        prev.api != null ? prev.api.getClass().getName() : "null",
                        api.getClass().getName());
            }
        }

        return api;
    }

    public <T extends ApiModule> T register(T module) {
        Objects.requireNonNull(module, "module");

        // attach first (keeps current behavior: module is usable right after register)
        module.attach(ctx);

        final String id = module.id();
        final Entry next = new Entry(id, module, module, module.name(), module.version());

        Entry prev = map.put(id, next);

        if (log.isDebugEnabled()) {
            if (prev == null) {
                log.debug("[api] register id='{}' name='{}' ver='{}' impl={}",
                        id, next.name, next.version, module.getClass().getName());
            } else {
                log.debug("[api] replace id='{}' prev={} new={} (prevLegacy={})",
                        id,
                        fmt(prev),
                        fmt(next),
                        prev.isLegacy());
            }
        }

        return module;
    }

    public Entry get(String id) {
        return map.get(id);
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