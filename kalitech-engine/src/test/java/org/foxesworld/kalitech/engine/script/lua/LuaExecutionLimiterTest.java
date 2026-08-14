package org.foxesworld.kalitech.engine.script.lua;

import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaExecutionLimiterTest {

    @Test
    void runawayCallbackIsStoppedAndRuntimeRemainsUsable() {
        String instructionsKey = "kalitech.lua.maxCallbackInstructions";
        String millisKey = "kalitech.lua.maxCallbackMillis";
        String previousInstructions = System.getProperty(instructionsKey);
        String previousMillis = System.getProperty(millisKey);

        System.setProperty(instructionsKey, "20000");
        System.setProperty(millisKey, "1000");
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            LuaValueRef runaway = LuaValueRef.of(runtime.globals().load("""
                    return function()
                        while true do
                        end
                    end
                    """, "@test/runaway").call());

            RuntimeException failure = assertThrows(RuntimeException.class, runaway::executeVoid);
            assertTrue(failure.getMessage().contains("budget exceeded"));

            LuaValueRef healthy = LuaValueRef.of(runtime.globals().load(
                    "return function() return 42 end",
                    "@test/after-runaway"
            ).call());
            assertEquals(42, healthy.execute().asInt());
        } finally {
            restore(instructionsKey, previousInstructions);
            restore(millisKey, previousMillis);
        }
    }

    @Test
    void lifecycleBudgetAllowsHeavyInitializationWithoutWeakeningFrameCallbacks() {
        String callbackInstructionsKey = "kalitech.lua.maxCallbackInstructions";
        String callbackMillisKey = "kalitech.lua.maxCallbackMillis";
        String lifecycleInstructionsKey = "kalitech.lua.maxLifecycleInstructions";
        String lifecycleMillisKey = "kalitech.lua.maxLifecycleMillis";
        String previousCallbackInstructions = System.getProperty(callbackInstructionsKey);
        String previousCallbackMillis = System.getProperty(callbackMillisKey);
        String previousLifecycleInstructions = System.getProperty(lifecycleInstructionsKey);
        String previousLifecycleMillis = System.getProperty(lifecycleMillisKey);

        System.setProperty(callbackInstructionsKey, "5000000");
        System.setProperty(callbackMillisKey, "1");
        System.setProperty(lifecycleInstructionsKey, "5000000");
        System.setProperty(lifecycleMillisKey, "2000");

        try (ScriptRuntime runtime = new ScriptRuntime()) {
            LuaValueRef initialization = LuaValueRef.of(runtime.globals().load("""
                    return function()
                        local sum = 0
                        for i = 1, 200000 do
                            sum = sum + i
                        end
                        return sum
                    end
                    """, "@test/lifecycle-budget").call());

            RuntimeException callbackFailure =
                    assertThrows(RuntimeException.class, initialization::execute);
            assertTrue(callbackFailure.getMessage().contains("wall-clock budget exceeded"), callbackFailure::getMessage);

            assertDoesNotThrow(() -> initialization.executeLifecycle("test.lifecycle"));
        } finally {
            restore(callbackInstructionsKey, previousCallbackInstructions);
            restore(callbackMillisKey, previousCallbackMillis);
            restore(lifecycleInstructionsKey, previousLifecycleInstructions);
            restore(lifecycleMillisKey, previousLifecycleMillis);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
