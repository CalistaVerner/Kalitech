package org.foxesworld.kalitech.engine.api;

import org.graalvm.polyglot.HostAccess;

public interface EntityApi {

    @HostAccess.Export int create(String name);
    @HostAccess.Export void destroy(int id);

    @HostAccess.Export void setComponent(int id, String type, Object data);
    @HostAccess.Export Object getComponent(int id, String type);

    // NEW
    @HostAccess.Export boolean hasComponent(int id, String type);
    @HostAccess.Export void removeComponent(int id, String type);
}