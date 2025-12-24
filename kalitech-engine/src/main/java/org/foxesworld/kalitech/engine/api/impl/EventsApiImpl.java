package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EventsApi;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class EventsApiImpl implements EventsApi {

    private final ScriptEventBus bus;

    public EventsApiImpl(EngineApiImpl engineApi) {
        this.bus = engineApi.getBus();
    }

    @HostAccess.Export
    @Override
    public void emit(String topic, Object payload) {
        bus.emit(topic, payload);
    }

    @HostAccess.Export
    @Override
    public int on(String topic, Value handler) {
        return bus.on(topic, handler);
    }

    @HostAccess.Export
    @Override
    public int once(String topic, Value handler) {
        return bus.on(topic, handler);
    }

    @HostAccess.Export
    @Override
    public boolean off(String topic, int token) {
        return bus.off(topic, token);
    }

    @HostAccess.Export
    @Override
    public void clear(String topic) {
        bus.offByModule(topic);
    }
}