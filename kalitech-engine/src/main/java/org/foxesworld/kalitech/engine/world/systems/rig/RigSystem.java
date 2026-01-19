package org.foxesworld.kalitech.engine.world.systems.rig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.rig.*;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.ThreadMode;

import java.util.Objects;

/**
 * RigSystem
 *
 * Installs a new rig domain into SystemContext state:
 * - RigService (engine-agnostic)
 * - RigApi (JS-facing HostAccess exports)
 */
public final class RigSystem implements KSystem {

    public static final String STATE_KEY_SERVICE = "rig.service";
    public static final String STATE_KEY_API = "rig.api";
    public static final String STATE_KEY_GLOBAL_ALIAS = "RIG";

    private static final Logger log = LogManager.getLogger(RigSystem.class);

    private final RigProfileRegistry registry;
    private final RigService service;
    private final RigApi api;

    public RigSystem(RigProfileRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.service = new RigService(this.registry, new DefaultRigBinder());
        this.api = new RigApi(this.service);
    }

    @Override
    public ThreadMode threadMode() {
        return ThreadMode.MAIN;
    }

    @Override
    public int priority() {
        return 55;
    }

    @Override
    public double desiredHz() {
        return 60.0;
    }

    @Override
    public void onStart(SystemContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        ctx.state().set(STATE_KEY_SERVICE, service);
        ctx.state().set(STATE_KEY_API, api);
        ctx.state().set(STATE_KEY_GLOBAL_ALIAS, api);

        log.info("[RigSystem] installed: serviceKey='{}', apiKey='{}', alias='{}'",
                STATE_KEY_SERVICE, STATE_KEY_API, STATE_KEY_GLOBAL_ALIAS);

        ctx.hotReloadHub().register(reason -> {
            if (reason == null) return;
            if (reason.startsWith("rig:")) {
                log.info("[RigSystem] hot reload event: reason={}", reason);
            }
        });
    }

    @Override
    public void onStop(SystemContext ctx) {
        if (ctx == null) return;
        ctx.state().remove(STATE_KEY_GLOBAL_ALIAS);
        ctx.state().remove(STATE_KEY_API);
        ctx.state().remove(STATE_KEY_SERVICE);
        log.info("[RigSystem] removed rig domain from ctx.state");
    }
}