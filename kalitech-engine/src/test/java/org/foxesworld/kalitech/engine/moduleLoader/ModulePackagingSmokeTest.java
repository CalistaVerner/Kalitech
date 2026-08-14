package org.foxesworld.kalitech.engine.moduleLoader;

import org.apache.logging.log4j.LogManager;
import org.foxesworld.kalitech.engine.api.module.ApiModule;
import org.foxesworld.kalitech.engine.script.LuaSyntaxVerifier;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModulePackagingSmokeTest {

    private static final Set<String> EXPECTED_MODULES = Set.of(
            "camera",
            "hud",
            "input",
            "material",
            "particles",
            "physics",
            "render",
            "sound",
            "terrain"
    );

    @Test
    void rebuiltModuleEntrypointsEvaluateUnderLuaRuntime() throws Exception {
        Path repo = Path.of(System.getProperty("kalitech.repoRoot"))
                .toAbsolutePath()
                .normalize();
        ModuleScanner scanner = new ModuleScanner(LogManager.getLogger(ModulePackagingSmokeTest.class));
        List<Path> jars = scanner.listJars(repo.resolve("modules"));

        ArrayList<URLClassLoader> loaders = new ArrayList<>();
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            Field initialized = ScriptRuntime.class.getDeclaredField("builtinsInitialized");
            initialized.setAccessible(true);
            initialized.setBoolean(runtime, true);

            RuntimeLuaBridge bridge = new RuntimeLuaBridge(runtime);
            ArrayList<ModuleDescriptor> descriptors = new ArrayList<>();
            for (Path jar : jars) {
                ModuleJar module = scanner.readModule(jar);
                URLClassLoader loader = ModuleClassLoaders.newModuleClassLoader(
                        module.jarPath,
                        ModulePackagingSmokeTest.class.getClassLoader()
                );
                loaders.add(loader);
                bridge.mountLua(module.desc.id, loader, module.desc.lua);
                descriptors.add(module.desc);
            }

            for (ModuleDescriptor descriptor : descriptors) {
                assertNotNull(
                        runtime.require("@modules/" + descriptor.id + "/index.lua"),
                        "Lua entrypoint returned null: " + descriptor.id
                );
            }
        } finally {
            for (int i = loaders.size() - 1; i >= 0; i--) {
                loaders.get(i).close();
            }
        }
    }

    @Test
    void rebuiltModuleJarsContainOnlyLoadableLuaEntrypoints() throws Exception {
        Path repo = Path.of(System.getProperty("kalitech.repoRoot"))
                .toAbsolutePath()
                .normalize();
        Path modulesDir = repo.resolve("modules");

        ModuleScanner scanner = new ModuleScanner(LogManager.getLogger(ModulePackagingSmokeTest.class));
        List<Path> jars = scanner.listJars(modulesDir);
        assertEquals(EXPECTED_MODULES.size(), jars.size(), "unexpected module JAR count");

        HashSet<String> ids = new HashSet<>();
        try (ScriptRuntime runtime = new ScriptRuntime()) {
            RuntimeLuaBridge bridge = new RuntimeLuaBridge(runtime);

            for (Path jar : jars) {
                ModuleJar module = scanner.readModule(jar);
                ModuleDescriptor descriptor = module.desc;

                assertTrue(ids.add(descriptor.id), "duplicate module id: " + descriptor.id);
                assertNotNull(descriptor.lua, "missing Lua entrypoint: " + descriptor.id);
                assertTrue(descriptor.lua.endsWith(".lua"), "non-Lua entrypoint: " + descriptor.lua);

                try (URLClassLoader loader = ModuleClassLoaders.newModuleClassLoader(
                        module.jarPath,
                        ModulePackagingSmokeTest.class.getClassLoader()
                ); ZipFile zip = new ZipFile(module.jarPath.toFile())) {
                    ZipEntry entrypoint = zip.getEntry(descriptor.lua);
                    assertNotNull(entrypoint, "missing Lua entrypoint resource: " + descriptor.lua);
                    bridge.mountLua(descriptor.id, loader, descriptor.lua);

                    for (String mainClass : descriptor.mainClass) {
                        Class<?> type = Class.forName(mainClass, false, loader);
                        assertTrue(ApiModule.class.isAssignableFrom(type),
                                "mainClass is not an ApiModule: " + mainClass);
                    }

                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory()) continue;

                        String name = entry.getName().toLowerCase();

                        if (name.endsWith(".lua")) {
                            try (InputStream stream = zip.getInputStream(entry)) {
                                String code = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                LuaSyntaxVerifier.verify(code, jar.getFileName() + "!/" + entry.getName());
                            }
                        }
                    }
                }
            }
        }

        assertEquals(EXPECTED_MODULES, ids);
    }
}
