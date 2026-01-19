package org.foxesworld.kalitech.engine.world.systems.rig;

import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.modules.rig.DefaultRigBinder;
import org.foxesworld.kalitech.engine.modules.rig.RigApi;
import org.foxesworld.kalitech.engine.modules.rig.RigProfileRegistry;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.ThreadMode;

import java.util.Objects;

/**
 * RigWorldSystem
 *
 * Single strict system that installs rig domain + runs attachments.
 */
public final class RigWorldSystem implements KSystem {

    public static final String STATE_KEY_SERVICE = "rig.service";
    public static final String STATE_KEY_API = "rig.api";
    public static final String STATE_KEY_ALIAS = "RIG";

    private final RigProfileRegistry registry = new RigProfileRegistry();
    private final RigService service = new RigService(registry, new DefaultRigBinder());
    private final RigApi api = new RigApi(service);

    private final RigAttachmentSystem attachments;

    public RigWorldSystem(EcsWorld ecs) {
        this.attachments = new RigAttachmentSystem(Objects.requireNonNull(ecs, "ecs"));
    }

    @Override
    public ThreadMode threadMode() {
        return ThreadMode.MAIN;
    }

    @Override
    public int priority() {
        return 57;
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
        ctx.state().set(STATE_KEY_ALIAS, api);
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        attachments.onUpdate(ctx, tpf);
    }

    @Override
    public void onStop(SystemContext ctx) {
        if (ctx == null) return;
        ctx.state().remove(STATE_KEY_ALIAS);
        ctx.state().remove(STATE_KEY_API);
        ctx.state().remove(STATE_KEY_SERVICE);
    }
}