package org.foxesworld.kalitech.engine.world.systems.providers;

import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.ScriptSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

import java.nio.file.Path;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class ScriptEntitiesSystemProvider implements SystemProvider {

    @Override public String id() { return "scriptEntities"; }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        boolean hot = bool(config, "hotReload", false);
        double cd = f64(config, "cooldown", 0.35);
        String root = str(config, "watchRoot", "assets");
        return new ScriptSystem(ctx.ecs(), hot, (float) cd, Path.of(root));
    }
}