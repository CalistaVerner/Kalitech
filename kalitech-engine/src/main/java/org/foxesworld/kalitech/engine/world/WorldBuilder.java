package org.foxesworld.kalitech.engine.world;

import com.jme3.app.SimpleApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemRegistry;

import java.util.*;

public final class WorldBuilder {

    private static final Logger log = LogManager.getLogger(WorldBuilder.class);

    private final SimpleApplication app; // (на будущее, сейчас не обязателен)
    private final SystemRegistry registry;

    public WorldBuilder(SimpleApplication app, SystemRegistry registry) {
        this.app = Objects.requireNonNull(app, "app");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public KWorld buildFromWorldDesc(SystemContext ctx, Value worldDesc) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(worldDesc, "worldDesc");

        String name = getString(worldDesc, "name", "main");
        KWorld world = new KWorld(name);

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

            String id = getString(s, "id", null);
            if (id == null || id.isBlank()) continue;

            int order = getInt(s, "order", 0);
            Value config = member(s, "config");
            defs.add(new SystemDef(id, order, config));
        }

        defs.sort(Comparator.comparingInt(d -> d.order));

        for (SystemDef d : defs) {
            KSystem sys = registry.create(d.id, ctx, d.config);
            world.addSystem(sys); // <-- твой API
            log.info("WorldBuilder: added system id={} order={}", d.id, d.order);
        }

        return world;
    }

    private static Value member(Value obj, String name) {
        if (obj == null || obj.isNull()) return null;
        if (!obj.hasMember(name)) return null;
        Value v = obj.getMember(name);
        return (v == null || v.isNull()) ? null : v;
    }

    private static String getString(Value obj, String name, String def) {
        Value v = member(obj, name);
        if (v == null) return def;
        try { return v.asString(); } catch (Exception ignored) { return def; }
    }

    private static int getInt(Value obj, String name, int def) {
        Value v = member(obj, name);
        if (v == null) return def;
        try { return v.asInt(); } catch (Exception ignored) { return def; }
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