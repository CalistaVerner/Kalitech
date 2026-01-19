package org.foxesworld.kalitech.engine.modules.rig.jme;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.rig.RigBinding;
import org.foxesworld.kalitech.engine.modules.rig.RigProfile;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonPoseView;
import org.foxesworld.kalitech.engine.modules.rig.SkeletonView;

import java.util.Objects;

public final class SocketTransformReader {

    private final Transform tmpBoneModel = new Transform();
    private final Transform tmpBoneWorld = new Transform();
    private final Transform tmpSocketLocal = new Transform();

    private final Vector3f tmpPos = new Vector3f();
    private final Quaternion tmpRot = new Quaternion();

    public boolean readSocketWorld(RigProfile profile,
                                   RigBinding binding,
                                   Spatial parentSpatial,
                                   String socketId,
                                   Transform store) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(parentSpatial, "parentSpatial");
        Objects.requireNonNull(store, "store");

        RigProfile.SocketSpec socket = profile.socket(socketId);
        if (socket == null) return false;

        Integer boneIndex = binding.boneIndexForSocket(socketId);
        if (boneIndex == null) return false;

        SkeletonView view = JmeSkeletonResolver.resolve(parentSpatial);
        if (!(view instanceof SkeletonPoseView pose)) return false;

        pose.getModelTransform(boneIndex, tmpBoneModel);

        tmpBoneWorld.set(tmpBoneModel);
        tmpBoneWorld.combineWithParent(parentSpatial.getWorldTransform());

        tmpPos.set(socket.ox, socket.oy, socket.oz);
        tmpRot.fromAngles(
                (float) Math.toRadians(socket.rxDeg),
                (float) Math.toRadians(socket.ryDeg),
                (float) Math.toRadians(socket.rzDeg)
        );
        tmpSocketLocal.setTranslation(tmpPos);
        tmpSocketLocal.setRotation(tmpRot);
        tmpSocketLocal.setScale(Vector3f.UNIT_XYZ);

        store.set(tmpSocketLocal);
        store.combineWithParent(tmpBoneWorld);
        return true;
    }
}