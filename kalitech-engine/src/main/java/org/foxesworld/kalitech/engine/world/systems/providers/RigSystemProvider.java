package org.foxesworld.kalitech.engine.world.systems.providers;

import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;
import org.foxesworld.kalitech.engine.world.systems.rig.RigWorldSystem;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * RigSystemProvider
 *
 * New rig domain system (profiles + sockets + attachments).
 */
public final class RigSystemProvider implements SystemProvider {

    @Override
    public String id() {
        return "rig";
    }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        Objects.requireNonNull(ctx, "ctx");

        EcsWorld ecs = ctx.ecs();
        if (ecs == null) {
            throw new IllegalStateException("Rig system requires ECS (ctx.ecs() is null)");
        }

        return new RigWorldSystem(ecs);
    }
}