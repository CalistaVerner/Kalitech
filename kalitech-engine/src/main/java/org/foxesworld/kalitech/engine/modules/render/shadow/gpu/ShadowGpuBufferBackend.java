// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/gpu/ShadowGpuBufferBackend.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.gpu;

import java.nio.ByteBuffer;

/**
 * Backend abstraction for GPU buffer upload/bind operations.
 * <p>
 * Implementations must run on the render thread.
 */
public interface ShadowGpuBufferBackend {

    /**
     * Ensures the GPU buffer exists and is at least {@code sizeBytes}.
     */
    void ensureCapacity(int sizeBytes);

    /**
     * Uploads buffer content to GPU (UBO/SSBO).
     * <p>
     * {@code data.remaining()} must equal the intended upload size.
     */
    void upload(ByteBuffer data);

    /**
     * Binds the buffer to a binding point (UBO binding or SSBO binding).
     */
    void bind(int bindingIndex);
}