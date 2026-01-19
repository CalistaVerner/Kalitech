package org.foxesworld.kalitech.engine.modules.rig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigProfileRegistry
 *
 * Thread-safe immutable snapshot registry.
 * You can swap snapshots on hot-reload without mutating existing maps.
 */
public final class RigProfileRegistry {

    private volatile Snapshot snapshot = new Snapshot(Map.of());

    public RigProfileRegistry() {
    }

    public RigProfile get(String id) {
        if (id == null) return null;
        return snapshot.map.get(id.trim());
    }

    public RigProfile require(String id) {
        RigProfile p = get(id);
        if (p == null) throw new IllegalArgumentException("Unknown RigProfile id: " + id);
        return p;
    }

    public Map<String, RigProfile> all() {
        return snapshot.map;
    }

    public void replaceAll(Map<String, RigProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles");
        this.snapshot = new Snapshot(profiles);
    }

    private static final class Snapshot {
        final Map<String, RigProfile> map;

        Snapshot(Map<String, RigProfile> src) {
            Map<String, RigProfile> m = new LinkedHashMap<>();
            for (Map.Entry<String, RigProfile> e : src.entrySet()) {
                String id = (e.getKey() == null) ? "" : e.getKey().trim();
                if (id.isEmpty()) continue;
                RigProfile p = e.getValue();
                if (p == null) continue;
                if (!id.equals(p.id)) {
                    throw new IllegalArgumentException("RigProfile key '" + id + "' != profile.id '" + p.id + "'");
                }
                if (m.containsKey(id)) {
                    throw new IllegalStateException("Duplicate RigProfile id: " + id);
                }
                m.put(id, p);
            }
            this.map = Collections.unmodifiableMap(m);
        }
    }
}