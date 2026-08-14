package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface EventsApi {
    @LuaExport void emit(String topic, Object payload);

    /** Subscribe: returns a subscription id (token) that can be used to off(). */
    @LuaExport int on(String topic, LuaValueRef handler);

    /**
     * Subscribe one-shot: handler is removed after the first event.
     */
    @LuaExport
    int once(String topic, LuaValueRef handler);

    /** Unsubscribe by token. */
    @LuaExport boolean off(String topic, int token);

    /** Remove all listeners for a topic. */
    @LuaExport void clear(String topic);
}