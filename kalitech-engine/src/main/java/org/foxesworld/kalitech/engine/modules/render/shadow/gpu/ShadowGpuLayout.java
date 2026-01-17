// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/gpu/ShadowGpuLayout.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.gpu;

/**
 * Binary layout for KT_ShadowUBO (std140).
 * <p>
 * Layout is designed to be identical in std140 and std430 for the chosen types:
 * uvec4 header, mat4[], vec4[], uvec4[].
 */
public final class ShadowGpuLayout {

    public static final int MAX_SPLITS = 8;

    public static final int HEADER_BYTES = 16;           // uvec4
    public static final int MAT4_BYTES = 64;             // 16 floats
    public static final int VEC4_BYTES = 16;             // 4 floats or 4 uints

    public static final int BYTES_PER_SPLIT =
            MAT4_BYTES + VEC4_BYTES + VEC4_BYTES + VEC4_BYTES; // viewProj + nearFar + texelWorldInv + flags

    public static final int TOTAL_BYTES =
            HEADER_BYTES + (MAX_SPLITS * BYTES_PER_SPLIT);

    // Split block offsets (relative to the start of split i block).
    public static final int OFF_VIEWPROJ = 0;
    public static final int OFF_NEARFAR = OFF_VIEWPROJ + MAT4_BYTES;
    public static final int OFF_TEXEL = OFF_NEARFAR + VEC4_BYTES;
    public static final int OFF_FLAGS = OFF_TEXEL + VEC4_BYTES;

    private ShadowGpuLayout() {
    }
}