package org.foxesworld.kalitech.engine.modules.chromium;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Receives OSR BGRA pixels from JCEF onPaint and publishes them into {@link ChromiumPixelBuffer}.
 */
public final class ChromiumOsrPaintSink {

    private final ChromiumPixelBuffer pixelBuffer;
    private final ChromiumPixelBuffer.ByteBufferAllocator allocator;

    private long lastPrintMs;
    private int frames;

    public ChromiumOsrPaintSink(ChromiumPixelBuffer pixelBuffer, ChromiumPixelBuffer.ByteBufferAllocator allocator) {
        this.pixelBuffer = Objects.requireNonNull(pixelBuffer, "pixelBuffer");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    public void onPaint(ByteBuffer bgraPixels, int width, int height) {
        if (bgraPixels == null) {
            return;
        }

        pixelBuffer.writeFrame(bgraPixels, width, height, allocator);

        frames++;
        long now = System.currentTimeMillis();
        if (now - lastPrintMs >= 1000L) {
            System.out.println("[chromium][osr] fps=" + frames + " size=" + width + "x" + height);
            frames = 0;
            lastPrintMs = now;
        }
    }
}
