package org.foxesworld.kalitech.engine.world;

// Author: KΛYLΛ
/**
 * WorldBuilder
 *
 * Builds a {@link KWorld} from a JS/Polyglot world description object.
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
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class WorldBuilder {

    private static final Logger log = LogManager.getLogger(WorldBuilder.class);

    private final SimpleApplication app; // reserved for future use
    private final SystemRegistry registry;

    public WorldBuilder(SimpleApplication app, SystemRegistry registry) {
        this.app = Objects.requireNonNull(app, "app");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    private static WorldTimeParams parseWorldTimeParams(Value worldDesc) {
        Value t = member(worldDesc, "time");
        if (t == null || t.isNull()) return WorldTimeParams.defaults();

        double worldTime = num(t, "worldTime", 0.0);
        double timeRate = num(t, "timeRate", 1.0);
        boolean paused = bool(t, "paused", false);

        Double fixedStep = null;
        Value fs = member(t, "fixedStep");
        if (fs != null && !fs.isNull()) {
            double v = fs.fitsInDouble() ? fs.asDouble() : num(t, "fixedStep", 0.0);
            if (Double.isFinite(v) && v > 0.0) fixedStep = v;
        }

        Double maxDelta = null;
        Value md = member(t, "maxDelta");
        if (md != null && !md.isNull()) {
            double v = md.fitsInDouble() ? md.asDouble() : num(t, "maxDelta", 0.0);
            if (Double.isFinite(v) && v > 0.0) maxDelta = v;
        }

        return new WorldTimeParams(
                worldTime,
                timeRate,
                paused,
                fixedStep,
                maxDelta,
                member(t, "daySEconds").asDouble(),
                member(t, "dayLength").asDouble(),
                member(t, "dayIndex").asInt(),
                member(t, "timeOfDaySec").asDouble()
        );
    }

    public KWorld buildFromWorldDesc(SystemContext ctx, Value worldDesc) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(worldDesc, "worldDesc");

        String name = str(worldDesc, "name", "main");

        WorldTimeParams time = parseWorldTimeParams(worldDesc);
        KWorld world = new KWorld(name, time);

        Value systems = member(worldDesc, "systems");
        if (systems == null || !systems.hasArrayElements()) {
            log.warn("WorldBuilder: worldDesc.systems missing or not an array");
            return world;
        }

        List<SystemDef> defs = new ArrayList<>();
        long n = systems.getArraySize();

        for (long i = 0; i < n; i++) {
            Value s = systems.getArrayElement(i);
            if (s == null || s.isNull()) continue;

            String id = str(s, "id", null);
            if (id == null || id.isBlank()) continue;

            int order = (int) num(s, "order", 0);
            Value config = member(s, "config");
            defs.add(new SystemDef(id, order, config));
        }

        defs.sort(Comparator.comparingInt(d -> d.order));

        for (SystemDef d : defs) {
            KSystem sys = registry.create(d.id, ctx, d.config);
            if (sys == null) {
                log.warn("WorldBuilder: registry returned null system for id={} (skipping)", d.id);
                continue;
            }

            world.addSystem(sys, d.order);

            log.info("WorldBuilder: added system id={} order={}", d.id, d.order);
        }

        return world;
    }

    private static final class SystemDef {
        final String id;
        final int order;
        final Value config;

        SystemDef(String id, int order, Value config) {
            this.id = id;
            this.order = order;
            this.config = config;
        }
    }
}