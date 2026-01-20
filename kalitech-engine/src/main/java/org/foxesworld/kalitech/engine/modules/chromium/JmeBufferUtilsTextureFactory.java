package org.foxesworld.kalitech.engine.modules.chromium;

import com.jme3.util.BufferUtils;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;

import java.nio.ByteBuffer;

/**
 * Default jME texture factory based on {@link BufferUtils}.
 */
public final class JmeBufferUtilsTextureFactory implements ChromiumJmeTextureBridge.TextureFactory,
        ChromiumPixelBuffer.ByteBufferAllocator {

    @Override
    public Image createImageBGRA8(int width, int height, int bytes) {
        ByteBuffer buf = BufferUtils.createByteBuffer(bytes);
        buf.clear();
        return new Image(Image.Format.BGRA8, width, height, buf);
    }

    @Override
    public Texture2D createTexture2D(Image image) {
        return new Texture2D(image);
    }

    @Override
    public ByteBuffer allocateDirect(int bytes) {
        return BufferUtils.createByteBuffer(bytes);
    }
}