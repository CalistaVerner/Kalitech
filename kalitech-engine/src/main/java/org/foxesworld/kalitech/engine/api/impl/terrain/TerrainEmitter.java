package org.foxesworld.kalitech.engine.api.impl.terrain;

import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.HashMap;

public final class TerrainEmitter {
    private final ScriptEventBus bus;

    public TerrainEmitter(ScriptEventBus bus) {
        this.bus = bus;
    }

    public void emit(String topic, Object... kv) {
        if (bus == null) return;
        try {
            HashMap<String, Object> m = new HashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                m.put(String.valueOf(kv[i]), kv[i + 1]);
            }
            bus.emit(topic, m);
        } catch (Throwable ignored) {}
    }
}