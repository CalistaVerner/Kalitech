package org.foxesworld.kalitech.engine.modules.rig.jme;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.math.Transform;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonPoseView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class JmeArmatureSkeletonView implements JmeSkeletonView, SkeletonPoseView {

    private final Armature armature;
    private final Joint[] joints;
    private final String[] names;
    private final Map<String, Integer> indexByName;

    public JmeArmatureSkeletonView(Armature armature) {
        this.armature = Objects.requireNonNull(armature, "armature");
        this.joints = armature.getJointList().toArray(new Joint[0]);
        this.names = new String[joints.length];
        this.indexByName = new HashMap<>(Math.max(16, joints.length * 2));
        for (int i = 0; i < joints.length; i++) {
            String n = joints[i].getName();
            if (n == null) n = "";
            names[i] = n;
            indexByName.put(n, i);
        }
    }

    @Override
    public int boneCount() {
        return joints.length;
    }

    @Override
    public String boneName(int boneIndex) {
        if (boneIndex < 0 || boneIndex >= names.length) {
            throw new IndexOutOfBoundsException("boneIndex=" + boneIndex + " size=" + names.length);
        }
        return names[boneIndex];
    }

    @Override
    public int findBoneIndex(String boneName) {
        if (boneName == null) return -1;
        Integer idx = indexByName.get(boneName);
        if (idx != null) return idx;
        String t = boneName.trim();
        if (t.isEmpty()) return -1;
        idx = indexByName.get(t);
        return (idx != null) ? idx : -1;
    }

    @Override
    public void getModelTransform(int boneIndex, Transform store) {
        Objects.requireNonNull(store, "store");
        if (boneIndex < 0 || boneIndex >= joints.length) {
            throw new IndexOutOfBoundsException("boneIndex=" + boneIndex + " size=" + joints.length);
        }
        store.set(joints[boneIndex].getModelTransform());
    }
}