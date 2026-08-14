package org.foxesworld.kalitech.engine.world.systems.providers;

// package org.foxesworld.kalitech.engine.world.systems.providers;

import org.foxesworld.kalitech.engine.world.systems.TransformSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.AbstractSystemProvider;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemDescriptor;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemModule;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemType;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

/**
 * ECS transform system provider.
 */
public final class TransformSystemProvider extends AbstractSystemProvider {

    public TransformSystemProvider() {
        super(new SystemDescriptor(
                "transform",
                SystemType.CORE,
                SystemModule.ecs("ecs"),
                "Applies transform updates and maintains renderable/world transforms."
        ));
    }

    @Override
    public KSystem create(SystemContext ctx, LuaValueRef config) {
        return new TransformSystem(ctx.ecs());
    }
}
