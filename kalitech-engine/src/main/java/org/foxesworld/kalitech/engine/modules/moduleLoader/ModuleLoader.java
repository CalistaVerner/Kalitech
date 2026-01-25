package org.foxesworld.kalitech.engine.modules.moduleLoader;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.module.ApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads resolved modules: creates classloaders, instantiates ApiModule, registers into ApiRegistry,
 * mounts JS resources, and tracks loaded modules for deterministic shutdown.
 *
 * <p>Typing artifacts (*.d.ts / *.ts) are optional: if missing, the loader logs WARN and continues.
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

    private static boolean hasResource(ClassLoader cl, String path) {
        final String p = String.valueOf(path).trim();
        if (p.isEmpty()) return false;

        URL u = cl.getResource(p);
        if (u != null) return true;

        // Some build pipelines accidentally store resources without leading slash.
        if (p.charAt(0) == '/') {
            u = cl.getResource(p.substring(1));
        } else {
            u = cl.getResource('/' + p);
        }
        return u != null;
    }

    /**
     * Deterministic shutdown: detach modules in reverse order, then close classloaders in reverse order.
     */
    public void shutdown() {
        for (int i = loadedModules.size() - 1; i >= 0; i--) {
            ApiModule m = loadedModules.get(i);
            try {
                m.detach();
            } catch (Throwable t) {
                log.warn("[modules] detach failed id='{}' impl={} err={}",
                        safeId(m), m.getClass().getName(), t.toString());
            }
        }
        loadedModules.clear();

        for (int i = classLoaders.size() - 1; i >= 0; i--) {
            try {
                classLoaders.get(i).close();
            } catch (Throwable t) {
                log.warn("[modules] classloader close failed err={}", t.toString());
            }
        }
        classLoaders.clear();
    }

    private ApiModule instantiate(ModuleDescriptor d, ClassLoader cl) throws Exception {
        Class<?> c = Class.forName(d.mainClass, true, cl);
        Object o = c.getDeclaredConstructor().newInstance();
        if (!(o instanceof ApiModule m)) {
            throw new IllegalStateException("mainClass does not implement ApiModule: " + d.mainClass);
        }
        return m;
    }

    public void loadAll(List<ModuleJar> ordered) throws Throwable {
        Objects.requireNonNull(ordered, "ordered");

        for (ModuleJar mj : ordered) {
            ModuleDescriptor d = mj.desc;

            URLClassLoader cl = ModuleClassLoaders.newModuleClassLoader(mj.jarPath, parentLoader);
            classLoaders.add(cl);

            ApiModule module = instantiate(d, cl);
            apiRegistry.register(module); // attach(ctx) happens inside ApiRegistry.register
            loadedModules.add(module);

            if (d.js != null) {
                jsBridge.mountJs(d.id, cl, d.js);
            }

            // Typings are optional: warn only if missing.
            if (d.types != null) {
                if (hasResource(cl, d.types)) {
                    jsBridge.mountTypes(d.id, cl, d.types);
                } else {
                    log.warn("[modules] typings not found (optional) id='{}' types='{}' jar='{}'",
                            d.id, d.types, mj.jarPath.getFileName().toString());
                }
            }

            if (d.docs != null) {
                jsBridge.mountDocs(d.id, cl, d.docs);
            }

            if (d.globals.length != 0) {
                jsBridge.exposeGlobals(d.id, d.globals);
            }

            if (log.isDebugEnabled()) {
                log.debug("[modules] loaded id='{}' ver='{}' impl={} jar='{}'",
                        d.id, d.version, module.getClass().getName(), mj.jarPath.getFileName().toString());
            }
        }
    }
}