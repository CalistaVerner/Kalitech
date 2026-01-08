package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.state.AppStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.WorldApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldApiImpl extends AbstractApiModule implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    public WorldApiImpl() {
        super("world", "World", "1.0.0");
    }

    private static Value requireMember(Value obj, String key, String err) {
        if (!obj.hasMember(key)) throw new IllegalArgumentException(err);
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) throw new IllegalArgumentException(err);
        return v;
    }

    private static String requireStr(Value obj, String key, String err) {
        final Value v = requireMember(obj, key, err);
        if (!v.isString()) throw new IllegalArgumentException(err);
        final String s = v.asString();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(err);
        return s;
    }

    private static String readStr(Value obj, String key, String def) {
        if (!obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isString()) throw new IllegalArgumentException("world desc." + key + " must be a string");
        final String s = v.asString();
        return (s == null || s.isBlank()) ? def : s;
    }

    // =========================================================================
    // World create (STRICT, no legacy, no reflection)
    // =========================================================================

    private static boolean readBool(Value obj, String key, boolean def) {
        if (!obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isBoolean()) throw new IllegalArgumentException("world desc." + key + " must be a boolean");
        return v.asBoolean();
    }

    private static int readInt(Value obj, String key, int def) {
        if (!obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isNumber()) throw new IllegalArgumentException("world system." + key + " must be a number");
        return v.fitsInInt() ? v.asInt() : def;
    }

    // =========================================================================
    // Existing API: spawn/find/destroy (kept stable)
    // =========================================================================

    private static Object toProxy(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();

        if (v.hasArrayElements()) {
            final int len = (int) Math.min(v.getArraySize(), Integer.MAX_VALUE);
            final Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) arr[i] = toProxy(v.getArrayElement(i));
            return ProxyArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) {
                map.put(k, toProxy(v.getMember(k)));
            }
            return ProxyObject.fromMap(map);
        }

        return v;
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.engine = Objects.requireNonNull(ctx.engine, "ctx.engine");
        this.ecs = Objects.requireNonNull(engine.getEcs(), "engine.ecs");
    }

    private ScriptEventBus busOrNull() {
        return engine.getBus();
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

    // =========================================================================
    // Strict parsing helpers
    // =========================================================================

    private void emit(String name, Object payload) {
        final ScriptEventBus b = busOrNull();
        if (b == null) return;
        b.emit(name, payload);
    }

    /**
     * Contract:
     * engine.world().create({
     *   name: "main",             // optional (default "world")
     *   start: true,              // optional (default true)
     *   systems: [
     *     {
     *       id: "jsSystem",
     *       order: 10,
     *       config: {
     *         module: "Scripts/....js",
     *         profile: "world",     // optional
     *         config: { ... }       // optional, passed to JS create(ctx,cfg)
     *       }
     *     }
     *   ]
     * })
     */
    @HostAccess.Export
    public void create(Value desc) {
        if (desc == null || desc.isNull()) {
            throw new IllegalArgumentException("world.create(desc): desc is required");
        }

        final String name = readStr(desc, "name", "world");
        final boolean start = readBool(desc, "start", true);

        final Value systems = requireMember(desc, "systems", "world.create: desc.systems[] is required");
        if (!systems.hasArrayElements()) {
            throw new IllegalArgumentException("world.create: desc.systems must be an array");
        }

        final KWorld world = new KWorld(name);

        final long n = systems.getArraySize();
        for (long i = 0; i < n; i++) {
            final Value it = systems.getArrayElement(i);
            if (it == null || it.isNull() || !it.hasMembers()) {
                throw new IllegalArgumentException("world.create: systems[" + i + "] must be an object");
            }

            final String id = requireStr(it, "id", "world.create: systems[" + i + "].id is required");
            if (!"jsSystem".equals(id)) {
                throw new IllegalArgumentException("world.create: systems[" + i + "].id must be 'jsSystem' (got '" + id + "')");
            }

            final int order = readInt(it, "order", 0);

            final Value cfg = requireMember(it, "config", "world.create: systems[" + i + "].config is required");
            if (!cfg.hasMembers()) {
                throw new IllegalArgumentException("world.create: systems[" + i + "].config must be an object");
            }

            final String module = requireStr(cfg, "module", "world.create: systems[" + i + "].config.module is required");
            final String profile = readStr(cfg, "profile", "world").trim();

            final Value inner = (cfg.hasMember("config") ? cfg.getMember("config") : null);
            final Object cfgJs = (inner == null || inner.isNull()) ? null : toProxy(inner);

            final Map<String, Object> sysDesc = new LinkedHashMap<>();
            sysDesc.put("id", "jsSystem");
            sysDesc.put("order", order);
            sysDesc.put("module", module);
            sysDesc.put("profile", profile);
            sysDesc.put("config", cfgJs);

            world.addSystem(new JsWorldSystem(module, cfgJs, ProxyObject.fromMap(sysDesc), profile), order);
        }

        final WorldAppState wa = requireWorldAppState();
        wa.createWorld(world, start);

        emit("world.created", Map.of("name", name, "started", start, "systems", (int) Math.min(n, Integer.MAX_VALUE)));
        log.info("[world.create] name={} systems={} start={}", name, n, start);
    }

    private WorldAppState requireWorldAppState() {
        final var app = engine.getApp();
        if (app == null) throw new IllegalStateException("world.create: engine app is null");
        final AppStateManager sm = app.getStateManager();
        if (sm == null) throw new IllegalStateException("world.create: AppStateManager is null");
        final WorldAppState wa = sm.getState(WorldAppState.class);
        if (wa == null) throw new IllegalStateException("world.create: WorldAppState not attached");
        return wa;
    }

    @HostAccess.Export
    @Override
    public int spawn(Value args) {
        if (args == null || args.isNull()) {
            throw new IllegalArgumentException("world.spawn(args): args is required");
        }

        final String prefab = requireStr(args, "prefab", "world.spawn({prefab}): prefab is required");
        final String name = readStr(args, "name", null);

        final int id = ecs.createEntity();

        if (name != null && !name.isBlank()) {
            ecs.components().putByName(id, "Name", name);
        }

        ecs.components().put(id, ScriptComponent.class, new ScriptComponent(prefab));

        emit("entity.spawned", new EntitySpawned(id, name, prefab));
        log.debug("world.spawn -> id={} name='{}' prefab={}", id, name, prefab);

        return id;
    }

    @HostAccess.Export
    @Override
    public int findByName(String name) {
        if (name == null || name.isBlank()) return 0;

        final AtomicInteger found = new AtomicInteger(0);
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
        emit("entity.destroyed", id);
    }
}