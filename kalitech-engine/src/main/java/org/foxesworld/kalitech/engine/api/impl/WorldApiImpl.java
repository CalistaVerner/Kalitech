// FILE: WorldApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.Application;
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
import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.ScriptComponent;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.WorldTimeParams;
import org.foxesworld.kalitech.engine.world.systems.LuaWorldSystem;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorldApiImpl extends AbstractApiModule implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    public WorldApiImpl() {
        super("world", "World", "2.4.1");
    }

    private static LuaValueRef requireMember(LuaValueRef obj, String key, String err) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) {
            throw new IllegalArgumentException(err);
        }
        final LuaValueRef v = obj.getMember(key);
        if (v == null || v.isNull()) throw new IllegalArgumentException(err);
        return v;
    }

    private static String readStr(LuaValueRef obj, String key, String def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final LuaValueRef v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isString()) throw new IllegalArgumentException(errPrefix + key + " must be a string");
        final String s = v.asString();
        return (s == null || s.isBlank()) ? def : s;
    }

    private static boolean readBool(LuaValueRef obj, String key, boolean def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final LuaValueRef v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isBoolean()) throw new IllegalArgumentException(errPrefix + key + " must be a boolean");
        return v.asBoolean();
    }

    private static int readInt(LuaValueRef obj, String key, int def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final LuaValueRef v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isNumber() || !v.fitsInInt()) throw new IllegalArgumentException(errPrefix + key + " must be an int");
        return v.asInt();
    }

    private static double readDouble(LuaValueRef obj, String key, double def, String errPrefix) {
        if (obj == null || obj.isNull() || !obj.hasMembers() || !obj.hasMember(key)) return def;
        final LuaValueRef v = obj.getMember(key);
        if (v == null || v.isNull()) return def;
        if (!v.isNumber()) throw new IllegalArgumentException(errPrefix + key + " must be a number");
        final double x = v.asDouble();
        if (!Double.isFinite(x)) throw new IllegalArgumentException(errPrefix + key + " must be finite");
        return x;
    }

    private static String normalizeProfile(String p) {
        if (p == null) return "world";
        final String t = p.trim();
        return t.isEmpty() ? "world" : t;
    }

    private static String requireStr(LuaValueRef obj, String key, String err) {
        final LuaValueRef v = requireMember(obj, key, err);
        if (!v.isString()) throw new IllegalArgumentException(err);
        final String s = v.asString();
        if (s == null || s.isBlank()) throw new IllegalArgumentException(err);
        return s;
    }

    private static Object toProxy(LuaValueRef v) {
        if (v == null || v.isNull()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();

        if (v.hasArrayElements()) {
            final int len = (int) Math.min(v.getArraySize(), Integer.MAX_VALUE);
            final Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) arr[i] = toProxy(v.getArrayElement(i));
            return LuaArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) map.put(k, toProxy(v.getMember(k)));
            return LuaObject.fromMap(map);
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

    private static WorldTimeParams parseWorldTimeParams(LuaValueRef desc) {
        if (desc == null || desc.isNull() || !desc.hasMembers() || !desc.hasMember("time")) {
            return WorldTimeParams.defaults();
        }

        final LuaValueRef t = desc.getMember("time");
        if (t == null || t.isNull()) return WorldTimeParams.defaults();
        if (!t.hasMembers()) throw new IllegalArgumentException("world.create: desc.time must be an object");

        final double worldTime = readDouble(t, "worldTime", 0.0, "world time.");
        final double timeRate = readDouble(t, "timeRate", 1.0, "world time.");
        final boolean paused = readBool(t, "paused", false, "world time.");

        final Double fixedStep = t.hasMember("fixedStep") && !t.getMember("fixedStep").isNull()
                ? readOptionalPositiveDouble(t, "fixedStep", "world time.")
                : null;

        final Double maxDelta = t.hasMember("maxDelta") && !t.getMember("maxDelta").isNull()
                ? readOptionalPositiveDouble(t, "maxDelta", "world time.")
                : null;

        return new WorldTimeParams(worldTime, timeRate, paused, fixedStep, maxDelta);
    }

    private static Double readOptionalPositiveDouble(LuaValueRef obj, String key, String errPrefix) {
        final double v = readDouble(obj, key, 0.0, errPrefix);
        if (v <= 0.0) return null;
        return v;
    }

    private WorldAppState getWorldAppStateOrNull() {
        final EngineApiImpl e = this.engine;
        if (e == null) return null;

        final var app = e.getApp();
        if (app == null) return null;

        final var sm = app.getStateManager();
        if (sm == null) return null;

        return sm.getState(WorldAppState.class);
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Object getWorldTime() {
        final WorldAppState wa = getWorldAppStateOrNull();
        if (wa == null) return null;

        final KWorld w = wa.getWorldOrNull();
        if (w == null) return null;

        final var t = w.getTime();
        if (t == null) return null;

        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("worldTime", t.getWorldTimeSec());
        out.put("timeRate", t.getTimeRate());
        out.put("paused", t.isPaused());
        out.put("frameIndex", t.getFrameIndex());
        out.put("tickIndex", t.getTickIndex());
        out.put("realDt", t.getLastRealDtSec());
        out.put("simDt", t.getLastSimDtSec());
        out.put("stepDt", t.getLastStepDtSec());
        out.put("interpAlpha", t.interpolationAlpha());

        if (t.fixedStepSec() != null) {
            out.put("fixedStep", t.fixedStepSec());
        }
        if (t.getMaxDeltaSec() != null) {
            out.put("maxDelta", t.getMaxDeltaSec());
        }

        return LuaObject.fromMap(out);
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void create(LuaValueRef desc) {
        if (desc == null || desc.isNull() || !desc.hasMembers()) {
            throw new IllegalArgumentException("world.create(desc): desc object is required");
        }

        final String name = readStr(desc, "name", "world", "world desc.");
        final boolean start = readBool(desc, "start", true, "world desc.");

        final LuaValueRef systems = requireMember(desc, "systems", "world.create: desc.systems[] is required");
        if (!systems.hasArrayElements()) {
            throw new IllegalArgumentException("world.create: desc.systems must be an array");
        }

        final WorldTimeParams timeParams = parseWorldTimeParams(desc);
        final KWorld world = new KWorld(name, timeParams);

        final long n = systems.getArraySize();
        for (long i = 0; i < n; i++) {
            final LuaValueRef it = systems.getArrayElement(i);
            if (it == null || it.isNull() || !it.hasMembers()) {
                throw new IllegalArgumentException("world.create: systems[" + i + "] must be an object");
            }

            final String id = requireStr(it, "id", "world.create: systems[" + i + "].id is required");
            if (!"luaSystem".equals(id)) {
                throw new IllegalArgumentException(
                        "world.create: systems[" + i + "].id must be 'luaSystem' (got '" + id + "')"
                );
            }

            final int order = readInt(it, "order", 0, "world system.");
            final String stableId = readStr(it, "stableId", null, "world system.");

            final LuaValueRef cfg = requireMember(it, "config", "world.create: systems[" + i + "].config is required");
            if (!cfg.hasMembers()) {
                throw new IllegalArgumentException("world.create: systems[" + i + "].config must be an object");
            }

            final String module = requireStr(cfg, "module", "world.create: systems[" + i + "].config.module is required");

            final String runtime = normalizeProfile(
                    readStr(cfg, "runtime", readStr(cfg, "profile", "world", "world cfg."), "world cfg.")
            );

            final Object cfgLua = toProxy(cfg);

            final Map<String, Object> sysDesc = new LinkedHashMap<>();
            sysDesc.put("id", "luaSystem");
            sysDesc.put("order", order);
            sysDesc.put("stableId", stableId);
            sysDesc.put("module", module);
            sysDesc.put("runtime", runtime);
            sysDesc.put("config", cfgLua);

            world.addSystem(new LuaWorldSystem(module, cfgLua, LuaObject.fromMap(sysDesc), runtime), order);
        }

        final WorldAppState wa = ensureWorldAppStateAttached();
        wa.createWorld(world, start);

        emit("world.created", Map.of(
                "name", name,
                "started", start,
                "systems", (int) Math.min(n, Integer.MAX_VALUE)
        ));

        log.info("[world.create] name={} systems={} start={}", name, n, start);
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public String spawn(LuaValueRef args) {
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

    /**
     * Ensures WorldAppState is attached when world.create() is called.
     * This keeps RuntimeAppState free from any world lifecycle responsibilities.
     */
    private WorldAppState ensureWorldAppStateAttached() {
        final EngineApiImpl e = this.engine;
        if (e == null) throw new IllegalStateException("WorldApiImpl is not attached");

        final Application app = e.getApp();
        if (app == null) throw new IllegalStateException("Engine application is null");

        final AppStateManager sm = app.getStateManager();
        if (sm == null) throw new IllegalStateException("AppStateManager is null");

        WorldAppState wa = sm.getState(WorldAppState.class);
        if (wa != null) return wa;

        final RuntimeAppState host = sm.getState(RuntimeAppState.class);
        if (host == null) {
            throw new IllegalStateException("WorldAppState is not attached and RuntimeAppState is missing");
        }

        wa = new WorldAppState(host);
        sm.attach(wa);

        final WorldAppState wa2 = sm.getState(WorldAppState.class);
        if (wa2 == null) {
            throw new IllegalStateException("WorldAppState attach failed");
        }

        log.info("[world] WorldAppState attached lazily");
        return wa2;
    }
}
