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
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldApiImpl extends AbstractApiModule implements WorldApi {

    private static final Logger log = LogManager.getLogger(WorldApiImpl.class);

    private EngineApiImpl engine;
    private EcsWorld ecs;

    // --- Module ctor (for ApiRegistry.register(new WorldApiImpl())) ---
    public WorldApiImpl() {
        super("world", "World", "1.0.0");
    }

    // --- Legacy ctor (kept for compatibility; no logic changes) ---
    public WorldApiImpl(EngineApiImpl engineApi) {
        this();
        bind(engineApi);
    }

    // --- Module lifecycle ---
    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        bind(ctx.engine);
    }

    private void bind(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engineApi");
        this.ecs = engineApi.getEcs();
    }

    private ScriptEventBus bus() {
        return engine.getBus();
    }

    // =========================================================================
    // ✅ NEW: create world from script descriptor (script-driven)
    // =========================================================================

    /**
     * Supports different WorldAppState implementations without “magic must-have” coupling.
     * Tries (in order):
     * - createWorld(KWorld, boolean)
     * - createWorld(KWorld)
     * - setWorld(KWorld, boolean)
     * - setWorld(KWorld)
     */
    private static void attachWorldViaReflection(WorldAppState wa, KWorld world, boolean start) {
        Objects.requireNonNull(wa, "wa");
        Objects.requireNonNull(world, "world");

        final Class<?> c = wa.getClass();

        if (tryInvoke(c, wa, "createWorld", new Class<?>[]{KWorld.class, boolean.class}, new Object[]{world, start}))
            return;
        if (tryInvoke(c, wa, "createWorld", new Class<?>[]{KWorld.class}, new Object[]{world})) return;

        if (tryInvoke(c, wa, "setWorld", new Class<?>[]{KWorld.class, boolean.class}, new Object[]{world, start}))
            return;
        if (tryInvoke(c, wa, "setWorld", new Class<?>[]{KWorld.class}, new Object[]{world})) return;

        throw new IllegalStateException("WorldAppState has no supported attach method (createWorld/setWorld)");
    }

    private static boolean tryInvoke(Class<?> c, Object target, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = c.getMethod(name, sig);
            m.invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable t) {
            throw new RuntimeException("WorldAppState." + name + " invocation failed: " + t, t);
        }
    }

    private static int safeCount(Object systems) {
        try {
            if (systems == null) return 0;
            if (systems instanceof Value v && v.hasArrayElements())
                return (int) Math.min(v.getArraySize(), Integer.MAX_VALUE);
            if (systems instanceof Collection<?> c) return c.size();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Object readAny(Object o, String key) {
        if (o == null || key == null) return null;

        if (o instanceof Value v) {
            try {
                if (v.hasMember(key)) {
                    Value x = v.getMember(key);
                    return (x == null || x.isNull()) ? null : x;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        if (o instanceof Map<?, ?> m) {
            return m.get(key);
        }

        return null;
    }

    private static String readStr(Object o, String key, String def) {
        Object x = readAny(o, key);
        if (x == null) return def;
        if (x instanceof Value v) {
            try {
                return v.isNull() ? def : v.asString();
            } catch (Throwable ignored) {
                return def;
            }
        }
        String s = String.valueOf(x);
        return (s == null || s.isBlank()) ? def : s;
    }

    private static boolean readBool(Object o, String key, boolean def) {
        Object x = readAny(o, key);
        if (x == null) return def;
        if (x instanceof Value v) {
            try {
                return v.isBoolean() ? v.asBoolean() : def;
            } catch (Throwable ignored) {
                return def;
            }
        }
        if (x instanceof Boolean b) return b;
        return def;
    }

    // =========================================================================
    // Existing API: spawn/find/destroy
    // =========================================================================

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

    // =========================================================================
    // Parsing helpers (Value/Map/Object)
    // =========================================================================

    private static int readInt(Object o, String key, int def) {
        Object x = readAny(o, key);
        if (x == null) return def;
        if (x instanceof Value v) {
            try {
                return v.fitsInInt() ? v.asInt() : def;
            } catch (Throwable ignored) {
                return def;
            }
        }
        if (x instanceof Number n) return n.intValue();
        return def;
    }

    private static List<Object> toList(Object v) {
        if (v == null) return List.of();

        if (v instanceof Value arr) {
            if (!arr.hasArrayElements()) return List.of();
            int n = (int) Math.min(arr.getArraySize(), Integer.MAX_VALUE);
            ArrayList<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                try {
                    Value e = arr.getArrayElement(i);
                    out.add(e);
                } catch (Throwable ignored) {
                }
            }
            return out;
        }

        if (v instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }

        return List.of();
    }

    /**
     * Script contract (minimal):
     * engine.world().create({
     * name: "main",
     * start: true,               // optional
     * systems: [ {id:"jsSystem", order:10, config:{module:"Scripts/..."}} ],
     * entities: []               // optional; ignored here (spawn is separate)
     * })
     */
    @HostAccess.Export
    public void create(Object desc) {
        final Object d = (desc instanceof Value v) ? v : desc;

        final String name = readStr(d, "name", "world");
        final boolean start = readBool(d, "start", true);

        // Build world
        final KWorld world = new KWorld(name);

        // systems[]: keep it ultra-simple: only jsSystem for now
        Object systems = readAny(d, "systems");
        if (systems != null) {
            List<Object> list = toList(systems);
            for (Object it : list) {
                String id = readStr(it, "id", null);
                int order = readInt(it, "order", 0);

                // support legacy "provider" too
                if (id == null || id.isBlank()) id = readStr(it, "provider", null);

                if ("jsSystem".equals(id) || "js".equals(id) || "script".equals(id)) {
                    Object cfg = readAny(it, "config");
                    if (cfg == null) cfg = readAny(it, "cfg");

                    // minimal required: module
                    String module = readStr(cfg, "module", null);
                    if (module == null || module.isBlank()) {
                        throw new IllegalArgumentException("world.create: jsSystem requires config.module");
                    }

                    // sysDesc (optional, but super handy for scripts)
                    Map<String, Object> sysDesc = new LinkedHashMap<>();
                    sysDesc.put("id", "jsSystem");
                    sysDesc.put("module", module);
                    sysDesc.put("order", order);
                    sysDesc.put("config", cfg);

                    world.addSystem(new JsWorldSystem(module, cfg, ProxyObject.fromMap(sysDesc), "world"), order);
                } else {
                    // For "clean boot": ignore unknown providers (no hard dependency, no template must-have)
                    // You can make this strict later.
                    log.warn("[world.create] unknown system provider id='{}' (ignored)", id);
                }
            }
        }

        // Hand-off to WorldAppState if present (world subsystem is optional)
        final WorldAppState wa = findWorldAppState();
        if (wa == null) {
            // optional subsystem => don't crash, just signal
            log.warn("[world.create] WorldAppState not attached. World created but not running. name={}", name);
            emit("world.created", Map.of("name", name, "started", false));
            return;
        }

        attachWorldViaReflection(wa, world, start);

        emit("world.created", Map.of("name", name, "started", start));
        log.info("[world.create] name={} systems={} start={}", name, safeCount(systems), start);
    }

    private WorldAppState findWorldAppState() {
        try {
            var app = engine.getApp();
            if (app == null) return null;
            AppStateManager sm = app.getStateManager();
            if (sm == null) return null;
            return sm.getState(WorldAppState.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private void emit(String name, Object payload) {
        ScriptEventBus b = bus();
        if (b == null) return;
        try {
            b.emit(name, payload);
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // SpawnArgs (unchanged)
    // =========================================================================

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
                String name = readStr(v, "name", null);
                String prefab = readStr(v, "prefab", null);
                return new SpawnArgs(name, prefab);
            }

            if (args instanceof Map<?, ?> m) {
                Object n = m.get("name");
                Object p = m.get("prefab");
                return new SpawnArgs(n != null ? String.valueOf(n) : null, p != null ? String.valueOf(p) : null);
            }

            return new SpawnArgs(null, null);
        }
    }
}