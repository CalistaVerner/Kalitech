package org.foxesworld.kalitech.engine.ecs.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScriptComponentTest {

    @Test
    void storesCanonicalApplicationModuleId() {
        ScriptComponent component = new ScriptComponent("@app/game/entities/player.lua");

        assertEquals("@app/game/entities/player.lua", component.moduleId);
        assertNotEquals(0L, component.moduleHash);
    }

    @Test
    void rejectsPhysicalAssetPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScriptComponent("Scripts/entities/player.lua")
        );
    }
}
