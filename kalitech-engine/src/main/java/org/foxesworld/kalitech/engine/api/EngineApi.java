package org.foxesworld.kalitech.engine.api;

import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
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
    SoundApi sound();

    @HostAccess.Export
    RenderApi render();

    @HostAccess.Export
    CameraApi camera();

    // ✅ NEW
    @HostAccess.Export
    PhysicsApi physics();

    @HostAccess.Export
    LightApi light();

    @HostAccess.Export
    DebugDrawApi debug();

    // ✅ new unified surface layer
    @HostAccess.Export
    SurfaceApi surface();

    // ✅ new terrain builder
    @HostAccess.Export
    TerrainApi terrain();

    @HostAccess.Export
    boolean isJmeThread();


    // ✅ new terrain splat layer (separate from builder)
    @HostAccess.Export
    TerrainSplatApi terrainSplat();

    @HostAccess.Export
    EditorLinesApi editorLines();

    @HostAccess.Export
    MeshApi mesh();

    @HostAccess.Export
    HudApi hud();

    @HostAccess.Export
    String engineVersion();

    @HostAccess.Export
    TimeApi time();

    @HostAccess.Export
    InputApi input();

    @HostAccess.Export
    WorldApi world();

    @HostAccess.Export
    EditorApi editor();

    /**
     * Execute a callback on JME main thread via Application#enqueue.
     */
    @HostAccess.Export
    void runOnMainThread(Value fn);
}