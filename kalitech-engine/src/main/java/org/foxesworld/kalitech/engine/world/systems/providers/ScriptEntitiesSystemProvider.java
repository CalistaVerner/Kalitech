package org.foxesworld.kalitech.engine.world.systems.providers;

import org.foxesworld.kalitech.engine.app.RuntimeAppState;
import org.foxesworld.kalitech.engine.world.systems.ScriptSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.AbstractSystemProvider;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemDescriptor;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemModule;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemType;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import static org.foxesworld.kalitech.engine.script.util.LuaCfg.*;

/**
 * Provider for script-driven entity lifecycle systems.
 */
public final class ScriptEntitiesSystemProvider extends AbstractSystemProvider {

    public ScriptEntitiesSystemProvider() {
        super(new SystemDescriptor(
                "scriptEntities",
                SystemType.SCRIPTED,
                SystemModule.scripting("entities"),
                "Executes per-entity script lifecycles and hot-reload hooks."
        ));
    }

    @Override
    public KSystem create(SystemContext ctx, LuaValueRef config) {
        boolean hot = bool(config, "hotReload", false);
        double cd = f64(config, "cooldown", 0.35);
        RuntimeAppState host = ctx.app().getStateManager().getState(RuntimeAppState.class);
        if (host == null) {
            throw new IllegalStateException("scriptEntities requires RuntimeAppState");
        }
        return new ScriptSystem(
                ctx.ecs(),
                hot,
                (float) cd,
                host.getProjectOwnedRoot(),
                host.getEntryPoint()
        );
    }
}
