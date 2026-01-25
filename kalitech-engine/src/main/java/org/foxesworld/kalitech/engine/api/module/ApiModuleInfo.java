package org.foxesworld.kalitech.engine.api.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiModuleInfo {
    public final String id;
    public final String name;
    public final String version;
    public final boolean legacy;
    public final String impl;
    public final ApiStatsSnapshot stats;

    private ApiModuleInfo(String id,
                          String name,
                          String version,
                          boolean legacy,
                          String impl,
                          ApiStatsSnapshot stats) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.legacy = legacy;
        this.impl = impl;
        this.stats = stats;
    }

    static ApiModuleInfo from(ApiRegistry.Entry entry) {
        if (entry == null) return null;
        ApiStatsSnapshot stats = entry.module != null ? ApiStatsSnapshot.from(entry.module.stats()) : null;
        String impl = entry.api != null ? entry.api.getClass().getName() : "null";
        return new ApiModuleInfo(entry.id, entry.name, entry.version, entry.isLegacy(), impl, stats);
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("version", version);
        out.put("legacy", legacy);
        out.put("impl", impl);
        if (stats != null) {
            LinkedHashMap<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("calls", stats.calls);
            statsMap.put("errors", stats.errors);
            statsMap.put("avgMicros", stats.avgMicros);
            statsMap.put("maxMicros", stats.maxMicros);
            statsMap.put("nanosTotal", stats.nanosTotal);
            statsMap.put("nanosMax", stats.nanosMax);
            out.put("stats", statsMap);
        } else {
            out.put("stats", null);
        }
        return Collections.unmodifiableMap(out);
    }
}
