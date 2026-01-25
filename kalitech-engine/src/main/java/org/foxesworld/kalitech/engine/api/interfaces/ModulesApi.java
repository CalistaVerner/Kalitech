package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

import java.util.Map;

/**
 * Runtime API module catalog for tooling and scripts.
 */
public interface ModulesApi {

    /**
     * List registered API module ids.
     */
    @HostAccess.Export
    String[] list();

    /**
     * Describe a module by id, or null if not found.
     */
    @HostAccess.Export
    Map<String, Object> describe(String id);

    /**
     * Describe all modules in registration order.
     */
    @HostAccess.Export
    Map<String, Object>[] describeAll();
}
