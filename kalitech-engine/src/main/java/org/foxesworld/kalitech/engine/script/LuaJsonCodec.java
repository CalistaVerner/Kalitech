package org.foxesworld.kalitech.engine.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

import java.io.IOException;
import java.util.Objects;

/** Engine-owned JSON codec exposed only through {@code require("@builtin/json")}. */
public final class LuaJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LuaExport
    public Object decode(String text) throws IOException {
        return MAPPER.readValue(Objects.requireNonNull(text, "text"), Object.class);
    }

    @LuaExport
    public String encode(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }
}
