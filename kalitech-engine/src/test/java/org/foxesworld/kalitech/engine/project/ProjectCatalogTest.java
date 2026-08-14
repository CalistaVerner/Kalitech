package org.foxesworld.kalitech.engine.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectCatalogTest {

    @TempDir
    Path temp;

    @Test
    void discoversValidProjectsAndSkipsBrokenDescriptors() throws Exception {
        createProject("bravo", "game.bravo");
        createProject("alpha", "game.alpha");
        Path broken = temp.resolve("broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("project.json"), "{not-json}");

        ProjectCatalog.DiscoveryResult result = ProjectCatalog.discover(temp, null);

        assertEquals(2, result.projects().size());
        assertEquals("game.alpha", result.projects().get(0).id());
        assertEquals("game.bravo", result.projects().get(1).id());
        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().get(0).descriptorFile().endsWith("broken/project.json"));
    }

    @Test
    void acceptsProjectDirectoryAsDescriptorSource() throws Exception {
        Path project = createProject("sample", "game.sample");
        assertEquals(project.resolve("project.json").toAbsolutePath().normalize(),
                ProjectCatalog.descriptorPath(project));
    }

    private Path createProject(String directory, String id) throws Exception {
        Path project = temp.resolve(directory);
        Files.createDirectories(project.resolve("Scripts"));
        Files.writeString(project.resolve("Scripts/Application.lua"), "return {}\n");
        Files.writeString(project.resolve("project.json"), """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "scripts": {
                    "namespace": "game",
                    "root": "Scripts",
                    "entry": "Application.lua"
                  }
                }
                """.formatted(id));
        return project;
    }
}
