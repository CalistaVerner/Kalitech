package org.foxesworld.kalitech.engine.world;

// Author: KΛYLΛ
/**
 * WorldBuilder
 *
 * Builds a {@link KWorld} from a Lua world description object.
 * Resolves system IDs via {@link SystemRegistry}, sorts by "order", and registers systems into the world
 * using the world’s explicit ordering API (no implicit add).
 *
 * Notes:
 * - Production-friendly: tolerates missing/invalid fields and continues building.
 * - Keeps the original "worldDesc" contract: { name, systems:[{id, order, config}] }.
 */

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemDescriptor;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemType;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.foxesworld.kalitech.engine.script.util.LuaCfg.*;

public final class WorldBuilder {

    private static final Logger log = LogManager.getLogger(WorldBuilder.class);

    private final SimpleApplication app; // reserved for future use
    private final SystemRegistry registry;

    public WorldBuilder(SimpleApplication app, SystemRegistry registry) {
        this.app = Objects.requireNonNull(app, "app");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    private static WorldTimeParams parseWorldTimeParams(LuaValueRef worldDesc) {
        LuaValueRef t = member(worldDesc, "time");
        if (t == null || t.isNull()) return WorldTimeParams.defaults();

        double worldTime = num(t, "worldTime", 0.0);
        double timeRate = num(t, "timeRate", 1.0);
        boolean paused = bool(t, "paused", false);

        Double fixedStep = null;
        LuaValueRef fs = member(t, "fixedStep");
        if (fs != null && !fs.isNull()) {
            double v = fs.fitsInDouble() ? fs.asDouble() : num(t, "fixedStep", 0.0);
            if (Double.isFinite(v) && v > 0.0) fixedStep = v;
        }

        Double maxDelta = null;
        LuaValueRef md = member(t, "maxDelta");
        if (md != null && !md.isNull()) {
            double v = md.fitsInDouble() ? md.asDouble() : num(t, "maxDelta", 0.0);
            if (Double.isFinite(v) && v > 0.0) maxDelta = v;
        }

        return new WorldTimeParams(worldTime, timeRate, paused, fixedStep, maxDelta);
    }

    public KWorld buildFromWorldDesc(SystemContext ctx, LuaValueRef worldDesc) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(worldDesc, "worldDesc");

        String name = str(worldDesc, "name", "main");

        WorldTimeParams time = parseWorldTimeParams(worldDesc);
        KWorld world = new KWorld(name, time);

        LuaValueRef systems = member(worldDesc, "systems");
        if (systems == null || !systems.hasArrayElements()) {
            log.warn("WorldBuilder: worldDesc.systems missing or not an array (world='{}')", name);
            return world;
        }

        List<SystemDef> defs = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> known = new ArrayList<>();
        long n = systems.getArraySize();

        log.info("WorldBuilder: building world='{}' systems={}", name, n);

        Set<String> seenIds = new java.util.HashSet<>();
        for (long i = 0; i < n; i++) {
            LuaValueRef s = systems.getArrayElement(i);
            if (s == null || s.isNull()) continue;

            String id = str(s, "id", null);
            if (id == null || id.isBlank()) continue;
            id = id.trim();
            if (!seenIds.add(id)) {
                duplicates.add(id);
                continue;
            }

            int order = (int) num(s, "order", 0);
            LuaValueRef config = member(s, "config");
            defs.add(new SystemDef(id, order, config));
        }

        defs.sort(Comparator.comparingInt(d -> d.order));

        for (SystemDef d : defs) {
            SystemDescriptor descriptor = registry.descriptor(d.id);
            if (descriptor == null) {
                unknown.add(d.id);
            } else {
                known.add(d.id);
            }

            KSystem sys = registry.create(d.id, ctx, d.config);
            if (sys == null) {
                log.warn("WorldBuilder: registry returned null system for id={} (skipping)", d.id);
                continue;
            }

            world.addSystem(sys, d.order);
            added.add(d.id);

            if (descriptor != null) {
                log.info("WorldBuilder: added system id={} order={} type={} module={}",
                        d.id, d.order, descriptor.type(), descriptor.module());
            } else {
                log.info("WorldBuilder: added system id={} order={}", d.id, d.order);
            }
        }

        if (!duplicates.isEmpty()) {
            log.warn("WorldBuilder: duplicate system ids skipped: {}", duplicates);
        }
        if (!unknown.isEmpty()) {
            log.warn("WorldBuilder: unknown system ids: {}", unknown);
        }
        log.info("WorldBuilder: summary world='{}' added={} known={} total={}",
                name, added.size(), known.size(), defs.size());
        log.info("WorldBuilder: registry types summary={}", summarizeTypes());

        return world;
    }

    private static final class SystemDef {
        final String id;
        final int order;
        final LuaValueRef config;

        SystemDef(String id, int order, LuaValueRef config) {
            this.id = id;
            this.order = order;
            this.config = config;
        }
    }

    private String summarizeTypes() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<SystemType, List<SystemDescriptor>> entry
                : registry.descriptorsByType().entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey()).append('=').append(entry.getValue().size());
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }
}
