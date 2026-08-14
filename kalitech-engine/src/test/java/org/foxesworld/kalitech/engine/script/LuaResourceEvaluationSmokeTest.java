package org.foxesworld.kalitech.engine.script;

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
        Path assets = repo.resolve("assets");
        Path builtins = repo.resolve("kalitech-engine/src/main/resources/kalitech/engine");

        try (ScriptRuntime runtime = new ScriptRuntime()) {
            Field initialized = ScriptRuntime.class.getDeclaredField("builtinsInitialized");
            initialized.setAccessible(true);
            initialized.setBoolean(runtime, true);

            runtime.setModuleStreamProvider(moduleId -> {
                Path file = assets.resolve(moduleId).normalize();
                if (!file.startsWith(assets) || !Files.isRegularFile(file)) return null;
                return Files.newInputStream(file);
            });

            for (String moduleId : luaModuleIds(builtins, "@builtin/")) {
                assertNotNull(runtime.require(moduleId), "builtin returned null: " + moduleId);
            }
            for (String moduleId : luaModuleIds(assets, "")) {
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
