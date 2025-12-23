package org.foxesworld.kalitech.engine.world.systems.providers;

import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.util.ValueCfg;
import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

public final class JsWorldSystemProvider implements SystemProvider {

    @Override public String id() { return "jsSystem"; }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        String module = ValueCfg.str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("jsSystem requires config.module = 'Scripts/.../file.js'");
        }
        return new JsWorldSystem(module);
    }
}