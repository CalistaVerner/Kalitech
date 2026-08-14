package org.foxesworld.kalitech.engine.moduleLoader;

/**
 * Mounts Lua and documentation resources from module JARs.
 */
public interface ModuleLuaBridge {

    void mountLua(String moduleId, ClassLoader loader, String luaPath) throws Throwable;

    default void mountDocs(String moduleId, ClassLoader loader, String docsPath)
            throws Exception {
    }
}
