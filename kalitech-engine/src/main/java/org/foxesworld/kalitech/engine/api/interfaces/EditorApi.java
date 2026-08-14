// FILE: org/foxesworld/kalitech/engine/api/interfaces/EditorApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * Editor API for runtime developer affordances (debug camera, stats overlays).
 */
public interface EditorApi {

    /** Returns true when editor helpers are enabled. */
    @LuaExport boolean enabled();

    /** Enables or disables editor helpers. */
    @LuaExport void setEnabled(boolean enabled);

    /** Toggles editor helpers. */
    @LuaExport void toggle();

    /** Enables or disables free-flight camera controls. */
    @LuaExport void setFlyCam(boolean enabled);

    /** Enables or disables the stats overlay. */
    @LuaExport void setStatsView(boolean enabled);
}
