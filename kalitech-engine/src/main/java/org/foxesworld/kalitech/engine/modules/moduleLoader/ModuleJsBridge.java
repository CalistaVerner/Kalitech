package org.foxesworld.kalitech.engine.modules.moduleLoader;

/**
 * Bridge for mounting JS/TS/docs resources from module JARs into the scripting runtime.
 *
 * <p>Implementation must be deterministic and should avoid caching large contents unless needed.
 */
public interface ModuleJsBridge {

    /**
     * Mount JS entrypoint for module id. Typical use: register "@modules/<id>" alias.
     *
     * @param moduleId module id
     * @param loader   classloader that can open resources from jar
     * @param jsPath   path inside jar (e.g. resources/.../index.js)
     */
    void mountJs(String moduleId, ClassLoader loader, String jsPath) throws Throwable;

    default void mountTypes(String moduleId, ClassLoader loader, String dtsPath) throws Exception {
    }

    default void mountDocs(String moduleId, ClassLoader loader, String docsPath) throws Exception {
    }

    default void exposeGlobals(String moduleId, String[] globals) throws Throwable {
    }
}