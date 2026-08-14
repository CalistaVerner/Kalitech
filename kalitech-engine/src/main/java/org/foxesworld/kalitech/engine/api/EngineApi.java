package org.foxesworld.kalitech.engine.api;

import org.foxesworld.kalitech.engine.api.interfaces.*;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsApi;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

@SuppressWarnings("unused")
public interface EngineApi {
    @LuaExport
    LogApi log();

    @LuaExport
    AssetsApi assets();

    @LuaExport
    EventsApi bus();

    @LuaExport
    MaterialApi material();

    @LuaExport
    EntityApi entity();

    @LuaExport
    SoundApi sound();

    @LuaExport
    RenderApi render();

    @LuaExport
    CameraApi camera();

    //  NEW
    @LuaExport
    PhysicsApi physics();

    @LuaExport
    LightApi light();

    @LuaExport
    DebugDrawApi debug();

    @LuaExport
    ParticlesApi particles();

    //  new unified surface layer
    @LuaExport
    SurfaceApi surface();

    //  new terrain builder
    @LuaExport
    TerrainApi terrain();

    @LuaExport
    boolean isJmeThread();


    //  new terrain splat layer (separate from builder)
    @LuaExport
    TerrainSplatApi terrainSplat();

    @LuaExport
    EditorLinesApi editorLines();

    @LuaExport
    MeshApi mesh();

    @LuaExport
    HudApi hud();

    @LuaExport
    String engineVersion();

    @LuaExport
    TimeApi time();

    @LuaExport
    InputApi input();

    @LuaExport
    WorldApi world();

    @LuaExport
    EditorApi editor();

    @LuaExport
    ModulesApi modules();

    @LuaExport
    double fps();

    /**
     * Execute a callback on JME main thread via Application#enqueue.
     */
    @LuaExport
    void runOnMainThread(LuaValueRef fn);
}
