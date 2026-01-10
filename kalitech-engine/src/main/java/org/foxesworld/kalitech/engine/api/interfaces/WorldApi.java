package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface WorldApi {

    /**
     * Spawn entity from prefab.
     * args: { name?: string, prefab: string }
     * returns entity UUID (string)
     */
    @HostAccess.Export
    String spawn(Value args);

    /**
     * Find entity UUID by Name (stored in ComponentStore byName "Name").
     * returns "" if not found.
     */
    @HostAccess.Export
    String findByName(String name);

    /**
     * Destroy entity by UUID.
     */
    @HostAccess.Export
    void destroy(String uuid);
}