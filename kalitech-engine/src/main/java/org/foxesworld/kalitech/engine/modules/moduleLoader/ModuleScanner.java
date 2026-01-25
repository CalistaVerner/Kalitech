package org.foxesworld.kalitech.engine.modules.moduleLoader;

import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

/**
 * Discovers module JARs and reads their module.json manifests.
 */
public final class ModuleScanner {

    public static final String MANIFEST_PATH = "META-INF/kalitech/module.json";

    private final Logger log;

    public ModuleScanner(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Lists JAR files in a directory recursively in a deterministic order (by file name).
     */
    public List<Path> listJars(Path modulesDir) throws Exception {
        Objects.requireNonNull(modulesDir, "modulesDir");
        if (!Files.isDirectory(modulesDir)) return List.of();

        ArrayList<Path> jars = new ArrayList<>();
        try (var stream = Files.walk(modulesDir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jars::add);
        }
        jars.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return jars;
    }

    public ModuleJar readModule(Path jarPath) throws Exception {
        Objects.requireNonNull(jarPath, "jarPath");

        try (JarFile jf = new JarFile(jarPath.toFile())) {
            var entry = jf.getJarEntry(MANIFEST_PATH);
            if (entry == null) {
                throw new IllegalArgumentException("Missing " + MANIFEST_PATH + " in " + jarPath);
            }

            String json;
            try (InputStream in = jf.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            ModuleDescriptor d = ModuleJson.parse(json);

            if (log.isDebugEnabled()) {
                log.debug("[modules] discovered id='{}' ver='{}' mainClass='{}' jar='{}'",
                        d.id, d.version, d.mainClass, jarPath.getFileName().toString());
            }

            return new ModuleJar(jarPath, d.id, d);
        }
    }
}