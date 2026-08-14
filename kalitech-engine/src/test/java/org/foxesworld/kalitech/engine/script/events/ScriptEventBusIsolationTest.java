package org.foxesworld.kalitech.engine.script.events;

import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScriptEventBusIsolationTest {

    @Test
    void failedLuaSubscriptionIsRemovedWithoutStoppingOtherHandlers() {
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            runtime.globals().set("BAD_CALLS", 0);
            runtime.globals().set("GOOD_CALLS", 0);

            LuaValueRef bad = LuaValueRef.of(runtime.globals().load("""
                    return function(_)
                        BAD_CALLS = BAD_CALLS + 1
                        error("intentional event failure")
                    end
                    """, "@test/bad-event").call());
            LuaValueRef good = LuaValueRef.of(runtime.globals().load("""
                    return function(_)
                        GOOD_CALLS = GOOD_CALLS + 1
                    end
                    """, "@test/good-event").call());

            ScriptEventBus bus = new ScriptEventBus();
            int badToken = bus.on("test.event", bad);
            int goodToken = bus.on("test.event", good);

            bus.emit("test.event", MapPayload.ONE);
            bus.emit("test.event", MapPayload.TWO);
            assertEquals(2, bus.pump());

            assertEquals(1, runtime.globals().get("BAD_CALLS").checkint());
            assertEquals(2, runtime.globals().get("GOOD_CALLS").checkint());
            assertFalse(bus.off(badToken));
            assertTrue(bus.off(goodToken));
        }
    }

    private enum MapPayload {
        ONE,
        TWO
    }
}
