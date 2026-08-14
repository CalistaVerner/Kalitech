package org.foxesworld.kalitech.engine.script;

import org.foxesworld.kalitech.engine.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class LuaResourceEvaluationSmokeTest {

    @Test
    void everyEngineAndApplicationLuaResourceEvaluates() throws Exception {
        Path repo = Path.of(System.getProperty("kalitech.repoRoot"))
                .toAbsolutePath()
                .normalize();
        ProjectDescriptor project = ProjectDescriptor.load(
                Path.of(System.getProperty("kalitech.project"))
        );
        Path projectOwned = project.projectOwnedRoot();
        ScriptEntryPoint entryPoint = project.scripts();
        Path applicationScripts = projectOwned.resolve(entryPoint.scriptRoot());
        Path builtins = repo.resolve("kalitech-engine/src/main/resources/kalitech/engine");

        try (ScriptRuntime runtime = new ScriptRuntime()) {
            Field initialized = ScriptRuntime.class.getDeclaredField("builtinsInitialized");
            initialized.setAccessible(true);
            initialized.setBoolean(runtime, true);

            runtime.setModuleStreamProvider(moduleId -> {
                String projectPath = entryPoint.projectOwnedPath(moduleId);
                if (projectPath == null) return null;
                Path file = projectOwned.resolve(projectPath).normalize();
                if (!file.startsWith(projectOwned) || !Files.isRegularFile(file)) return null;
                return Files.newInputStream(file);
            });

            for (String moduleId : luaModuleIds(builtins, "@builtin/")) {
                assertNotNull(runtime.require(moduleId), "builtin returned null: " + moduleId);
            }
            for (String moduleId : luaModuleIds(applicationScripts, entryPoint.moduleRoot() + "/")) {
                assertNotNull(runtime.require(moduleId), "application module returned null: " + moduleId);
            }
        }
    }

    private static List<String> luaModuleIds(Path root, String prefix) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".lua"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .map(path -> prefix + path)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }
}
