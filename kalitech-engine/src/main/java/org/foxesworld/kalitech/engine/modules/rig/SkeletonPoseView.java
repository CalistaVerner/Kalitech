package org.foxesworld.kalitech.engine.modules.rig;

import com.jme3.math.Transform;

/**
 * SkeletonPoseView
 *
 * Adds access to current model-space pose (translation/rotation/scale) of bones.
 */
public interface SkeletonPoseView extends SkeletonView {

    /**
     * Writes model-space bone transform into store.
     *
     * @param boneIndex bone index
     * @param store output transform (must not be null)
     */
    void getModelTransform(int boneIndex, Transform store);
}
