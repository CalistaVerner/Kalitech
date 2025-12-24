package org.foxesworld.kalitech.engine.api;

import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unused")
public interface EngineApi {
    @HostAccess.Export
    LogApi log();

    @HostAccess.Export
    AssetsApi assets();

    @HostAccess.Export
    EventsApi events();

    @HostAccess.Export
    MaterialApi material();

    @HostAccess.Export
    EntityApi entity();

    @HostAccess.Export
    RenderApi render();

    @HostAccess.Export
    CameraApi camera();

    @HostAccess.Export
    String engineVersion();

    @HostAccess.Export TimeApi time();
    @HostAccess.Export InputApi input();


    @HostAccess.Export
    WorldApi world();

    @HostAccess.Export
    EditorApi editor();

    /**
     * Execute a callback on JME main thread via Application#enqueue.
     *
     * <p>Use this when you call APIs that touch the scene graph, viewport processors,
     * camera, physics, etc.
     */
    @HostAccess.Export
    void runOnMainThread(Value fn);
}