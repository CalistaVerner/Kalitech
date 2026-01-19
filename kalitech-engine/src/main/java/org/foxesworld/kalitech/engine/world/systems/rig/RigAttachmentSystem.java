package org.foxesworld.kalitech.engine.world.systems.rig;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.RenderableComponent;
import org.foxesworld.kalitech.engine.ecs.components.RigAttachmentComponent;
import org.foxesworld.kalitech.engine.modules.rig.RigBinding;
import org.foxesworld.kalitech.engine.modules.rig.RigProfile;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonView;
import org.foxesworld.kalitech.engine.modules.rig.jme.JmeSkeletonResolver;
import org.foxesworld.kalitech.engine.modules.rig.jme.SocketTransformReader;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.ThreadMode;

import java.util.Objects;

/**
 * RigAttachmentSystem
 *
 * Updates entity attachments to rig sockets.
 * Requires:
 * - RenderableComponent on both parent and child (with spatial)
 * - RigAttachmentComponent on child
 * - RigService installed in ctx.state (by RigSystem)
 */
public final class RigAttachmentSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(RigAttachmentSystem.class);

    private final EcsWorld ecs;
    private final SocketTransformReader reader = new SocketTransformReader();

    private final Transform tmpSocketWorld = new Transform();
    private final Vector3f tmpExtraPos = new Vector3f();
    private final Quaternion tmpExtraRot = new Quaternion();

    public RigAttachmentSystem(EcsWorld ecs) {
        this.ecs = Objects.requireNonNull(ecs, "ecs");
    }

    @Override
    public ThreadMode threadMode() {
        return ThreadMode.MAIN;
    }

    @Override
    public int priority() {
        return 58;
    }

    @Override
    public double desiredHz() {
        return 60.0;
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        if (ctx == null) return;
        RigService service = (RigService) ctx.state().get("rig.service");
        if (service == null) return;

        var view = ecs.components().view(RigAttachmentComponent.class);
        if (view == null || view.isEmpty()) return;

        for (var entry : view.entrySet()) {
            final int childEntity = entry.getKey();
            final RigAttachmentComponent ac = entry.getValue();
            if (ac == null) continue;

            RenderableComponent childRc = ecs.components().get(childEntity, RenderableComponent.class);
            if (childRc == null || childRc.spatial == null) continue;

            RenderableComponent parentRc = ecs.components().get(ac.parentEntity, RenderableComponent.class);
            if (parentRc == null || parentRc.spatial == null) continue;

            if (ac.rigProfileId == null || ac.rigProfileId.isBlank()) continue;
            if (ac.socketId == null || ac.socketId.isBlank()) continue;

            RigProfile profile;
            try {
                profile = service.profiles().require(ac.rigProfileId);
            } catch (Throwable t) {
                continue;
            }

            ensureBinding(service, ac, parentRc);

            RigBinding binding = ac.binding;
            if (binding == null) continue;

            boolean ok = reader.readSocketWorld(profile, binding, parentRc.spatial, ac.socketId, tmpSocketWorld);
            if (!ok) continue;

            applyExtra(ac);

            // Apply to child spatial.
            childRc.spatial.setLocalTranslation(tmpSocketWorld.getTranslation());

            if (ac.followRotation) {
                childRc.spatial.setLocalRotation(tmpSocketWorld.getRotation());
            }
        }
    }

    private void ensureBinding(RigService service, RigAttachmentComponent ac, RenderableComponent parentRc) {
        if (ac.binding != null && ac.boundProfileId != null && ac.boundProfileId.equals(ac.rigProfileId)) {
            return;
        }

        SkeletonView sk = JmeSkeletonResolver.resolve(parentRc.spatial);
        if (sk == null) {
            return;
        }

        try {
            ac.binding = service.bind(ac.rigProfileId, sk);
            ac.boundProfileId = ac.rigProfileId;
        } catch (Throwable t) {
            ac.binding = null;
            ac.boundProfileId = null;
            if (log.isDebugEnabled()) {
                log.debug("[RigAttachmentSystem] bind failed profileId={} parentEntity={}", ac.rigProfileId, ac.parentEntity, t);
            }
        }
    }

    private void applyExtra(RigAttachmentComponent ac) {
        if ((ac.ox == 0f && ac.oy == 0f && ac.oz == 0f) &&
                (ac.rxDeg == 0f && ac.ryDeg == 0f && ac.rzDeg == 0f)) {
            return;
        }

        tmpExtraPos.set(ac.ox, ac.oy, ac.oz);
        tmpExtraRot.fromAngles(
                (float) Math.toRadians(ac.rxDeg),
                (float) Math.toRadians(ac.ryDeg),
                (float) Math.toRadians(ac.rzDeg)
        );

        Transform extra = new Transform(tmpExtraPos, tmpExtraRot, Vector3f.UNIT_XYZ);

        // socketWorld = socketWorld * extraLocal
        extra.combineWithParent(tmpSocketWorld);
        tmpSocketWorld.set(extra);
    }
}