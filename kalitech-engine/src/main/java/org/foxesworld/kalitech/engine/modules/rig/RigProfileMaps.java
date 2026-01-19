package org.foxesworld.kalitech.engine.modules.rig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigProfileMaps
 *
 * Pure helpers to build immutable registry snapshots.
 */
public final class RigProfileMaps {

    private RigProfileMaps() {
    }

    public static Map<String, RigProfile> copyAndPut(Map<String, RigProfile> base, RigProfile p) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(p, "p");
        Map<String, RigProfile> m = new LinkedHashMap<>(base);
        m.put(p.id, p);
        return m;
    }

    public static Map<String, RigProfile> copyAndPutAll(Map<String, RigProfile> base, Map<String, RigProfile> add) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(add, "add");
        Map<String, RigProfile> m = new LinkedHashMap<>(base);
        m.putAll(add);
        return m;
    }
}