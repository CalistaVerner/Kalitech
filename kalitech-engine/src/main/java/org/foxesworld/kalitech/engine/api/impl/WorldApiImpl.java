package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.app.state.AppStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
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

public final class WorldApiImpl extends AbstractApiModule implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    public WorldApiImpl() {
        super("world", "World", "2.0.0");
    }

    private static Value requireMember(Value obj, String key, String err) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) {
            throw new IllegalArgumentException(err);
        }
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) throw new IllegalArgumentException(err);
        return v;
    }

    private static String readStr(Value obj, String key, String def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isString()) throw new IllegalArgumentException(errPrefix + key + " must be a string");
        final String s = v.asString();
        return (s == null || s.isBlank()) ? def : s;
    }

    private static boolean readBool(Value obj, String key, boolean def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isBoolean()) throw new IllegalArgumentException(errPrefix + key + " must be a boolean");
        return v.asBoolean();
    }

    private static int readInt(Value obj, String key, int def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final Value v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isNumber() || !v.fitsInInt()) throw new IllegalArgumentException(errPrefix + key + " must be an int");
        return v.asInt();
    }

    private static String normalizeProfile(String p) {
        if (p == null) return "world";
        final String t = p.trim();
        return t.isEmpty() ? "world" : t;
    }

    private static String requireStr(Value obj, String key, String err) {
        final Value v = requireMember(obj, key, err);
        if (!v.isString()) throw new IllegalArgumentException(err);
        final String s = v.asString();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(err);
        return s;
    }

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
            for (String k : v.getMemberKeys()) map.put(k, toProxy(v.getMember(k)));
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

    @Override
    public void detach() {
        this.ecs = null;
        this.engine = null;
        super.detach();
    }

    private ScriptEventBus busOrNull() {
        final EngineApiImpl e = this.engine;
        return e != null ? e.getBus() : null;
    }

    private void emit(String name, Object payload) {
        final ScriptEventBus b = busOrNull();
        if (b != null) b.emit(name, payload);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void create(Value desc) {
        if (desc == null || desc.isNull() || !desc.hasMembers()) {
            throw new IllegalArgumentException("world.create(desc): desc object is required");
        }

        final String name = readStr(desc, "name", "world", "world desc.");
        final boolean start = readBool(desc, "start", true, "world desc.");

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

            final int order = readInt(it, "order", 0, "world system.");
            final String stableId = readStr(it, "stableId", null, "world system.");

            final Value cfg = requireMember(it, "config", "world.create: systems[" + i + "].config is required");
            if (!cfg.hasMembers()) {
                throw new IllegalArgumentException("world.create: systems[" + i + "].config must be an object");
            }

            final String module = requireStr(cfg, "module", "world.create: systems[" + i + "].config.module is required");

            final String runtime = normalizeProfile(
                    readStr(cfg, "runtime", readStr(cfg, "profile", "world", "world cfg."), "world cfg.")
            );

            final Object cfgJs = toProxy(cfg);

            final Map<String, Object> sysDesc = new LinkedHashMap<>();
            sysDesc.put("id", "jsSystem");
            sysDesc.put("order", order);
            sysDesc.put("stableId", stableId);
            sysDesc.put("module", module);
            sysDesc.put("runtime", runtime);
            sysDesc.put("config", cfgJs);

            world.addSystem(new JsWorldSystem(module, cfgJs, ProxyObject.fromMap(sysDesc), runtime), order);
        }

        final WorldAppState wa = requireWorldAppState();
        wa.createWorld(world, start);

        emit("world.created", Map.of(
                "name", name,
                "started", start,
                "systems", (int) Math.min(n, Integer.MAX_VALUE)
        ));

        log.info("[world.create] name={} systems={} start={}", name, n, start);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public String spawn(Value args) {
        if (args == null || args.isNull() || !args.hasMembers()) {
            throw new IllegalArgumentException("world.spawn(args): args object is required");
        }

        final String prefab = requireStr(args, "prefab", "world.spawn: args.prefab is required");
        final String name = readStr(args, "name", null, "world.spawn args.");

        final String uuid = ecs.createEntity();

        if (name != null) {
            final String n = name.trim();
            if (!n.isEmpty()) ecs.putComponentByName(uuid, "Name", n);
        }

        ecs.putComponent(uuid, ScriptComponent.class, new ScriptComponent(prefab));

        emit("entity.spawned", Map.of("uuid", uuid, "name", name, "prefab", prefab));
        if (log.isDebugEnabled()) log.debug("[world.spawn] uuid={} name='{}' prefab={}", uuid, name, prefab);

        return uuid;
    }

    @Override
    public String findByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("world.findByName(name): name is required");
        }

        final String target = name.trim();
        final String[] found = new String[1];

        ecs.components().forEachByName("Name", (entityId, value) -> {
            if (found[0] != null) return;
            if (!(value instanceof String v)) return;
            if (!target.equals(v)) return;

            String uuid = ecs.uuids().uuidStringOf(entityId);
            if (uuid != null && !uuid.isBlank()) found[0] = uuid;
        });

        return (found[0] != null) ? found[0] : "";
    }

    @Override
    public void destroy(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("world.destroy(uuid): uuid is required");
        }

        engine.__surfaceCleanupOnEntityDestroy(uuid);
        ecs.destroyEntity(uuid);
        emit("entity.destroyed", Map.of("uuid", uuid));
        if (log.isDebugEnabled()) log.debug("[world.destroy] uuid={}", uuid);
    }

    private WorldAppState requireWorldAppState() {
        final var app = engine.getApp();
        final AppStateManager sm = app.getStateManager();
        final WorldAppState wa = sm.getState(WorldAppState.class);
        if (wa == null) throw new IllegalStateException("WorldAppState is not attached");
        return wa;
    }
}