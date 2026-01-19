package org.foxesworld.kalitech.engine.modules.rig.jme;

import com.jme3.animation.Bone;
import com.jme3.animation.Skeleton;
import com.jme3.math.Transform;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonPoseView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Deprecated
public final class JmeLegacySkeletonView implements JmeSkeletonView, SkeletonPoseView {

    private final Skeleton skeleton;
    private final Bone[] bones;
    private final String[] names;
    private final Map<String, Integer> indexByName;

    public JmeLegacySkeletonView(Skeleton skeleton) {
        this.skeleton = Objects.requireNonNull(skeleton, "skeleton");
        this.bones = skeleton.getRoots();
        this.names = new String[bones.length];
        this.indexByName = new HashMap<>(Math.max(16, bones.length * 2));
        for (int i = 0; i < bones.length; i++) {
            String n = bones[i].getName();
            if (n == null) n = "";
            names[i] = n;
            indexByName.put(n, i);
        }
    }

    @Override
    public int boneCount() {
        return bones.length;
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
        if (boneIndex < 0 || boneIndex >= bones.length) {
            throw new IndexOutOfBoundsException("boneIndex=" + boneIndex + " size=" + bones.length);
        }
        Bone b = bones[boneIndex];
        store.setTranslation(b.getModelSpacePosition());
        store.setRotation(b.getModelSpaceRotation());
        store.setScale(b.getModelSpaceScale());
    }
}