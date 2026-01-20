package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.module.ApiModuleProvider;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;

public final class BuiltinApiModuleProvider implements ApiModuleProvider {
    @Override
    public String id() {
        return "builtin";
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public void register(ApiRegistry registry) {
        registry.register(new LogApiImpl());
        registry.register(new AssetsApiImpl());
        registry.register(new EventsApiImpl());
        registry.register(new TimeApiImpl());
        registry.register(new InputApiImpl());

        registry.register(new MaterialApiImpl());
        registry.register(new RenderApiImpl());
        registry.register(new EntityApiImpl());
        registry.register(new CameraApiImpl());

        registry.register(new PhysicsApiImpl());
        registry.register(new SurfaceApiImpl());

        registry.register(new TerrainApiImpl());
        registry.register(new TerrainSplatApiImpl());
        registry.register(new EditorLinesApiImpl());
        registry.register(new MeshApiImpl());

        registry.register(new LightApiImpl());
        registry.register(new SoundApiImpl());
        registry.register(new DebugDrawApiImpl());
        registry.register(new HudApiImpl());
        registry.register(new WorldApiImpl());
        registry.register(new EditorApiImpl());
        registry.register(new ParticlesApiImpl());
    }
}
