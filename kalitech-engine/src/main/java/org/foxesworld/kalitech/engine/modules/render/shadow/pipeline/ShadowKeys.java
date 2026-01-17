// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowKeys.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.gpu.ShadowGpuParams;

/**
 * Canonical keys for shadow pipeline communication via {@link ShadowWorkspace}.
 */
public final class ShadowKeys {

    // ---------------- FRAME ----------------

    public static final ShadowKey<Float> VIEW_CAM_MOVE_WORLD =
            ShadowKey.frame("shadow.viewCamMoveWorld", Float.class);

    public static final ShadowKey<Float> VIEW_CAM_ROTATE_DEG =
            ShadowKey.frame("shadow.viewCamRotateDeg", Float.class);

    public static final ShadowKey<Boolean> VIEW_CAM_TELEPORT =
            ShadowKey.frame("shadow.viewCamTeleport", Boolean.class);

    public static final ShadowKey<Float> STABILITY_SCORE =
            ShadowKey.frame("shadow.stabilityScore", Float.class);

    public static final ShadowKey<Vector3f> LIGHT_DIR =
            ShadowKey.frame("shadow.lightDir", Vector3f.class);

    public static final ShadowKey<Vector3f> LIGHT_LEFT =
            ShadowKey.frame("shadow.lightLeft", Vector3f.class);

    public static final ShadowKey<Vector3f> LIGHT_UP =
            ShadowKey.frame("shadow.lightUp", Vector3f.class);

    /**
     * Mandatory GPU parameter packet for the current frame.
     * Written once per frame and then mutated in-place.
     */
    public static final ShadowKey<ShadowGpuParams> GPU_PARAMS =
            ShadowKey.frame("shadow.gpuParams", ShadowGpuParams.class);

    // ---------------- SPLIT ----------------

    public static final ShadowKey<Float> TEXEL_WORLD =
            ShadowKey.split("shadow.texelWorld", Float.class);

    public static final ShadowKey<Boolean> ALLOW_TEXEL_SNAP =
            ShadowKey.split("shadow.allowTexelSnap", Boolean.class);

    public static final ShadowKey<Boolean> ALLOW_SHADOW_CAM_REFIT =
            ShadowKey.split("shadow.allowShadowCamRefit", Boolean.class);

    public static final ShadowKey<Boolean> SPLIT_TELEPORT =
            ShadowKey.split("shadow.splitTeleport", Boolean.class);

    public static final ShadowKey<Boolean> SNAP_APPLIED =
            ShadowKey.split("shadow.snapApplied", Boolean.class);

    public static final ShadowKey<Boolean> TEXEL_SNAPPED =
            ShadowKey.split("shadow.texelSnapped", Boolean.class);

    private ShadowKeys() {
    }
}