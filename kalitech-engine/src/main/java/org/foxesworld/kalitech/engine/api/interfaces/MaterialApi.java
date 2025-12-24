package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.impl.MaterialApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface MaterialApi {
    @HostAccess.Export
    MaterialApiImpl.MaterialHandle create(Value cfg);
    @HostAccess.Export void destroy(MaterialApiImpl.MaterialHandle handle); // optional
}