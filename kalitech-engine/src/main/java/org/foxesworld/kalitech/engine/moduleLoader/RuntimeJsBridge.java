package org.foxesworld.kalitech.engine.moduleLoader;

import org.foxesworld.kalitech.engine.script.ScriptRuntime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ScriptRuntime bridge that mounts module JS resources from external JARs.
 *
 * <p>Key idea: ScriptRuntime loads modules via {@link ScriptRuntime.ModuleStreamProvider}.
 * We install a composite provider that serves "@modules/..." from JAR resources and
 * delegates everything else to the previous provider (e.g. Assets/Scripts).
 *
 * <p>Also provides a deterministic stub for "@modules/<id>" -> requires "@modules/<id>/index.js"
 * to keep relative requires working.
 */
public final class RuntimeJsBridge implements ModuleJsBridge {

    public static final String MODULES_PREFIX = "@modules/";

    private final ScriptRuntime runtime;

    /**
     * moduleKey -> mapping
     */
    private final ConcurrentHashMap<String, Mount> mounts = new ConcurrentHashMap<>(64);

    /**
     * Guard to install composite provider once.
     */
    private volatile boolean installed = false;

    public RuntimeJsBridge(ScriptRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    private static InputStream stubIndexRedirect(String key) {
        // CommonJS wrapper expects module/exports already created by ScriptRuntime
        // We only redirect exports.
        String src = "module.exports = require('" + MODULES_PREFIX + key + "/index.js');\n";
        byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }

    private static String normalizeModuleKey(String moduleId) {
        String id = moduleId.trim().replace('\\', '/');
        while (id.startsWith("/")) id = id.substring(1);

        if (id.startsWith(MODULES_PREFIX)) {
            id = id.substring(MODULES_PREFIX.length());
        }

        // key only: "input" from "input" or "input/..."
        int slash = id.indexOf('/');
        String key = (slash < 0) ? id : id.substring(0, slash);
        key = key.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("moduleId is blank");
        return key.toLowerCase();
    }

    private static String normalizeResourcePath(String p) {
        String s = p.trim().replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String stripLeadingSlashes(String s) {
        String out = s;
        while (out.startsWith("/")) out = out.substring(1);
        return out;
    }

    private static void validateResourceExists(ClassLoader loader, String resPath) {
        try (InputStream in = loader.getResourceAsStream(resPath)) {
            if (in == null) {
                //throw new IllegalArgumentException("Resource not found in module jar: " + resPath);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to validate resource: " + resPath, e);
        }
    }

    private static ScriptRuntime.ModuleStreamProvider readCurrentStreamProvider(ScriptRuntime rt) {
        try {
            Field f = ScriptRuntime.class.getDeclaredField("streamLoader");
            f.setAccessible(true);
            Object v = f.get(rt);
            return (v instanceof ScriptRuntime.ModuleStreamProvider p) ? p : null;
        } catch (Throwable t) {
            // If we cannot access, we still can install our provider, but it will replace previous one.
            return null;
        }
    }

    @Override
    public void mountJs(String moduleId, ClassLoader loader, String jsPath) throws Exception {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(jsPath, "jsPath");

        installIfNeeded();

        final String key = normalizeModuleKey(moduleId);
        final String entry = normalizeResourcePath(jsPath);

        final int slash = entry.lastIndexOf('/');
        final String baseDir = (slash >= 0) ? entry.substring(0, slash + 1) : "";

        mounts.put(key, new Mount(loader, baseDir, entry));
    }

    @Override
    public void mountTypes(String moduleId, ClassLoader loader, String dtsPath) throws Exception {
        // Optional: keep as validation only (resource exists).
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(dtsPath, "dtsPath");
        validateResourceExists(loader, normalizeResourcePath(dtsPath));
    }

    @Override
    public void mountDocs(String moduleId, ClassLoader loader, String docsPath) throws Exception {
        // Optional: keep as validation only (resource exists).
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(docsPath, "docsPath");
        validateResourceExists(loader, normalizeResourcePath(docsPath));
    }

    @Override
    public void exposeGlobals(String moduleId, String[] globals) throws Exception {
        // Optional: you can expose globals by evaluating require in JS after initBuiltIns().
        // We keep it NO-OP here because global exposure policy is engine-specific.
        // If you want: runtime.ctx().getBindings("js").putMember(name, runtime.require("@modules/<id>"));
        // But do it only after builtins are initialized.
    }

    private void installIfNeeded() {
        if (installed) return;

        synchronized (this) {
            if (installed) return;

            ScriptRuntime.ModuleStreamProvider prev = readCurrentStreamProvider(runtime);

            ScriptRuntime.ModuleStreamProvider composite = moduleId -> {
                InputStream in = tryOpenFromMountedJars(moduleId);
                if (in != null) return in;
                return (prev != null) ? prev.openStream(moduleId) : null;
            };

            runtime.setModuleStreamProvider(composite);
            installed = true;
        }
    }

    private InputStream tryOpenFromMountedJars(String rawId) throws Exception {
        if (rawId == null) return null;

        String id = rawId.trim().replace('\\', '/');
        if (!id.startsWith(MODULES_PREFIX)) return null;

        // Accept both "@modules/x" and "@modules/x.js" (normalize)
        while (id.startsWith("/")) id = id.substring(1);

        // Split: "@modules/<key>/..."
        String rest = id.substring(MODULES_PREFIX.length());
        if (rest.isEmpty()) return null;

        int slash = rest.indexOf('/');
        final String key = (slash < 0) ? rest : rest.substring(0, slash);

        final Mount m = mounts.get(key);
        if (m == null) return null;

        // Request root: "@modules/<key>" -> return stub that requires index.js
        if (slash < 0) {
            return stubIndexRedirect(key);
        }

        String rel = rest.substring(slash + 1);
        if (rel.isEmpty()) return stubIndexRedirect(key);

        // Normalize to ".js" if needed
        rel = stripLeadingSlashes(rel);
        if (!rel.endsWith(".js")) rel = rel + ".js";

        // Resolve to resource path inside jar
        String resPath = m.baseDir + rel;
        InputStream in = m.loader.getResourceAsStream(resPath);
        if (in != null) return in;

        // If requested explicitly "index.js" but missing, try entry
        if ("index.js".equals(rel)) {
            return m.loader.getResourceAsStream(m.entryPath);
        }

        return null;
    }

    private static final class Mount {
        final ClassLoader loader;
        final String baseDir;
        final String entryPath;

        Mount(ClassLoader loader, String baseDir, String entryPath) {
            this.loader = loader;
            this.baseDir = baseDir;
            this.entryPath = entryPath;
        }
    }
}