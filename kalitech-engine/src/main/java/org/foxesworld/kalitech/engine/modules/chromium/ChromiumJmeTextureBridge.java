package org.foxesworld.kalitech.engine.modules.chromium;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Updates a jME {@link Texture2D} from OSR BGRA frames stored in {@link ChromiumPixelBuffer}.
 *
 * Call {@link #updateIfDirty()} from jME render thread.
 */
public final class ChromiumJmeTextureBridge {

    private final ChromiumPixelBuffer pixelBuffer;

    private Texture2D texture;
    private Image image;

    private int lastAppliedSeq = 0;

    public ChromiumJmeTextureBridge(ChromiumPixelBuffer pixelBuffer) {
        this.pixelBuffer = Objects.requireNonNull(pixelBuffer, "pixelBuffer");
    }

    public Texture2D initOrRecreate(TextureFactory factory) {
        Objects.requireNonNull(factory, "factory");

        int w = pixelBuffer.width();
        int h = pixelBuffer.height();

        this.image = factory.createImageBGRA8(w, h, pixelBuffer.capacityBytes());
        this.texture = factory.createTexture2D(image);

        this.lastAppliedSeq = 0;
        return texture;
    }

    public Texture2D texture() {
        return texture;
    }

    public Image image() {
        return image;
    }

    public boolean updateIfDirty() {
        if (texture == null || image == null) {
            return false;
        }

        ChromiumPixelBuffer.Readable r = pixelBuffer.tryAcquireReadable();
        if (r == null) {
            return false;
        }

        int seq = r.sequence();
        if (seq == lastAppliedSeq) {
            return false;
        }

        int w = r.width();
        int h = r.height();

        if (w != image.getWidth() || h != image.getHeight()) {
            return false;
        }

        ByteBuffer src = r.pixels();
        ByteBuffer dst = image.getData(0);

        dst.clear();
        src.clear();

        int n = Math.min(dst.remaining(), src.remaining());
        if (n > 0) {
            int oldLimit = src.limit();
            src.limit(src.position() + n);
            dst.put(src);
            src.limit(oldLimit);
        }

        dst.flip();
        image.setUpdateNeeded();

        lastAppliedSeq = seq;
        return true;
    }

    public interface TextureFactory {
        Image createImageBGRA8(int width, int height, int bytes);
        Texture2D createTexture2D(Image image);
    }
}