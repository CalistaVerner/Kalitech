// FILE: org/foxesworld/kalitech/engine/world/WorldCreateParams.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

import java.util.List;
import java.util.Objects;

/**
 * Immutable world creation descriptor coming from script-side manifest.
 * Java must treat it as the source of truth and only enforce safety.
 */
public final class WorldCreateParams {

    public final String name;
    public final boolean start;
    public final WorldTimeParams time;

    /**
     * Systems and entities are passed as raw objects/records in your pipeline.
     * Keep this DTO stable; conversion happens in World builder layer.
     */
    public final List<?> systems;
    public final List<?> entities;

    public WorldCreateParams(String name, boolean start, WorldTimeParams time, List<?> systems, List<?> entities) {
        this.name = (name == null || name.isBlank()) ? "world" : name.trim();
        this.start = start;
        this.time = Objects.requireNonNullElse(time, WorldTimeParams.defaults());
        this.systems = (systems != null) ? systems : List.of();
        this.entities = (entities != null) ? entities : List.of();
    }
}