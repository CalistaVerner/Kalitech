package org.foxesworld.kalitech.engine.script.resolve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApplicationResolverTest {

    @Test
    void preservesValidatedApplicationModuleIds() {
        ApplicationResolver resolver = new ApplicationResolver();

        assertEquals(
                "@app/kalitech/player/index.lua",
                resolver.resolve("", "@app/kalitech/player/index.lua").orElseThrow()
        );
        assertTrue(resolver.resolve("", "@app/Kalitech/player.lua").isEmpty());
        assertTrue(resolver.resolve("", "@app/kalitech/../player.lua").isEmpty());
        assertTrue(resolver.resolve("", "@app/kalitech").isEmpty());
    }

    @Test
    void physicalScriptPathsHaveNoCompatibilityFallback() {
        PassThroughResolver resolver = new PassThroughResolver();

        assertTrue(resolver.resolve("", "Scripts/Application.lua").isEmpty());
        assertTrue(resolver.resolve("", "Scripts/player/index.lua").isEmpty());
        assertEquals(
                "Mods/example.lua",
                resolver.resolve("", "Mods/example.lua").orElseThrow()
        );
    }
}
