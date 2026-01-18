package org.foxesworld.kalitech.engine.world.systems.registry;

import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.graalvm.polyglot.Value;

public interface SystemProvider {
    /**
     * String ID used by JS to reference the system.
     */
    String id();

    /**
     * Creates a system instance. Config is a JS object (Value).
     */
    KSystem create(SystemContext ctx, Value config);
}
