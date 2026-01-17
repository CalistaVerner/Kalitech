// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/gpu/ShadowGpuParams.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.gpu;

import com.jme3.math.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Per-frame GPU parameter packet for cascaded shadows.
 * <p>
 * Designed for zero allocations during runtime:
 * <ul>
 *   <li>One object stored in workspace</li>
 *   <li>Mutable arrays reused each frame</li>
 *   <li>Packer writes into a persistent direct ByteBuffer</li>
 * </ul>
 */
public final class ShadowGpuParams {

    public static final int FLAG_ALLOW_REFIT = 1 << 0;
    public static final int FLAG_ALLOW_SNAP = 1 << 1;
    public static final int FLAG_TELEPORT = 1 << 2;
    public static final int FLAG_TEXEL_SNAPPED = 1 << 3;

    private long frameId;
    private int numSplits;
    private int shadowMapSize;

    // Column-major 4x4 matrices (16 floats per split), contiguous.
    private final float[] viewProj = new float[ShadowGpuLayout.MAX_SPLITS * 16];

    // vec4 per split
    private final float[] nearFar = new float[ShadowGpuLayout.MAX_SPLITS * 4];
    private final float[] texel = new float[ShadowGpuLayout.MAX_SPLITS * 4];

    // uvec4 per split (we use only .x)
    private final int[] flags = new int[ShadowGpuLayout.MAX_SPLITS * 4];

    // Scratch array reused for Matrix4f extraction
    private final float[] m16 = new float[16];

    public void beginFrame(long frameId, int numSplits, int shadowMapSize) {
        this.frameId = frameId;
        this.numSplits = clamp(numSplits, 1, ShadowGpuLayout.MAX_SPLITS);
        this.shadowMapSize = Math.max(1, shadowMapSize);
    }

    public long frameId() {
        return frameId;
    }

    public int numSplits() {
        return numSplits;
    }

    public int shadowMapSize() {
        return shadowMapSize;
    }

    /**
     * Sets split data. Matrix is stored column-major to match GLSL mat4 memory.
     * <p>
     * Note: jME Matrix4f fields are row/col named, but OpenGL expects column-major packing.
     * We explicitly pack into column-major order.
     */
    public void setSplit(int splitIndex,
                         Matrix4f viewProjection,
                         float splitNear,
                         float splitFar,
                         float texelWorld,
                         int flagsMask) {
        if (splitIndex < 0 || splitIndex >= ShadowGpuLayout.MAX_SPLITS) return;

        writeMat4ColumnMajor(viewProjection, splitIndex);

        int v4 = splitIndex * 4;
        nearFar[v4] = splitNear;
        nearFar[v4 + 1] = splitFar;
        nearFar[v4 + 2] = splitFar - splitNear;
        nearFar[v4 + 3] = 0f;

        float invTexel = texelWorld > 0f ? (1f / texelWorld) : 0f;
        float invMap = shadowMapSize > 0 ? (1f / (float) shadowMapSize) : 0f;

        texel[v4] = texelWorld;
        texel[v4 + 1] = invTexel;
        texel[v4 + 2] = (float) shadowMapSize;
        texel[v4 + 3] = invMap;

        int u4 = splitIndex * 4;
        flags[u4] = flagsMask;
        flags[u4 + 1] = 0;
        flags[u4 + 2] = 0;
        flags[u4 + 3] = 0;
    }

    /**
     * Packs this packet into a ByteBuffer matching KT_ShadowUBO std140 layout.
     * <p>
     * Requirements:
     * <ul>
     *   <li>dst must be a direct buffer</li>
     *   <li>dst.capacity() >= ShadowGpuLayout.TOTAL_BYTES</li>
     *   <li>dst.order(ByteOrder.nativeOrder()) recommended</li>
     * </ul>
     */
    public void packStd140(ByteBuffer dst) {
        if (dst == null) throw new NullPointerException("dst");
        if (dst.capacity() < ShadowGpuLayout.TOTAL_BYTES) {
            throw new IllegalArgumentException("dst.capacity < TOTAL_BYTES: " + dst.capacity());
        }

        dst.order(ByteOrder.nativeOrder());
        dst.clear();

        // Header: uvec4 { numSplits, shadowMapSize, frameIdLo, frameIdHi }
        dst.putInt(numSplits);
        dst.putInt(shadowMapSize);
        dst.putInt((int) (frameId & 0xFFFFFFFFL));
        dst.putInt((int) ((frameId >>> 32) & 0xFFFFFFFFL));

        // Split blocks
        for (int i = 0; i < ShadowGpuLayout.MAX_SPLITS; i++) {
            // mat4 viewProj (16 floats), column-major
            int mOff = i * 16;
            for (int k = 0; k < 16; k++) {
                dst.putFloat(viewProj[mOff + k]);
            }

            // vec4 nearFar
            int vOff = i * 4;
            dst.putFloat(nearFar[vOff]);
            dst.putFloat(nearFar[vOff + 1]);
            dst.putFloat(nearFar[vOff + 2]);
            dst.putFloat(nearFar[vOff + 3]);

            // vec4 texelWorldInv
            dst.putFloat(texel[vOff]);
            dst.putFloat(texel[vOff + 1]);
            dst.putFloat(texel[vOff + 2]);
            dst.putFloat(texel[vOff + 3]);

            // uvec4 flags
            int uOff = i * 4;
            dst.putInt(flags[uOff]);
            dst.putInt(flags[uOff + 1]);
            dst.putInt(flags[uOff + 2]);
            dst.putInt(flags[uOff + 3]);
        }

        dst.flip();
    }

    private void writeMat4ColumnMajor(Matrix4f m, int splitIndex) {
        if (m == null) return;

        // Explicit column-major packing:
        // [ m00 m10 m20 m30 | m01 m11 m21 m31 | m02 m12 m22 m32 | m03 m13 m23 m33 ]
        int base = splitIndex * 16;

        viewProj[base] = m.m00;
        viewProj[base + 1] = m.m10;
        viewProj[base + 2] = m.m20;
        viewProj[base + 3] = m.m30;

        viewProj[base + 4] = m.m01;
        viewProj[base + 5] = m.m11;
        viewProj[base + 6] = m.m21;
        viewProj[base + 7] = m.m31;

        viewProj[base + 8] = m.m02;
        viewProj[base + 9] = m.m12;
        viewProj[base + 10] = m.m22;
        viewProj[base + 11] = m.m32;

        viewProj[base + 12] = m.m03;
        viewProj[base + 13] = m.m13;
        viewProj[base + 14] = m.m23;
        viewProj[base + 15] = m.m33;
    }

    private static int clamp(int v, int a, int b) {
        return v < a ? a : (Math.min(v, b));
    }
}