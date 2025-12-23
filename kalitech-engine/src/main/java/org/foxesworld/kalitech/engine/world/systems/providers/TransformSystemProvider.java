package org.foxesworld.kalitech.engine.world.systems.providers;

// package org.foxesworld.kalitech.engine.world.systems.providers;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.TransformSystem;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

public final class TransformSystemProvider implements SystemProvider {

    @Override public String id() { return "transform"; }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        return new TransformSystem(ctx.ecs());
    }
}