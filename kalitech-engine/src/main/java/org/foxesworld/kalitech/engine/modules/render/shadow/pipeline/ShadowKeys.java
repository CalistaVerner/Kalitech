// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowKeys.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.math.Vector3f;

/**
 * Canonical keys for shadow pipeline communication via {@link ShadowWorkspace}.
 */
public final class ShadowKeys {

    /**
     * Latest camera movement in world units (frame-scope).
     */
    public static final ShadowKey<Float> VIEW_CAM_MOVE_WORLD =
            ShadowKey.frame("shadow.viewCamMoveWorld", Float.class);

    // ---------------- FRAME ----------------
    /**
     * Latest camera rotation delta in degrees (frame-scope).
     */
    public static final ShadowKey<Float> VIEW_CAM_ROTATE_DEG =
            ShadowKey.frame("shadow.viewCamRotateDeg", Float.class);
    /**
     * Deterministic light direction (frame-scope).
     */
    public static final ShadowKey<Vector3f> LIGHT_DIR =
            ShadowKey.frame("shadow.lightDir", Vector3f.class);
    /**
     * Deterministic light left axis (frame-scope).
     */
    public static final ShadowKey<Vector3f> LIGHT_LEFT =
            ShadowKey.frame("shadow.lightLeft", Vector3f.class);
    /**
     * Deterministic light up axis (frame-scope).
     */
    public static final ShadowKey<Vector3f> LIGHT_UP =
            ShadowKey.frame("shadow.lightUp", Vector3f.class);
    /**
     * Texel world size for this split (split-scope).
     */
    public static final ShadowKey<Float> TEXEL_WORLD =
            ShadowKey.split("shadow.texelWorld", Float.class);

    // ---------------- SPLIT ----------------
    /**
     * If false, texel snapping must be skipped for this split (split-scope).
     */
    public static final ShadowKey<Boolean> ALLOW_TEXEL_SNAP =
            ShadowKey.split("shadow.allowTexelSnap", Boolean.class);
    /**
     * True if snap logic executed for this split (split-scope).
     */
    public static final ShadowKey<Boolean> SNAP_APPLIED =
            ShadowKey.split("shadow.snapApplied", Boolean.class);
    /**
     * True if snapping actually changed the shadow camera (split-scope).
     */
    public static final ShadowKey<Boolean> TEXEL_SNAPPED =
            ShadowKey.split("shadow.texelSnapped", Boolean.class);

    private ShadowKeys() {
    }
}