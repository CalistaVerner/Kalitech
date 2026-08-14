package org.foxesworld.kalitech.engine.script;

import org.foxesworld.kalitech.engine.project.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaRuntimeSmokeTest {

    @Test
    void runtimeLibraryExecutesLuaSemantics() {
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            Globals globals = runtime.globals();
            globals.load("""
                    local values = {10, 20}
                    assert(KLength(values) == 2)
                    assert(KIndex(values, 0) == 10)
                    KSetIndex(values, 1, 25)
                    assert(values[2] == 25)

                    KArrayOps.push(values, 30)
                    assert(KArrayOps.indexOf(values, 25) == 1)
                    local mapped = KArrayOps.map(values, function(_, value)
                        return value * 2
                    end)
                    assert(KArrayOps.join(mapped, ",") == "20,50,60")

                    local receiver = {base = 4}
                    local fn = function(self, value) return self.base + value end
                    assert(KFunction:call(fn, receiver, 3) == 7)
                    assert(KFunction:apply(fn, receiver, {5}) == 9)
                    local bound = KFunction:bind(fn, receiver)
                    assert(bound(nil, 6) == 10)

                    local proxy = KProxy({}, {
                        get = function(self, target, key)
                            if key == "answer" then return 42 end
                            return target[key]
                        end
                    })
                    assert(proxy.answer == 42)

                    local version = KString:parseSemver("3.8.1")
                    assert(version[0] == 3 and version[1] == 8 and version[2] == 1)
                    """, "@test/lua-runtime").call();
        }
    }

    @Test
    void everyMigratedLuaModuleCompiles() throws IOException {
        Path repo = Path.of(System.getProperty("kalitech.repoRoot"))
                .toAbsolutePath()
                .normalize();
        ProjectDescriptor project = ProjectDescriptor.load(
                Path.of(System.getProperty("kalitech.project"))
        );
        List<Path> roots = List.of(
                project.projectOwnedRoot(),
                repo.resolve("kalitech-engine/src/main/resources"),
                repo.resolve("modulesSrc")
        );

        ArrayList<Path> modules = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".lua"))
                        .forEach(modules::add);
            }
        }

        assertTrue(modules.size() >= 149, "expected all Lua resources");
        for (Path module : modules) {
            String code = Files.readString(module);
            LuaSyntaxVerifier.verify(code, repo.relativize(module).toString());
        }
    }
}
