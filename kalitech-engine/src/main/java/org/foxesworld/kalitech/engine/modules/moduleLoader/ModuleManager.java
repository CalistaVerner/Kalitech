package org.foxesworld.kalitech.engine.modules.moduleLoader;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One-stop module manager: scan jars, resolve dependencies, and load.
 */
public final class ModuleManager {

    private final Logger log;
    private final ModuleScanner scanner;
    private final ModuleResolver resolver = new ModuleResolver();
    private final ModuleLoader loader;

    public ModuleManager(Logger log, ApiRegistry apiRegistry, ModuleJsBridge jsBridge, ClassLoader parentLoader) {
        this.log = Objects.requireNonNull(log, "log");
        this.scanner = new ModuleScanner(log);
        this.loader = new ModuleLoader(log, apiRegistry, jsBridge, parentLoader);
    }

    public void loadFromDir(Path modulesDir) {
        List<Path> jars;
        try {
            jars = scanner.listJars(modulesDir);

            if (jars.isEmpty()) {
                if (log.isDebugEnabled()) log.debug("[modules] no jars found dir='{}'", modulesDir);
                return;
            }

            ArrayList<ModuleJar> mods = new ArrayList<>(jars.size());
            for (Path jar : jars) {
                mods.add(scanner.readModule(jar));
            }

            List<ModuleJar> ordered = resolver.resolveLoadOrder(mods);
            loader.loadAll(ordered);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        loader.shutdown();
    }
}