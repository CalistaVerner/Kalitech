package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.app.SimpleApplication;
import org.foxesworld.kalitech.engine.api.EngineApi;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.world.KWorld;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaScriptIsolationTest {

    @Test
    void failingLuaSystemIsQuarantinedWhileWorldKeepsUpdatingAndReloadRecovers() {
        AtomicReference<Map<String, String>> sources = new AtomicReference<>(Map.of(
                "@app/test/broken.lua", """
                        local M = { starts = 0, updates = 0 }
                        function M:init(_)
                            self.starts = self.starts + 1
                            error("intentional test failure")
                        end
                        function M:update(_, _)
                            self.updates = self.updates + 1
                        end
                        return M
                        """,
                "@app/test/healthy.lua", """
                        local M = { starts = 0, updates = 0 }
                        function M:init(_)
                            self.starts = self.starts + 1
                        end
                        function M:update(_, _)
                            self.updates = self.updates + 1
                        end
                        return M
                        """
        ));

        EngineApi engine = fakeInterface(EngineApi.class, new ConcurrentHashMap<>());
        SimpleApplication app = new SimpleApplication() {
            @Override
            public void simpleInitApp() {
            }
        };

        try (ScriptRuntime runtime = new ScriptRuntime()) {
            runtime.setModuleStreamProvider(moduleId -> {
                String source = sources.get().get(moduleId);
                return source == null ? null : new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
            });
            runtime.initBuiltIns(engine);

            KWorld world = new KWorld("isolation-test");
            SystemContext context = new SystemContext(
                    app,
                    engine,
                    null,
                    new ScriptEventBus(),
                    null,
                    world.worldTime(),
                    runtime,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            LuaWorldSystem broken = new LuaWorldSystem("@app/test/broken.lua", Map.of(), Map.of(), "world");
            LuaWorldSystem healthy = new LuaWorldSystem("@app/test/healthy.lua", Map.of(), Map.of(), "world");
            world.addSystem(broken, 0);
            world.addSystem(healthy, 1);

            assertDoesNotThrow(() -> world.start(context));
            assertDoesNotThrow(() -> world.update(context, 1f / 60f));

            assertTrue(broken.isQuarantined());
            assertTrue(broken.quarantineReason().contains("intentional test failure"));

            LuaValueRef brokenValue = runtime.require("@app/test/broken.lua");
            LuaValueRef healthyValue = runtime.require("@app/test/healthy.lua");
            assertEquals(1, brokenValue.getMember("starts").asInt());
            assertEquals(0, brokenValue.getMember("updates").asInt());
            assertEquals(1, healthyValue.getMember("starts").asInt());
            assertEquals(1, healthyValue.getMember("updates").asInt());

            sources.set(Map.of(
                    "@app/test/broken.lua", """
                            local M = { starts = 0, updates = 0 }
                            function M:init(_)
                                self.starts = self.starts + 1
                            end
                            function M:update(_, _)
                                self.updates = self.updates + 1
                            end
                            return M
                            """,
                    "@app/test/healthy.lua", sources.get().get("@app/test/healthy.lua")
            ));

            assertDoesNotThrow(() -> broken.onHotReload(context, "test-fix"));
            assertDoesNotThrow(() -> broken.onStart(context));
            assertDoesNotThrow(() -> broken.onUpdate(context, 1f / 60f));
            assertFalse(broken.isQuarantined());

            LuaValueRef recovered = runtime.require("@app/test/broken.lua");
            assertEquals(1, recovered.getMember("starts").asInt());
            assertEquals(1, recovered.getMember("updates").asInt());

            assertDoesNotThrow(() -> world.stop(context));
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
                            case "toString" -> "LuaIsolationStub(" + key.getSimpleName() + ")";
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
