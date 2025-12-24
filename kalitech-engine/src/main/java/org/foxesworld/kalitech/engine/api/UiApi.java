// FILE: UiApi.java
package org.foxesworld.kalitech.engine.api;

import org.graalvm.polyglot.HostAccess;

public interface UiApi {

    /** Just a smoke-test method for JS wiring. */
    @HostAccess.Export
    String ping();

    /** Pump Chromium message loop (call each frame). */
    @HostAccess.Export
    void tick();

    /** createSurface({id,width,height,url}) -> handle */
    @HostAccess.Export
    int createSurface(Object cfg);

    /** Navigate by handle. */
    @HostAccess.Export
    void navigate(int handle, String url);

    /** Convenience: navigate active surface. */
    @HostAccess.Export
    void navigate(String url);

    /** Send message to UI by handle. */
    @HostAccess.Export
    void send(int handle, Object msg);

    /** Convenience: send to active surface. */
    @HostAccess.Export
    void send(Object msg);

    /** Destroy UI surface. */
    @HostAccess.Export
    void destroySurface(int handle);

    /** Shutdown backend (only on app exit). */
    @HostAccess.Export
    void shutdown();

    /** Optional: active surface helpers (если ты их оставишь в impl). */
    @HostAccess.Export
    default int getActive() { return -1; }

    @HostAccess.Export
    default void setActive(int handle) {}

    @HostAccess.Export
    default boolean exists(int handle) { return false; }
}
