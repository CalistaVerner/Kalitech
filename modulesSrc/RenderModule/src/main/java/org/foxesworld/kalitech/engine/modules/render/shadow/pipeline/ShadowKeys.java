/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;

public final class ShadowKeys {
    public static final ShadowKey<Float> VIEW_CAM_MOVE_WORLD = ShadowKey.frame("shadow.viewCamMoveWorld", Float.class);
    public static final ShadowKey<Float> VIEW_CAM_ROTATE_DEG = ShadowKey.frame("shadow.viewCamRotateDeg", Float.class);
    public static final ShadowKey<Vector3f> LIGHT_DIR = ShadowKey.frame("shadow.lightDir", Vector3f.class);
    public static final ShadowKey<Vector3f> LIGHT_LEFT = ShadowKey.frame("shadow.lightLeft", Vector3f.class);
    public static final ShadowKey<Vector3f> LIGHT_UP = ShadowKey.frame("shadow.lightUp", Vector3f.class);
    public static final ShadowKey<Float> TEXEL_WORLD = ShadowKey.split("shadow.texelWorld", Float.class);
    public static final ShadowKey<Boolean> ALLOW_TEXEL_SNAP = ShadowKey.split("shadow.allowTexelSnap", Boolean.class);
    public static final ShadowKey<Boolean> SNAP_APPLIED = ShadowKey.split("shadow.snapApplied", Boolean.class);
    public static final ShadowKey<Boolean> TEXEL_SNAPPED = ShadowKey.split("shadow.texelSnapped", Boolean.class);

    private ShadowKeys() {
    }
}

