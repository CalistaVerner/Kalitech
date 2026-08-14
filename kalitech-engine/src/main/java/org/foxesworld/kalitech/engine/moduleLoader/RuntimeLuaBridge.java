package org.foxesworld.kalitech.engine.moduleLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptRuntime;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua runtime bridge for module resources embedded in external JARs.
 */
public final class RuntimeLuaBridge implements ModuleLuaBridge {

    private static final Logger log = LogManager.getLogger(RuntimeLuaBridge.class);
    public static final String MODULES_PREFIX = "@modules/";

    private final ScriptRuntime runtime;
    private final ConcurrentHashMap<String, Mount> mounts = new ConcurrentHashMap<>(64);
    private volatile boolean installed;

    public RuntimeLuaBridge(ScriptRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void mountLua(String moduleId, ClassLoader loader, String luaPath) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(luaPath, "luaPath");
        installIfNeeded();

        String key = normalizeModuleKey(moduleId);
        String entry = normalizeResourcePath(luaPath);
        int slash = entry.lastIndexOf('/');
        String baseDirectory = slash < 0 ? "" : entry.substring(0, slash + 1);
        mounts.put(key, new Mount(loader, baseDirectory, entry));
    }

    @Override
    public void mountDocs(String moduleId, ClassLoader loader, String docsPath)
            throws Exception {
        validateOptionalResource(moduleId, "docs", loader, normalizeResourcePath(docsPath));
    }

    private void installIfNeeded() {
        if (installed) return;
        synchronized (this) {
            if (installed) return;
            ScriptRuntime.ModuleStreamProvider previous = runtime.moduleStreamProvider();
            runtime.setModuleStreamProvider(moduleId -> {
                InputStream mounted = tryOpenMounted(moduleId);
                if (mounted != null) return mounted;
                return previous == null ? null : previous.openStream(moduleId);
            });
            installed = true;
        }
    }

    private InputStream tryOpenMounted(String rawId) {
        if (rawId == null) return null;
        String id = rawId.trim().replace('\\', '/');
        if (!id.startsWith(MODULES_PREFIX)) return null;

        String rest = id.substring(MODULES_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;

        String key = rest.substring(0, slash).toLowerCase();
        Mount mount = mounts.get(key);
        if (mount == null) return null;

        String relative = stripLeadingSlashes(rest.substring(slash + 1));
        if (relative.isBlank()) relative = "index.lua";
        if (!hasExtension(relative)) relative += ".lua";

        InputStream exact = mount.loader.getResourceAsStream(mount.baseDirectory + relative);
        if (exact != null) return exact;
        if ("index.lua".equals(relative)) {
            return mount.loader.getResourceAsStream(mount.entryPath);
        }
        return null;
    }


    private static String normalizeModuleKey(String moduleId) {
        String id = moduleId.trim().replace('\\', '/');
        while (id.startsWith("/")) id = id.substring(1);
        if (id.startsWith(MODULES_PREFIX)) id = id.substring(MODULES_PREFIX.length());
        int slash = id.indexOf('/');
        String key = slash < 0 ? id : id.substring(0, slash);
        if (key.isBlank()) throw new IllegalArgumentException("moduleId is blank");
        return key.toLowerCase();
    }

    private static String normalizeResourcePath(String path) {
        String result = path.trim().replace('\\', '/');
        while (result.startsWith("/")) result = result.substring(1);
        return result;
    }

    private static String stripLeadingSlashes(String path) {
        String result = path;
        while (result.startsWith("/")) result = result.substring(1);
        return result;
    }

    private static boolean hasExtension(String path) {
        return path.endsWith(".lua") || path.endsWith(".json");
    }

    private static void validateOptionalResource(
            String moduleId,
            String kind,
            ClassLoader loader,
            String path
    ) throws Exception {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                log.warn("[modules] optional {} resource missing id='{}' path='{}'",
                        kind, moduleId, path);
            }
        }
    }

    private record Mount(ClassLoader loader, String baseDirectory, String entryPath) {
    }
}
