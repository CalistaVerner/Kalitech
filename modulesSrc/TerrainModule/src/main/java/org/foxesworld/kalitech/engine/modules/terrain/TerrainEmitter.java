/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.script.events.ScriptEventBus
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import java.util.HashMap;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

public final class TerrainEmitter {
    private final ScriptEventBus bus;

    public TerrainEmitter(EngineApiImpl engineApi) {
        this.bus = engineApi.getBus();
    }

    public void emit(String topic, Object ... kv) {
        if (this.bus == null) {
            return;
        }
        try {
            HashMap<String, Object> m = new HashMap<String, Object>();
            int i = 0;
            while (i + 1 < kv.length) {
                m.put(String.valueOf(kv[i]), kv[i + 1]);
                i += 2;
            }
            this.bus.emit(topic, m);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

