package org.foxesworld.kalitech.engine.modules.rig.jme;

import com.jme3.anim.SkinningControl;
import com.jme3.animation.Skeleton;
import com.jme3.animation.SkeletonControl;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonView;

import java.util.Objects;

/**
 * Resolves SkeletonView from a Spatial using common JME controls.
 *
 * No reflection. No assumptions about "fps" or "arms".
 */
@Deprecated
public final class JmeSkeletonResolver {

    private JmeSkeletonResolver() {
    }

    public static SkeletonView resolve(Spatial spatial) {
        Objects.requireNonNull(spatial, "spatial");

        SkinningControl sc = spatial.getControl(SkinningControl.class);
        if (sc != null && sc.getArmature() != null) {
            return new JmeArmatureSkeletonView(sc.getArmature());
        }

        SkeletonControl legacy = spatial.getControl(SkeletonControl.class);
        if (legacy != null) {
            Skeleton sk = legacy.getSkeleton();
            if (sk != null) return new JmeLegacySkeletonView(sk);
        }

        return null;
    }
}