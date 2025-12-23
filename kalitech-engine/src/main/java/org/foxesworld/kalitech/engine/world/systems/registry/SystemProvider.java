package org.foxesworld.kalitech.engine.world.systems.registry;

import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;

public interface SystemProvider {
    /** строковый ID, по которому JS будет подключать систему */
    String id();

    /** создаёт систему. config — кусок JS-объекта (Value) */
    KSystem create(SystemContext ctx, Value config);
}