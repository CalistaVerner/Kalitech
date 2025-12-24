package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface TimeApi {
    /** last frame tpf (seconds) */
    @HostAccess.Export double tpf();

    /** alias for tpf (seconds) */
    @HostAccess.Export double dt();

    /** monotonic time since engine start (seconds) */
    @HostAccess.Export double now();

    /** frame counter since start */
    @HostAccess.Export long frame();
}