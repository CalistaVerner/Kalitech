package org.foxesworld.kalitech.engine.world.systems.providers;

import org.foxesworld.kalitech.engine.world.systems.ScriptSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.AbstractSystemProvider;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemDescriptor;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemModule;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemType;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.nio.file.Path;

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
        String root = str(config, "watchRoot", "assets");
        return new ScriptSystem(ctx.ecs(), hot, (float) cd, Path.of(root));
    }
}
