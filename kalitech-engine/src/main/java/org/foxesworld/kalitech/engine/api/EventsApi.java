package org.foxesworld.kalitech.engine.api;

import org.graalvm.polyglot.HostAccess;

public interface EventsApi {
    @HostAccess.Export void emit(String topic, Object payload);
}