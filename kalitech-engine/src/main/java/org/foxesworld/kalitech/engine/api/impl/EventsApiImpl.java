package org.foxesworld.kalitech.engine.api.impl;

import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.EventsApi;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class EventsApiImpl implements EventsApi {

    private final ScriptEventBus bus;

    public EventsApiImpl(ScriptEventBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @HostAccess.Export
    @Override
    public void emit(String topic, Object payload) {
        bus.emit(topic, payload);
    }
}