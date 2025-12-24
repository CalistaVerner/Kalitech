package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface WorldApi {

    /**
     * Spawn entity from prefab.
     * args: { name?: string, prefab: string }
     * returns entityId
     */
    @HostAccess.Export int spawn(Object args);

    /** Find entityId by Name (stored in ComponentStore byName "Name"). */
    @HostAccess.Export int findByName(String name);

    /** Destroy entity by id. */
    @HostAccess.Export void destroy(int id);
}