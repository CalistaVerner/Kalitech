// FILE: org/foxesworld/kalitech/engine/api/interfaces/EditorApi.java
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface EditorApi {

    @HostAccess.Export boolean enabled();
    @HostAccess.Export void setEnabled(boolean enabled);

    @HostAccess.Export void toggle();

    // Частые “editor affordances”
    @HostAccess.Export void setFlyCam(boolean enabled);
    @HostAccess.Export void setStatsView(boolean enabled);
}
