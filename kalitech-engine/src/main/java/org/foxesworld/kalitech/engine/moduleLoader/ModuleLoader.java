package org.foxesworld.kalitech.engine.moduleLoader;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.module.ApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads resolved modules: creates classloaders, instantiates ApiModule, registers into ApiRegistry,
 * mounts JS resources, and tracks loaded modules for deterministic shutdown.
 */
public final class ModuleLoader {

    private final Logger log;
    private final ApiRegistry apiRegistry;
    private final ModuleJsBridge jsBridge;
    private final ClassLoader parentLoader;

    private final ArrayList<URLClassLoader> classLoaders = new ArrayList<>(16);
    private final ArrayList<ApiModule> loadedModules = new ArrayList<>(16);

    public ModuleLoader(Logger log, ApiRegistry apiRegistry, ModuleJsBridge jsBridge, ClassLoader parentLoader) {
        this.log = Objects.requireNonNull(log, "log");
        this.apiRegistry = Objects.requireNonNull(apiRegistry, "apiRegistry");
        this.jsBridge = Objects.requireNonNull(jsBridge, "jsBridge");
        this.parentLoader = Objects.requireNonNull(parentLoader, "parentLoader");
    }

    private static String safeId(ApiModule m) {
        try {
            return m.id();
        } catch (Throwable ignored) {
            return "<?>"; // never fail shutdown logging
        }
    }

    public void loadAll(List<ModuleJar> ordered) throws Throwable {
        Objects.requireNonNull(ordered, "ordered");

        for (ModuleJar mj : ordered) {
            ModuleDescriptor d = mj.desc;

            URLClassLoader cl = ModuleClassLoaders.newModuleClassLoader(mj.jarPath, parentLoader);
            classLoaders.add(cl);

            // Mount JS/types/docs/globals once per JAR descriptor
            if (d.js != null) jsBridge.mountJs(d.id, cl, d.js);
            if (d.types != null) jsBridge.mountTypes(d.id, cl, d.types);
            if (d.docs != null) jsBridge.mountDocs(d.id, cl, d.docs);
            if (d.globals.length != 0) jsBridge.exposeGlobals(d.id, d.globals);

            // Instantiate and register multiple ApiModules from the same JAR
            for (String mainClass : d.mainClass) {
                ApiModule module = instantiate(mainClass, cl);
                apiRegistry.register(module); // attach(ctx) happens inside ApiRegistry.register
                loadedModules.add(module);

                if (log.isDebugEnabled()) {
                    log.debug("[modules] loaded id='{}' ver='{}' impl={} mainClass='{}' jar='{}'",
                            d.id, d.version, module.getClass().getName(), mainClass, mj.jarPath.getFileName().toString());
                }
            }
        }
    }

    /**
     * Deterministic shutdown: detach modules in reverse order, then close classloaders in reverse order.
     */
    public void shutdown() {
        for (int i = loadedModules.size() - 1; i >= 0; i--) {
            ApiModule m = loadedModules.get(i);
            try {
                apiRegistry.detach(m);
            } catch (Throwable t) {
                log.error("[modules] detach failed id='{}' impl={}", safeId(m), m.getClass().getName(), t);
            }
        }
        loadedModules.clear();

        for (int i = classLoaders.size() - 1; i >= 0; i--) {
            URLClassLoader cl = classLoaders.get(i);
            try {
                cl.close();
            } catch (Throwable t) {
                log.error("[modules] classloader close failed {}", cl, t);
            }
        }
        classLoaders.clear();
    }

    private ApiModule instantiate(String mainClass, ClassLoader cl) throws Exception {
        Class<?> c = Class.forName(mainClass, true, cl);
        Object o = c.getDeclaredConstructor().newInstance();
        if (!(o instanceof ApiModule m)) {
            throw new IllegalStateException("mainClass does not implement ApiModule: " + mainClass);
        }
        return m;
    }
}