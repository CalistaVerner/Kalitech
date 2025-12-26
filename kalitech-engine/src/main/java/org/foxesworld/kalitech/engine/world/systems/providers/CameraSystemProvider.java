package org.foxesworld.kalitech.engine.world.systems.providers;

import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.CameraState;
import org.foxesworld.kalitech.engine.world.systems.CameraSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

public final class CameraSystemProvider implements SystemProvider {

    @Override
    public String id() {
        return "camera";
    }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        // We need the shared state from EngineApiImpl (not exported to JS)
        EngineApiImpl api = (EngineApiImpl) ctx.api;
        CameraState state = api.getCameraState();

        CameraSystem sys = new CameraSystem(state, ctx.getPhysicsSpace());
        //sys.setBodyPositionProvider((bodyId, out) -> ctx.physicsAccess().getBodyPosition(bodyId, out)).setRaycastProvider((from, to) -> ctx.physicsAccess().rayFraction(from, to));
        sys.applyConfig(config); // optional per-world overrides
        return sys;
    }
}