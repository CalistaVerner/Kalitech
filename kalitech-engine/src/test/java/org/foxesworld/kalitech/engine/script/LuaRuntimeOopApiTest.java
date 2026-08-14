package org.foxesworld.kalitech.engine.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class LuaRuntimeOopApiTest {

    @Test
    void builtinRuntimeExposesOnlyObjectOrientedServices() {
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            assertDoesNotThrow(() -> runtime.globals().load("""
                    local runtime = require("@builtin/lua_runtime")

                    assert(runtime.version == "2.0.0")
                    assert(type(runtime.array) == "table")
                    assert(type(runtime.string) == "table")
                    assert(type(runtime.number) == "table")
                    assert(type(runtime.table) == "table")
                    assert(type(runtime.class) == "table")
                    assert(type(runtime.iterator) == "table")
                    assert(type(runtime.sparseArray) == "table")
                    assert(type(runtime.collection) == "table")
                    assert(type(runtime.type) == "table")
                    assert(type(runtime.object) == "table")

                    -- Legacy procedural exports are deliberately gone.
                    assert(runtime.LuaArrayIsArray == nil)
                    assert(runtime.LuaConstruct == nil)
                    assert(runtime.LuaNumber == nil)
                    assert(runtime.LuaStringTrim == nil)
                    assert(runtime.LuaTableMerge == nil)
                    assert(runtime.LuaTypeOf == nil)

                    local values = {1, 2, 3}
                    assert(runtime.array:isArray(values))
                    assert(runtime.array:join(values, "|") == "1|2|3")
                    local copy = runtime.array:slice(values, 1, 3)
                    assert(#copy == 2 and copy[1] == 2 and copy[2] == 3)
                    assert(runtime.string:trim("  Kalitech  " ) == "Kalitech")
                    assert(runtime.number:isFinite(runtime.number:coerce("42")))

                    local Example = runtime.class:create()
                    Example.name = "Example"
                    function Example.prototype.lua_constructor(self, value) self.value = value end
                    function Example.prototype.get(self) return self.value end
                    local instance = runtime.class:construct(Example, 42)
                    assert(instance:get() == 42)
                    assert(runtime.class:isInstance(instance, Example))

                    local set = runtime.collection:newSet({"a", "b"})
                    assert(runtime.collection:isSet(set) and set:has("a") and set.size == 2)
                    local map = runtime.collection:newMap({{"answer", 42}})
                    assert(runtime.collection:isMap(map) and map:get("answer") == 42)
                    assert(runtime.Map == nil and runtime.Set == nil)

                    local util = require("@builtin/bootstrap/Util.lua")
                    assert(getmetatable(util) ~= nil)
                    assert(type(util.safeJson) == "function")
                    assert(rawget(util, "safeJson") == nil)

                    """, "@test/lua-runtime-oop").call());
        }
    }
}
