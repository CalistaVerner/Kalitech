package org.foxesworld.kalitech.engine.modules.rig;

/**
 * SkeletonView
 *
 * Minimal skeleton adapter interface used by the rig module.
 * Concrete engines (jME, custom, etc.) should provide an implementation.
 */
public interface SkeletonView {

    /**
     * @return number of bones in the skeleton.
     */
    int boneCount();

    /**
     * Returns authored bone name for the given index.
     *
     * @param boneIndex bone index
     * @return bone name, never null
     */
    String boneName(int boneIndex);

    /**
     * Finds bone index by authored bone name.
     *
     * @param boneName authored bone name
     * @return bone index or -1 if not found
     */
    int findBoneIndex(String boneName);
}