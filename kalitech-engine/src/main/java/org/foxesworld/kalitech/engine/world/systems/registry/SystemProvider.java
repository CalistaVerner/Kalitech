package org.foxesworld.kalitech.engine.world.systems.registry;

import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface SystemProvider {
    /**
     * String ID used by Lua to reference the system.
     */
    String id();

    /**
     * Metadata describing the provider for diagnostics and logging.
     * <p>
     * Default implementation returns a simple descriptor based on {@link #id()}.
     */
    default SystemDescriptor descriptor() {
        return SystemDescriptor.simple(id());
    }

    /**
     * Creates a system instance. Config is a Lua object (LuaValueRef).
     */
    KSystem create(SystemContext ctx, LuaValueRef config);
}
