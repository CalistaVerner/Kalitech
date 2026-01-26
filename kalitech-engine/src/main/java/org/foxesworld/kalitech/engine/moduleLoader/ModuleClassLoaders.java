package org.foxesworld.kalitech.engine.moduleLoader;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Module classloader factory.
 *
 * <p>Rule: engine packages must be loaded from parent to avoid class identity splits.
 */
public final class ModuleClassLoaders {

    private ModuleClassLoaders() {
    }

    public static URLClassLoader newModuleClassLoader(Path jarPath, ClassLoader parent) {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(parent, "parent");

        final URL url = ModuleJar.toJarUrl(jarPath);
        return new FilteringUrlClassLoader(new URL[]{url}, parent);
    }

    /**
     * Child-first for module libs, but parent-first for engine/core packages.
     */
    private static final class FilteringUrlClassLoader extends URLClassLoader {

        static {
            // Required for URLClassLoader close() / internal perms in some environments
            ClassLoader.registerAsParallelCapable();
        }

        FilteringUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        private static boolean isParentFirst(String name) {
            // Java / JVM / Graal / JME / engine APIs must be singletons
            return name.startsWith("java.")
                    || name.startsWith("javax.")
                    || name.startsWith("jdk.")
                    || name.startsWith("sun.")
                    || name.startsWith("org.graalvm.")
                    || name.startsWith("com.oracle.truffle.")
                    || name.startsWith("com.jme3.")
                    || name.startsWith("org.lwjgl.")
                    || name.startsWith("org.slf4j.")
                    || name.startsWith("org.apache.logging.log4j.")
                    || name.startsWith("org.foxesworld.kalitech.engine."); // CRITICAL
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Always delegate core stuff to parent
            if (isParentFirst(name)) {
                return super.loadClass(name, resolve); // parent-first by URLClassLoader default
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    // Child-first for non-engine classes
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        c = super.loadClass(name, false);
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }
}