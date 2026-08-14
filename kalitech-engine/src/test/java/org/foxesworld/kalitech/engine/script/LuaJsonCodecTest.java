package org.foxesworld.kalitech.engine.script;

import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LuaJsonCodecTest {

    @Test
    void builtinJsonModuleDecodesAndEncodesWithoutGlobalAliases() {
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            LuaValueRef json = runtime.require("@builtin/json");

            LuaValueRef decoded = json.invokeMember(
                    "decode",
                    "{\"name\":\"kalitech\",\"enabled\":true,\"values\":[1,2,3]}"
            );
            assertEquals("kalitech", decoded.getMember("name").asString());
            assertEquals(true, decoded.getMember("enabled").asBoolean());
            assertEquals(3, decoded.getMember("values").getArraySize());

            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", "kalitech");
            value.put("values", List.of(1, 2, 3));
            assertEquals(
                    "{\"name\":\"kalitech\",\"values\":[1,2,3]}",
                    json.invokeMember("encode", value).asString()
            );
        }
    }
}
