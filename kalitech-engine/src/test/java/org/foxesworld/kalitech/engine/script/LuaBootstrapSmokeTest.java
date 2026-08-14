package org.foxesworld.kalitech.engine.script;

import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaBootstrapSmokeTest {

    @Test
    void builtinsInitializeAndStartApplicationEntrypoint() throws Exception {
        Path repo = Path.of(System.getProperty("kalitech.repoRoot")).toAbsolutePath().normalize();
        Path assets = repo.resolve("assets");
        EngineApi engine = fakeInterface(EngineApi.class, new ConcurrentHashMap<>());
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            runtime.setModuleStreamProvider(moduleId -> {
                Path file = assets.resolve(moduleId).normalize();
                if (!file.startsWith(assets) || !Files.isRegularFile(file)) return null;
                return Files.newInputStream(file);
            });
            runtime.initBuiltIns(engine);

            LuaValueRef bootstrap = runtime.require("@builtin/init.lua");
            assertNotNull(bootstrap);
            assertTrue(bootstrap.hasMember("attachEngine"));
            assertTrue(bootstrap.getMember("attachEngine").canExecute());
            LuaValueRef engineRoot = LuaValueRef.of(runtime.globals().get("ENGINE"));
            assertTrue(engineRoot.hasMember("world"), "ENGINE.world module was not registered");
            LuaValueRef material = engineRoot.getMember("material");
            assertTrue(material.hasMember("getMaterial"),
                    "ENGINE.material must be the registered Lua module, not a host method");
            LuaValueRef terrain = engineRoot.getMember("terrain");
            assertTrue(terrain.hasMember("create"),
                    "ENGINE.terrain must be the registered Lua module, not a host method");

            assertDoesNotThrow(() -> runtime.globals().load("""
                    local builder = ENGINE.mesh["box$"](ENGINE.mesh)
                    builder:size(2):name("smoke-box"):pos(1, 2, 3)
                    local cfg = builder:cfg()
                    assert(cfg.type == "box")
                    assert(cfg.size == 2)
                    assert(cfg.name == "smoke-box")
                    assert(cfg.pos[1] == 1 and cfg.pos[2] == 2 and cfg.pos[3] == 3)
                    """, "@test/mesh-builder").call());

            LuaValueRef app = runtime.require("Scripts/main.lua");
            assertTrue(app.hasMember("start"));
            assertTrue(app.getMember("start").canExecute());
            runtime.globals().set("APP_MAIN", app.asLuaValue());
            assertDoesNotThrow(() -> runtime.globals().load("""
                    local originalWorld = ENGINE.world
                    local builder = {}
                    function builder:systems(_) return self end
                    function builder:time(_) return self end
                    function builder:build() return {} end
                    ENGINE.world = {
                        env = function(_, cfg) return cfg end,
                        ["$"] = function() return builder end,
                        create = function() end
                    }
                    APP_MAIN.start()
                    ENGINE.world = originalWorld
                    """, "@test/application-start").call());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fakeInterface(Class<T> type, Map<Class<?>, Object> cache) {
        return (T) cache.computeIfAbsent(type, key -> Proxy.newProxyInstance(
                key.getClassLoader(),
                new Class<?>[]{key},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "LuaBootstrapStub(" + key.getSimpleName() + ")";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == (args == null ? null : args[0]);
                            default -> null;
                        };
                    }

                    Class<?> result = method.getReturnType();
                    if (result == void.class) return null;
                    if (result == boolean.class) return false;
                    if (result == byte.class) return (byte) 0;
                    if (result == short.class) return (short) 0;
                    if (result == int.class) return 0;
                    if (result == long.class) return 0L;
                    if (result == float.class) return 0f;
                    if (result == double.class) return 0d;
                    if (result == char.class) return '\0';
                    if (result == String.class) {
                        return "engineVersion".equals(method.getName()) ? "999.0.0" : "";
                    }
                    if (result.isArray()) return Array.newInstance(result.getComponentType(), 0);
                    if (result == List.class) return List.of();
                    if (result == Map.class) return Map.of();
                    if (result.isEnum()) {
                        Object[] values = result.getEnumConstants();
                        return values.length == 0 ? null : values[0];
                    }
                    if (result.isInterface()) return fakeInterface(result, cache);
                    return null;
                }
        ));
    }
}
