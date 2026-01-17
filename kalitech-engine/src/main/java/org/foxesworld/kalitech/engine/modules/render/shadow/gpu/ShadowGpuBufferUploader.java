// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/gpu/ShadowGpuBufferUploader.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Render-thread safe uploader for shadow GPU params.
 * <p>
 * Keeps a persistent direct ByteBuffer and updates GPU buffer once per frame.
 */
public final class ShadowGpuBufferUploader {

    private final ShadowGpuBufferBackend backend;
    private final int bindingIndex;

    private final ByteBuffer staging;
    private long lastUploadedFrameId = Long.MIN_VALUE;

    public ShadowGpuBufferUploader(ShadowGpuBufferBackend backend, int bindingIndex) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.bindingIndex = bindingIndex;

        // Persistent direct staging buffer, reused every frame (no allocations).
        this.staging = ByteBuffer.allocateDirect(ShadowGpuLayout.TOTAL_BYTES)
                .order(ByteOrder.nativeOrder());

        backend.ensureCapacity(ShadowGpuLayout.TOTAL_BYTES);
        backend.bind(bindingIndex);
    }

    /**
     * Uploads the packet if frameId changed. Must be called from render thread.
     */
    public void uploadIfDirty(ShadowGpuParams params) {
        if (params == null) return;
        long fid = params.frameId();
        if (fid == lastUploadedFrameId) return;

        params.packStd140(staging);
        backend.upload(staging);

        lastUploadedFrameId = fid;
    }

    public int bindingIndex() {
        return bindingIndex;
    }
}