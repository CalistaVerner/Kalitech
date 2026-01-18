package org.foxesworld.kalitech.engine.script.runtime;

/**
 * Stable contract between Java systems and script runtime.
 * No reflection, no fallbacks, no silent failures.
 */
public interface ScriptModuleRuntime {

    /**
     * Require script module by id.
     *
     * @param id module id
     * @return exported module object
     */
    Object require(String id);

    /**
     * Invalidate cached module.
     *
     * @param id module id
     */
    void invalidate(String id);

    /**
     * Ensure runtime is fully initialized before user scripts execution.
     */
    void ensureReady();
}