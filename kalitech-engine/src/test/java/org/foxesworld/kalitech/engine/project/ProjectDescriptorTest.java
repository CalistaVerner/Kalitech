package org.foxesworld.kalitech.engine.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProjectDescriptorTest {

    @TempDir
    Path temp;

    @Test
    void descriptorParentIsTheProjectOwnedRoot() throws Exception {
        Path projectRoot = Files.createDirectories(
                temp.resolve("project").resolve("demoGame")
        );
        Path scripts = Files.createDirectories(projectRoot.resolve("lua"));
        Files.writeString(scripts.resolve("Boot.lua"), "return {}\n");
        Path descriptor = projectRoot.resolve("project.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "id": "demo.project",
                  "scripts": {
                    "namespace": "demo",
                    "root": "lua",
                    "entry": "Boot.lua"
                  }
                }
                """);

        ProjectDescriptor project = ProjectDescriptor.load(descriptor);

        assertEquals("demo.project", project.id());
        assertEquals(projectRoot.toAbsolutePath().normalize(), project.projectOwnedRoot());
        assertEquals(descriptor.toAbsolutePath().normalize(), project.descriptorFile());
        assertEquals("@app/demo/Boot.lua", project.scripts().moduleId());
        assertEquals(
                "lua/Boot.lua",
                project.scripts().projectOwnedPath(project.scripts().moduleId())
        );
    }

    @Test
    void rejectsMissingEntrypointInsteadOfGuessingMainLua() throws Exception {
        Path projectRoot = Files.createDirectories(temp.resolve("demoGame"));
        Path scripts = Files.createDirectories(projectRoot.resolve("Scripts"));
        Files.writeString(scripts.resolve("main.lua"), "return {}\n");
        Path descriptor = projectRoot.resolve("project.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "id": "demo",
                  "scripts": {
                    "namespace": "demo",
                    "root": "Scripts"
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> ProjectDescriptor.load(descriptor));
    }

    @Test
    void rejectsLegacyExternalRootFields() throws Exception {
        Path projectRoot = Files.createDirectories(temp.resolve("demoGame"));
        Path scripts = Files.createDirectories(projectRoot.resolve("Scripts"));
        Files.writeString(scripts.resolve("Application.lua"), "return {}\n");
        Path descriptor = projectRoot.resolve("project.json");

        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "id": "demo",
                  "assets": "../assets",
                  "scripts": {
                    "namespace": "demo",
                    "root": "Scripts",
                    "entry": "Application.lua"
                  }
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> ProjectDescriptor.load(descriptor));

        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "id": "demo",
                  "projectOwned": "../anotherGame",
                  "scripts": {
                    "namespace": "demo",
                    "root": "Scripts",
                    "entry": "Application.lua"
                  }
                }
                """);
        assertThrows(IllegalArgumentException.class, () -> ProjectDescriptor.load(descriptor));
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        Path projectRoot = Files.createDirectories(temp.resolve("demoGame"));
        Path scripts = Files.createDirectories(projectRoot.resolve("Scripts"));
        Files.writeString(scripts.resolve("Application.lua"), "return {}\n");
        Path descriptor = projectRoot.resolve("project.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "id": "demo",
                  "unknown": true,
                  "scripts": {
                    "namespace": "demo",
                    "root": "Scripts",
                    "entry": "Application.lua"
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> ProjectDescriptor.load(descriptor));
    }
}
