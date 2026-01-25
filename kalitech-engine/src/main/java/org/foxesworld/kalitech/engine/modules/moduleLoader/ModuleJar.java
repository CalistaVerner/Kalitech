package org.foxesworld.kalitech.engine.modules.moduleLoader;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Pair of (descriptor + origin).
 *
 * <p>For external modules {@link #jarPath} is non-null.
 * For classpath/built-in modules {@link #jarPath} is null and {@link #origin} identifies the resource.
 */
public final class ModuleJar {

    /**
     * Null for classpath modules.
     */
    public final Path jarPath;

    /**
     * Human-readable origin for logs (jar file name or classpath origin).
     */
    public final String origin;

    public final ModuleDescriptor desc;

    public ModuleJar(Path jarPath, String origin, ModuleDescriptor desc) {
        this.jarPath = jarPath;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.desc = Objects.requireNonNull(desc, "desc");
    }

    public static ModuleJar forJar(Path jarPath, ModuleDescriptor desc) {
        Objects.requireNonNull(jarPath, "jarPath");
        return new ModuleJar(jarPath, jarPath.getFileName().toString(), desc);
    }

    public static ModuleJar forClasspath(String origin, ModuleDescriptor desc) {
        return new ModuleJar(null, origin, desc);
    }

    /**
     * Utility used by module classloader factory.
     */
    public static URL toJarUrl(Path jarPath) {
        try {
            // URLClassLoader expects a plain file URL to the jar.
            return jarPath.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("bad jar path: " + jarPath, e);
        }
    }

    public boolean isExternalJar() {
        return jarPath != null;
    }

    /**
     * Converts jar path to an URL suitable for {@link java.net.URLClassLoader}.
     *
     * @throws IllegalStateException if this module is not backed by a jar.
     */
    public URL jarUrl() {
        if (jarPath == null) {
            throw new IllegalStateException("module is classpath-based: origin=" + origin);
        }
        return toJarUrl(jarPath);
    }
}