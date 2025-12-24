package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.interfaces.WorldApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Map;
import java.util.Objects;

/**
 * JS-first World facade.
 * Java is skeleton: validates, spawns, stores minimal components.
 * JS is matter: prefab script decides behavior (via ScriptSystem).
 */
public final class WorldApiImpl implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private final EcsWorld ecs;
    private final ScriptEventBus bus;

    public WorldApiImpl(EngineApiImpl engineApi) {
        this.ecs = engineApi.getEcs();
        this.bus = engineApi.getBus();
    }

    @HostAccess.Export
    @Override
    public int spawn(Object args) {
        SpawnArgs a = SpawnArgs.parse(args);

        if (a.prefab == null || a.prefab.isBlank()) {
            throw new IllegalArgumentException("world.spawn({prefab}) prefab is required");
        }

        int id = ecs.createEntity();

        if (a.name != null && !a.name.isBlank()) {
            ecs.components().putByName(id, "Name", a.name);
        }

        // ScriptSystem will pick this up and create instance
        ecs.components().put(id, ScriptComponent.class, new ScriptComponent(a.prefab));

        // emit event (optional)
        try {
            bus.emit("entity.spawned", new EntitySpawned(id, a.name, a.prefab));
        } catch (Exception ignored) {}

        log.debug("world.spawn -> id={} name='{}' prefab={}", id, a.name, a.prefab);
        return id;
    }

    @HostAccess.Export
    @Override
    public int findByName(String name) {
        if (name == null || name.isBlank()) return 0;
        Map<Integer, Object> names = ecs.components().viewByName("Name");
        for (var e : names.entrySet()) {
            if (name.equals(e.getValue())) return e.getKey();
        }
        return 0;
    }

    @HostAccess.Export
    @Override
    public void destroy(int id) {
        ecs.destroyEntity(id);
        try { bus.emit("entity.destroyed", id); } catch (Exception ignored) {}
    }

    /** Payload class visible to JS (fields). */
    public static final class EntitySpawned {
        public final int id;
        public final String name;
        public final String prefab;

        public EntitySpawned(int id, String name, String prefab) {
            this.id = id;
            this.name = name;
            this.prefab = prefab;
        }
    }

    // ---------------- parsing ----------------

    private static final class SpawnArgs {
        final String name;
        final String prefab;

        SpawnArgs(String name, String prefab) {
            this.name = name;
            this.prefab = prefab;
        }

        static SpawnArgs parse(Object args) {
            if (args == null) return new SpawnArgs(null, null);

            // Graal Value
            if (args instanceof Value v) {
                String name = readStr(v, "name");
                String prefab = readStr(v, "prefab");
                return new SpawnArgs(name, prefab);
            }

            // Map (if someone calls from Java)
            if (args instanceof Map<?, ?> m) {
                Object n = m.get("name");
                Object p = m.get("prefab");
                return new SpawnArgs(n == null ? null : String.valueOf(n), p == null ? null : String.valueOf(p));
            }

            // Allow passing prefab as string directly: world.spawn("Scripts/entities/player.js")
            if (args instanceof String s) {
                return new SpawnArgs(null, s);
            }

            throw new IllegalArgumentException("world.spawn(args) expects object {name,prefab} or string prefab");
        }

        private static String readStr(Value v, String key) {
            try {
                if (v.hasMember(key)) {
                    Value m = v.getMember(key);
                    if (m != null && !m.isNull()) return m.asString();
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}