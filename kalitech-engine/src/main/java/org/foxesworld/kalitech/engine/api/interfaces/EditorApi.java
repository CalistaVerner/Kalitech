// FILE: org/foxesworld/kalitech/engine/api/interfaces/EditorApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

/**
 * Editor API for runtime developer affordances (debug camera, stats overlays).
 */
public interface EditorApi {

    /** Returns true when editor helpers are enabled. */
    @HostAccess.Export boolean enabled();

    /** Enables or disables editor helpers. */
    @HostAccess.Export void setEnabled(boolean enabled);

    /** Toggles editor helpers. */
    @HostAccess.Export void toggle();

    /** Enables or disables free-flight camera controls. */
    @HostAccess.Export void setFlyCam(boolean enabled);

    /** Enables or disables the stats overlay. */
    @HostAccess.Export void setStatsView(boolean enabled);
}
