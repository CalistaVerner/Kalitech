package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.impl.light.LightApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface LightApi {

    @HostAccess.Export
    LightApiImpl.LightHandle create(Value cfg);

    @HostAccess.Export
    void set(LightApiImpl.LightHandle handle, Value cfg);

    @HostAccess.Export
    void enable(LightApiImpl.LightHandle handle, boolean enabled);

    @HostAccess.Export
    boolean exists(LightApiImpl.LightHandle handle);

    @HostAccess.Export
    void destroy(LightApiImpl.LightHandle handle);

    @HostAccess.Export
    Value get(LightApiImpl.LightHandle handle);

    @HostAccess.Export
    Value list();
}