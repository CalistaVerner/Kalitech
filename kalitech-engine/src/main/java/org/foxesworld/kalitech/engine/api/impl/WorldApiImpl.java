// FILE: org/foxesworld/kalitech/engine/api/impl/WorldApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.WorldApi;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldApiImpl implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private final EngineApiImpl engine;
    private final EcsWorld ecs;

    public WorldApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.ecs = engineApi.getEcs();
    }

    private ScriptEventBus bus() {
        return engine.getBus();
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

        ecs.components().put(id, ScriptComponent.class, new ScriptComponent(a.prefab));

        ScriptEventBus b = bus();
        if (b != null) {
            try {
                b.emit("entity.spawned", new EntitySpawned(id, a.name, a.prefab));
            } catch (Exception ignored) {
            }
        }

        log.debug("world.spawn -> id={} name='{}' prefab={}", id, a.name, a.prefab);
        return id;
    }

    @HostAccess.Export
    @Override
    public int findByName(String name) {
        if (name == null || name.isBlank()) return 0;

        AtomicInteger found = new AtomicInteger(0);
        ecs.components().forEachByName("Name", (id, v) -> {
            if (found.get() != 0) return;
            if (name.equals(String.valueOf(v))) found.set(id);
        });

        return found.get();
    }

    @HostAccess.Export
    @Override
    public void destroy(int id) {
        ecs.destroyEntity(id);
        ScriptEventBus b = bus();
        if (b != null) {
            try {
                b.emit("entity.destroyed", id);
            } catch (Exception ignored) {
            }
        }
    }

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

    private static final class SpawnArgs {
        final String name;
        final String prefab;

        SpawnArgs(String name, String prefab) {
            this.name = name;
            this.prefab = prefab;
        }

        static SpawnArgs parse(Object args) {
            if (args == null) return new SpawnArgs(null, null);

            if (args instanceof Value v) {
                String name = readStr(v, "name");
                String prefab = readStr(v, "prefab");
                return new SpawnArgs(name, prefab);
            }

            if (args instanceof Map<?, ?> m) {
                Object n = m.get("name");
                Object p = m.get("prefab");
                return new SpawnArgs(n != null ? String.valueOf(n) : null, p != null ? String.valueOf(p) : null);
            }

            return new SpawnArgs(null, null);
        }

        private static String readStr(Value v, String key) {
            try {
                if (v.hasMember(key)) {
                    Value x = v.getMember(key);
                    if (x != null && !x.isNull()) return x.asString();
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}