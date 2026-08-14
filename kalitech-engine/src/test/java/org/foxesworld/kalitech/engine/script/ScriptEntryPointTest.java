package org.foxesworld.kalitech.engine.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScriptEntryPointTest {

    @Test
    void mapsOnlyItsVirtualApplicationNamespace() {
        ScriptEntryPoint entry = new ScriptEntryPoint(
                "Kalitech_Game",
                "Scripts/App",
                "Boot"
        );

        assertEquals("kalitech_game", entry.namespace());
        assertEquals("@app/kalitech_game/Boot.lua", entry.moduleId());
        assertEquals(
                "Scripts/App/player/index.lua",
                entry.projectOwnedPath("@app/kalitech_game/player/index.lua")
        );
        assertEquals(
                "@app/kalitech_game/player/index.lua",
                entry.moduleIdForProjectPath("Scripts/App/player/index.lua")
        );

        assertNull(entry.projectOwnedPath("@app/another/player/index.lua"));
        assertNull(entry.projectOwnedPath("Scripts/App/player/index.lua"));
        assertNull(entry.moduleIdForProjectPath("Textures/player.png"));
    }

    @Test
    void rejectsAbsoluteAndTraversalConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptEntryPoint("kalitech", "../Scripts", "Application.lua"));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptEntryPoint("kalitech", "Scripts", "../Application.lua"));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptEntryPoint("kalitech", "Scripts", "Application.ts"));
    }
}
